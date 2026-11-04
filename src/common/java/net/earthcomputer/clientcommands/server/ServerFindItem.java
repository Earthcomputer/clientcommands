package net.earthcomputer.clientcommands.server;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public final class ServerFindItem {
    private static final int TICKS_BETWEEN_PASSES = 20;

    private ServerFindItem() {
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(FindItemPayloads.Request.TYPE, FindItemPayloads.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FindItemPayloads.Cancel.TYPE, FindItemPayloads.Cancel.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FindItemPayloads.Match.TYPE, FindItemPayloads.Match.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FindItemPayloads.Finished.TYPE, FindItemPayloads.Finished.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(FindItemPayloads.Request.TYPE, (payload, context) -> onRequest(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(FindItemPayloads.Cancel.TYPE, (payload, context) -> cancel(context.player(), payload.requestId()));

        ServerPlayConnectionEvents.DISCONNECT.register((listener, _) -> ServerTaskQueue.cancelIf(task -> task instanceof SearchTask search && search.player == listener.player));
    }

    private static void onRequest(ServerPlayer player, FindItemPayloads.Request request) {
        if (!ServerPermission.FIND_ITEM.isAllowed()) {
            sendFinished(player, request.requestId(), FindItemPayloads.Finished.Reason.NO_PERMISSION);
            return;
        }

        MinecraftServer server = player.level().getServer();
        CommandBuildContext buildContext = CommandBuildContext.simple(server.registryAccess(), server.getWorldData().enabledFeatures());
        Predicate<ItemStack> predicate;
        try {
            predicate = ItemPredicateArgument.itemPredicate(buildContext).parse(new StringReader(request.itemPredicate()));
        } catch (CommandSyntaxException e) {
            sendFinished(player, request.requestId(), FindItemPayloads.Finished.Reason.INVALID_ITEM);
            return;
        }

        // a client only has one search at a time, a new one replaces the old one
        ServerTaskQueue.cancelIf(task -> task instanceof SearchTask search && search.player == player);
        ServerTaskQueue.add(new SearchTask(player, request, predicate));
    }

    private static void cancel(ServerPlayer player, int requestId) {
        ServerTaskQueue.cancelIf(task -> task instanceof SearchTask search && search.player == player && search.requestId == requestId);
    }

    private static void sendFinished(ServerPlayer player, int requestId, FindItemPayloads.Finished.Reason reason) {
        if (!player.hasDisconnected()) {
            ServerPlayNetworking.send(player, new FindItemPayloads.Finished(requestId, reason));
        }
    }

    private static final class SearchTask implements ServerTask {
        private final ServerPlayer player;
        private final int requestId;
        private final Predicate<ItemStackTemplate> searchingFor;
        private final boolean searchNested;
        private final boolean keepSearching;
        private final boolean allLoadedChunks;
        private final ServerLevel level;

        private final FindItemUtil.ChunkSearcher searcher = new FindItemUtil.ChunkSearcher() {
            @Override
            protected void searchBlock(BlockPos pos, Container container) {
                sendMatchIfFound(Either.left(pos), container);
            }

            @Override
            protected void searchEntity(Entity entity, Container container) {
                sendMatchIfFound(Either.right(new FindItemPayloads.EntityLocation(entity.getUUID(), entity.getName(), entity.blockPosition())), container);
            }
        };
        @Nullable
        private LongIterator chunksToSearch;
        private Vec3 origin = Vec3.ZERO;
        private boolean hasSearchedEnderChest = false;
        private int nextPassTick = 0;

        SearchTask(ServerPlayer player, FindItemPayloads.Request request, Predicate<ItemStack> predicate) {
            this.player = player;
            this.requestId = request.requestId();
            this.searchingFor = template -> predicate.test(template.create());
            this.searchNested = request.searchNested();
            this.keepSearching = request.keepSearching();
            this.allLoadedChunks = request.allLoadedChunks();
            this.level = player.level();
        }

        @Override
        public Status tick(MinecraftServer server, long deadline) {
            if (player.hasDisconnected()) {
                return Status.FINISHED;
            }
            if (!ServerPermission.FIND_ITEM.isAllowed()) {
                sendFinished(player, requestId, FindItemPayloads.Finished.Reason.NO_PERMISSION);
                return Status.FINISHED;
            }
            if (player.level() != level) {
                // the client stops the search when it changes dimension anyway
                onCancelled();
                return Status.FINISHED;
            }

            if (chunksToSearch == null) {
                if (server.getTickCount() < nextPassTick) {
                    return Status.YIELD;
                }
                startPass();
            }

            while (chunksToSearch.hasNext()) {
                long chunkPos = chunksToSearch.nextLong();
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos));
                if (chunk != null) {
                    searcher.searchChunk(level, chunk, origin);
                }
                if (System.nanoTime() >= deadline) {
                    return Status.IN_PROGRESS;
                }
            }
            chunksToSearch = null;

            BlockPos enderChestPos = searcher.getClosestEnderChest();
            if (enderChestPos != null && !hasSearchedEnderChest) {
                hasSearchedEnderChest = true;
                sendMatchIfFound(Either.left(enderChestPos), player.getEnderChestInventory());
            }

            if (keepSearching) {
                nextPassTick = server.getTickCount() + TICKS_BETWEEN_PASSES;
                return Status.YIELD;
            }

            sendFinished(player, requestId, FindItemPayloads.Finished.Reason.COMPLETED);
            return Status.FINISHED;
        }

        private void startPass() {
            origin = player.getEyePosition();
            ChunkPos playerChunk = player.chunkPosition();
            LongList chunks;
            if (allLoadedChunks) {
                chunks = new LongArrayList(level.getChunkSource().chunkMap.visibleChunkMap.keySet());
                chunks.sort((a, b) -> Integer.compare(
                    playerChunk.getChessboardDistance(ChunkPos.getX(a), ChunkPos.getZ(a)),
                    playerChunk.getChessboardDistance(ChunkPos.getX(b), ChunkPos.getZ(b))
                ));
            } else {
                int viewDistance = Mth.clamp(player.requestedViewDistance(), 2, level.getServer().getPlayerList().getViewDistance());
                chunks = FindItemUtil.getChunksAround(playerChunk, viewDistance);
            }
            chunksToSearch = chunks.iterator();
        }

        private void sendMatchIfFound(Either<BlockPos, FindItemPayloads.EntityLocation> location, Container container) {
            int count = FindItemUtil.countItems(container, searchingFor, searchNested);
            if (count > 0) {
                ServerPlayNetworking.send(player, new FindItemPayloads.Match(requestId, location, count));
            }
        }

        @Override
        public void onCancelled() {
            sendFinished(player, requestId, FindItemPayloads.Finished.Reason.CANCELLED);
        }
    }
}
