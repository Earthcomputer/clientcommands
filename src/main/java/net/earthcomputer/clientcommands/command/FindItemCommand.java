package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.earthcomputer.clientcommands.command.arguments.WithStringArgument;
import net.earthcomputer.clientcommands.features.ClientcommandsDataQueryHandler;
import net.earthcomputer.clientcommands.features.ServerPermissions;
import net.earthcomputer.clientcommands.server.FindItemUtil;
import net.earthcomputer.clientcommands.server.FindItemPayloads;
import net.earthcomputer.clientcommands.server.ServerPermission;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.earthcomputer.clientcommands.util.CUtil;
import net.earthcomputer.clientcommands.util.GuiBlocker;
import net.earthcomputer.clientcommands.util.MathUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static dev.xpple.clientarguments.arguments.CItemPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class FindItemCommand {
    private static final Flag<Boolean> FLAG_CLICK_INVENTORIES = Flag.ofFlag("click-inventories").build();
    private static final Flag<Boolean> FLAG_NO_SEARCH_NESTED = Flag.ofFlag("no-search-nested").withShortName('s').build();
    private static final Flag<Boolean> FLAG_KEEP_SEARCHING = Flag.ofFlag("keep-searching").build();
    private static final Flag<Boolean> FLAG_ALL_LOADED_CHUNKS = Flag.ofFlag("all-loaded-chunks").build();

    private static final SimpleCommandExceptionType ALL_LOADED_CHUNKS_REQUIRES_SERVER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cfinditem.allLoadedChunks.requiresServer"));

    public static void registerEvents() {
        ClientPlayNetworking.registerGlobalReceiver(FindItemPayloads.Match.TYPE, (payload, _) -> {
            ServerFindItemsTask task = ServerFindItemsTask.activeTasks.get(payload.requestId());
            if (task != null) {
                task.onMatch(payload);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FindItemPayloads.Finished.TYPE, (payload, _) -> {
            ServerFindItemsTask task = ServerFindItemsTask.activeTasks.get(payload.requestId());
            if (task != null) {
                task.onFinished(payload.reason());
            }
        });
    }

    private static boolean canUseServerSearch() {
        return ServerPermissions.has(ServerPermission.FIND_ITEM) && ClientPlayNetworking.canSend(FindItemPayloads.Request.TYPE);
    }

    @SuppressWarnings("unchecked")
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        var cfinditem = dispatcher.register(literal("cfinditem")
            .then(argument("item", withString(itemPredicate(context)))
                .executes(ctx ->
                    findItem(ctx,
                        getFlag(ctx, FLAG_CLICK_INVENTORIES),
                        getFlag(ctx, FLAG_NO_SEARCH_NESTED),
                        getFlag(ctx, FLAG_KEEP_SEARCHING),
                        getFlag(ctx, FLAG_ALL_LOADED_CHUNKS),
                        getWithString(ctx, "item", (Class<Predicate<ItemStack>>) (Class<?>) Predicate.class)))));
        FLAG_CLICK_INVENTORIES.addToCommand(dispatcher, cfinditem, ctx -> true);
        FLAG_NO_SEARCH_NESTED.addToCommand(dispatcher, cfinditem, ctx -> true);
        FLAG_KEEP_SEARCHING.addToCommand(dispatcher, cfinditem, ctx -> true);
        FLAG_ALL_LOADED_CHUNKS.addToCommand(dispatcher, cfinditem, ctx -> true);
    }

    private static int findItem(CommandContext<FabricClientCommandSource> ctx, boolean clickInventories, boolean noSearchNested, boolean keepSearching, boolean allLoadedChunks, WithStringArgument.Result<Predicate<ItemStack>> item) throws CommandSyntaxException {
        String taskName = TaskManager.addTask("cfinditem", makeFindItemsTask(item.string(), item.value(), clickInventories, !noSearchNested, keepSearching, allLoadedChunks));
        if (keepSearching) {
            ctx.getSource().sendFeedback(Component.translatable("commands.cfinditem.starting.keepSearching", item.string())
                    .append(" ")
                    .append(CComponentUtil.getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));
        } else {
            ctx.getSource().sendFeedback(Component.translatable("commands.cfinditem.starting", item.string()));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static SimpleTask makeFindItemsTask(String searchingForName, Predicate<ItemStack> searchingFor, boolean clickInventories, boolean searchNested, boolean keepSearching, boolean allLoadedChunks) throws CommandSyntaxException {
        Predicate<ItemStackTemplate> templatePredicate = template -> searchingFor.test(new ItemStack(template.item(), template.count(), template.components()));
        boolean useServerSearch = !clickInventories && canUseServerSearch();
        if (allLoadedChunks && !useServerSearch) {
            throw ALL_LOADED_CHUNKS_REQUIRES_SERVER_EXCEPTION.create();
        }
        if (useServerSearch) {
            return new ServerFindItemsTask(searchingForName, templatePredicate, searchNested, keepSearching, allLoadedChunks);
        }
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) && !clickInventories) {
            return new NbtQueryFindItemsTask(searchingForName, templatePredicate, searchNested, keepSearching);
        } else {
            return new ClickInventoriesFindItemsTask(searchingForName, templatePredicate, searchNested, keepSearching);
        }
    }

    private static abstract class AbstractFindItemsTask extends SimpleTask {
        private static final Set<Object> MUTEX_KEYS = Set.of(TaskManager.INTENSIVE_TASK_MUTEX);

        protected final String searchingForName;
        protected final Predicate<ItemStackTemplate> searchingFor;
        protected final boolean searchNested;
        protected final boolean keepSearching;

        protected int totalFound = 0;

        private AbstractFindItemsTask(String searchingForName, Predicate<ItemStackTemplate> searchingFor, boolean searchNested, boolean keepSearching) {
            this.searchingForName = searchingForName;
            this.searchingFor = searchingFor;
            this.searchNested = searchNested;
            this.keepSearching = keepSearching;
        }

        protected int countItems(ItemStackTemplate stack) {
            return FindItemUtil.countItems(stack, searchingFor, searchNested);
        }

        protected int countItems(CompoundTag stack) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return 0;
            }

            DataResult<ItemStackTemplate> itemResult = ItemStackTemplate.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack);
            if (itemResult.isSuccess()) {
                return countItems(itemResult.getOrThrow());
            } else {
                return 0;
            }
        }

        protected int countItems(ListTag inventory) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return 0;
            }

            int total = 0;
            for (int i = 0; i < inventory.size(); i++) {
                CompoundTag compound = inventory.getCompoundOrEmpty(i);
                total += countItems(compound);
            }
            return total;
        }

        protected void printEntityLocation(Entity entity, int count) {
            printEntityLocation(entity.getUUID(), entity.getName(), BlockPos.containing(entity.position()), count);
        }

        protected void printEntityLocation(UUID uuid, Component name, BlockPos pos, int count) {
            sendFeedback(
                Component.translatable(
                    "commands.cfinditem.match.entity",
                    count,
                    searchingForName,
                    name,
                    CComponentUtil.getLookCoordsTextComponent(pos)
                )
                    .append(" ")
                    .append(CComponentUtil.getGlowButtonTextComponent(uuid))
            );
        }

        protected void printLocation(BlockPos pos, int count) {
            sendFeedback(Component.translatable("commands.cfinditem.match", count, searchingForName, CComponentUtil.getLookCoordsTextComponent(pos))
                .append(" ")
                .append(CComponentUtil.getGlowButtonTextComponent(pos)));
        }

        @Override
        public void onCompleted() {
            ClientCommandHelper.sendFeedback(Component.translatable("commands.cfinditem.total", totalFound, searchingForName).withStyle(ChatFormatting.BOLD));
        }

        @Override
        public Set<Object> getMutexKeys() {
            return MUTEX_KEYS;
        }
    }

    private static class ClickInventoriesFindItemsTask extends AbstractFindItemsTask {
        private final Set<BlockPos> searchedBlocks = new HashSet<>();
        private final Set<UUID> searchedEntities = new HashSet<>();
        @Nullable
        private Either<BlockPos, Entity> currentlySearching = null;
        private int currentlySearchingTimeout;
        private boolean hasSearchedEnderChest = false;

        public ClickInventoriesFindItemsTask(String searchingForName, Predicate<ItemStackTemplate> searchingFor, boolean searchNested, boolean keepSearching) {
            super(searchingForName, searchingFor, searchNested, keepSearching);
        }

        @Override
        public boolean condition() {
            return true;
        }

        @Override
        protected void onTick() {
            Entity entity = Minecraft.getInstance().getCameraEntity();
            if (entity == null) {
                _break();
                return;
            }
            Level level = Minecraft.getInstance().level;
            assert level != null;
            LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
            assert gameMode != null;
            if (currentlySearchingTimeout > 0) {
                currentlySearchingTimeout--;
                return;
            }
            if (player.isShiftKeyDown()) {
                return;
            }
            Vec3 origin = entity.getEyePosition(0);

            double entityReachDistance = player.entityInteractionRange();
            for (Entity entityToSearch : level.getEntities(player, AABB.ofSize(origin, entityReachDistance, entityReachDistance, entityReachDistance), entityToSearch -> FindItemUtil.getSearchableContainer(entityToSearch) != null)) {
                Vec3 closestPos = MathUtil.getClosestPoint(entityToSearch.getBoundingBox(), origin);
                if (closestPos.distanceToSqr(origin) < entityReachDistance * entityReachDistance && searchedEntities.add(entityToSearch.getUUID())) {
                    startSearch(Either.right(entityToSearch), origin, closestPos);
                    scheduleDelay();
                    return;
                }
            }

            double reachDistance = player.blockInteractionRange();
            int minX = Mth.floor(origin.x - reachDistance);
            int minY = Mth.floor(origin.y - reachDistance);
            int minZ = Mth.floor(origin.z - reachDistance);
            int maxX = Mth.floor(origin.x + reachDistance);
            int maxY = Mth.floor(origin.y + reachDistance);
            int maxZ = Mth.floor(origin.z + reachDistance);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!canSearch(level, pos)) {
                            continue;
                        }
                        if (searchedBlocks.contains(pos)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(pos);
                        Vec3 closestPos = MathUtil.getClosestPoint(pos, state.getShape(level, pos), origin);
                        if (closestPos.distanceToSqr(origin) > reachDistance * reachDistance) {
                            continue;
                        }
                        searchedBlocks.add(pos);
                        if (FindItemUtil.isEnderChest(level.getBlockEntity(pos))) {
                            if (hasSearchedEnderChest) {
                                continue;
                            }
                            hasSearchedEnderChest = true;
                        } else if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                            BlockPos offsetPos = pos.relative(ChestBlock.getConnectedDirection(state));
                            if (level.getBlockState(offsetPos).getBlock() == state.getBlock()) {
                                searchedBlocks.add(offsetPos);
                            }
                        }
                        startSearch(Either.left(pos), origin, closestPos);
                        scheduleDelay();
                        return;
                    }
                }
            }
            if (!keepSearching) {
                _break();
            }
        }

        private boolean canSearch(Level level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || (FindItemUtil.getSearchableContainer(blockEntity) == null && !FindItemUtil.isEnderChest(blockEntity))) {
                return false;
            } else if (state.getBlock() instanceof ChestBlock || FindItemUtil.isEnderChest(blockEntity)) {
                if (ChestBlock.isChestBlockedAt(level, pos)) {
                    return false;
                }
                if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos offsetPos = pos.relative(ChestBlock.getConnectedDirection(state));
                    return level.getBlockState(offsetPos).getBlock() != state.getBlock() || !ChestBlock.isChestBlockedAt(level, offsetPos);
                }
            } else if (state.getBlock() instanceof ShulkerBoxBlock && blockEntity instanceof ShulkerBoxBlockEntity shulkerBox) {
                return ShulkerBoxBlock.canOpen(state, level, pos, shulkerBox);
            }
            return true;
        }

        private void startSearch(Either<BlockPos, Entity> thing, Vec3 cameraPos, Vec3 clickPos) {
            Minecraft mc = Minecraft.getInstance();
            currentlySearching = thing;
            currentlySearchingTimeout = 100;
            GuiBlocker.addBlocker(new GuiBlocker() {
                @Override
                public boolean accept(@Nullable Screen screen) {
                    if (!(screen instanceof MenuAccess<?> menuAccess)) {
                        return true;
                    }
                    assert mc.player != null;
                    AbstractContainerMenu container = menuAccess.getMenu();
                    Set<Integer> playerInvSlots = new HashSet<>();
                    for (Slot slot : container.slots) {
                        if (slot.container instanceof Inventory) {
                            playerInvSlots.add(slot.index);
                        }
                    }
                    mc.player.containerMenu = new AbstractContainerMenu(container.menuType, container.containerId) {
                        @Override
                        public boolean stillValid(Player var1) {
                            return true;
                        }

                        @Override
                        public ItemStack quickMoveStack(Player player, int index) {
                            return ItemStack.EMPTY;
                        }

                        @Override
                        public void initializeContents(int revision, List<ItemStack> stacks, ItemStack cursorStack) {
                            List<ItemStack> containerStacks = new ArrayList<>();
                            for (int slot = 0; slot < stacks.size(); slot++) {
                                if (!playerInvSlots.contains(slot)) {
                                    containerStacks.add(stacks.get(slot));
                                }
                            }
                            int matchingItems = FindItemUtil.countItems(containerStacks, searchingFor, searchNested);
                            if (matchingItems > 0) {
                                int matchingItems_f = matchingItems;
                                CUtil.forEither(
                                    currentlySearching,
                                    pos -> printLocation(pos, matchingItems_f),
                                    entity -> printEntityLocation(entity, matchingItems_f)
                                );
                                totalFound += matchingItems;
                            }
                            currentlySearching = null;
                            currentlySearchingTimeout = 0;
                            mc.player.closeContainer();
                        }
                    };
                    return false;
                }
            });
            assert mc.gameMode != null;

            CUtil.forEither(thing,
                pos -> mc.gameMode.useItemOn(
                    mc.player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(clickPos,
                        Direction.getApproximateNearest((float) (clickPos.x - cameraPos.x), (float) (clickPos.y - cameraPos.y), (float) (clickPos.z - cameraPos.z)),
                        pos, false)),
                entity -> mc.gameMode.interact(
                    mc.player,
                    entity,
                    new EntityHitResult(entity),
                    InteractionHand.MAIN_HAND));
        }
    }

    private static class NbtQueryFindItemsTask extends AbstractFindItemsTask {
        private static final long MAX_SCAN_TIME = 30_000_000L; // 30ms
        private static final int NO_RESPONSE_TIMEOUT = 100; // ticks

        private final FindItemUtil.ChunkSearcher searcher = new FindItemUtil.ChunkSearcher() {
            @Override
            protected void searchBlock(BlockPos pos, Container container) {
                queryBlock(pos);
            }

            @Override
            protected void searchEntity(Entity entity, Container container) {
                queryEntity(entity);
            }
        };
        private boolean isScanning = true;
        @Nullable
        private LongIterator scanningIterator;
        private Vec3 origin = Vec3.ZERO;
        private final Set<BlockPos> waitingOnBlocks = new HashSet<>();
        private final Set<UUID> waitingOnEntities = new HashSet<>();
        private int currentlySearchingTimeout;
        private boolean hasQueriedEnderChest = false;
        @Nullable
        private Integer numItemsInEnderChest = null;
        private boolean hasPrintedEnderChest = false;

        public NbtQueryFindItemsTask(String searchingForName, Predicate<ItemStackTemplate> searchingFor, boolean searchNested, boolean keepSearching) {
            super(searchingForName, searchingFor, searchNested, keepSearching);
        }

        @Override
        public boolean condition() {
            return true;
        }

        @Override
        protected void onTick() {
            Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
            if (cameraEntity == null) {
                _break();
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;

            if (isScanning) {
                long startTime = System.nanoTime();

                if (scanningIterator == null) {
                    origin = cameraEntity.getEyePosition(0);
                    ChunkPos cameraChunk = new ChunkPos(Mth.floor(origin.x) >> 4, Mth.floor(origin.z) >> 4);
                    scanningIterator = FindItemUtil.getChunksAround(cameraChunk, Minecraft.getInstance().options.renderDistance().get()).iterator();
                }
                while (scanningIterator.hasNext()) {
                    long chunkPos = scanningIterator.nextLong();
                    int chunkX = ChunkPos.getX(chunkPos);
                    int chunkZ = ChunkPos.getZ(chunkPos);
                    if (level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null) {
                        printEnderChestIfClosest(new ChunkPos(chunkX, chunkZ));
                        searcher.searchChunk(level, level.getChunk(chunkX, chunkZ), origin);
                        if (searcher.getClosestEnderChest() != null && !hasQueriedEnderChest) {
                            hasQueriedEnderChest = true;
                            queryEnderChest();
                        }
                    }

                    if (System.nanoTime() - startTime > MAX_SCAN_TIME) {
                        // wait a tick
                        return;
                    }
                }
                isScanning = false;
                scanningIterator = null;
            }

            if (waitingOnBlocks.isEmpty() && waitingOnEntities.isEmpty() && (!hasQueriedEnderChest || numItemsInEnderChest != null)) {
                if (keepSearching) {
                    isScanning = true;
                } else {
                    _break();
                }
                return;
            }

            if (currentlySearchingTimeout > 0) {
                currentlySearchingTimeout--;
            } else {
                // timeout
                _break();
            }
        }

        // prints the ender chest early if we can't possibly find a closer one
        private void printEnderChestIfClosest(ChunkPos chunkToScan) {
            BlockPos enderChestPosition = searcher.getClosestEnderChest();
            if (enderChestPosition == null || numItemsInEnderChest == null || hasPrintedEnderChest) {
                return;
            }

            double enderChestDistanceSq = enderChestPosition.distToCenterSqr(origin);
            int originChunkX = Mth.floor(origin.x) >> 4;
            int originChunkZ = Mth.floor(origin.z) >> 4;
            int currentChunkRadius = Math.max(Math.abs(originChunkX - chunkToScan.x()), Math.abs(originChunkZ - chunkToScan.z()));
            double closestPossibleDistance = ((currentChunkRadius - 1) << 4) + Math.min(
                Math.min(origin.x - (originChunkX << 4), origin.z - (originChunkZ << 4)),
                Math.min(((originChunkX + 1) << 4) - origin.x, ((originChunkZ + 1) << 4) - origin.z));
            if (enderChestDistanceSq < closestPossibleDistance * closestPossibleDistance) {
                hasPrintedEnderChest = true;
                if (numItemsInEnderChest > 0) {
                    printLocation(enderChestPosition, numItemsInEnderChest);
                }
            }
        }

        private void queryEnderChest() {
            LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            ClientPacketListener packetListener = Minecraft.getInstance().getConnection();
            assert packetListener != null;

            currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
            ClientcommandsDataQueryHandler.get(packetListener).queryEntityNbt(player.getId(), playerNbt -> {
                int numItemsInEnderChest = 0;
                if (playerNbt != null && playerNbt.contains("EnderItems")) {
                    numItemsInEnderChest = countItems(playerNbt.getListOrEmpty("EnderItems"));
                }
                this.numItemsInEnderChest = numItemsInEnderChest;
                totalFound += numItemsInEnderChest;
                currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
            });
        }

        private void queryBlock(BlockPos pos) {
            ClientPacketListener packetListener = Minecraft.getInstance().getConnection();
            assert packetListener != null;

            waitingOnBlocks.add(pos);
            currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
            ClientcommandsDataQueryHandler.get(packetListener).queryBlockNbt(pos, blockNbt -> {
                waitingOnBlocks.remove(pos);
                if (blockNbt != null && blockNbt.contains(ContainerHelper.TAG_ITEMS)) {
                    int count = countItems(blockNbt.getListOrEmpty(ContainerHelper.TAG_ITEMS));
                    if (count == 0) {
                        count = countItems(blockNbt.getCompoundOrEmpty(DecoratedPotBlockEntity.TAG_ITEM));
                    }
                    if (count > 0) {
                        totalFound += count;
                        printLocation(pos, count);
                    }
                }
                currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
            });
        }

        private void queryEntity(Entity entity) {
            ClientPacketListener packetListener = Minecraft.getInstance().getConnection();
            assert packetListener != null;

            waitingOnEntities.add(entity.getUUID());
            currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
            ClientcommandsDataQueryHandler.get(packetListener).queryEntityNbt(entity.getId(), entityNbt -> {
                waitingOnEntities.remove(entity.getUUID());
                if (!entity.isRemoved() && entityNbt != null && entityNbt.contains(ContainerHelper.TAG_ITEMS)) {
                    int count = countItems(entityNbt.getListOrEmpty(ContainerHelper.TAG_ITEMS));
                    if (count > 0) {
                        totalFound += count;
                        printEntityLocation(entity, count);
                    }
                }
                currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
            });
        }

        @Override
        public void onCompleted() {
            BlockPos enderChestPosition = searcher.getClosestEnderChest();
            if (enderChestPosition != null && numItemsInEnderChest != null && numItemsInEnderChest > 0 && !hasPrintedEnderChest) {
                printLocation(enderChestPosition, numItemsInEnderChest);
            }
            super.onCompleted();
        }
    }

    /**
     * Searches using clientcommands installed on the server, see {@code ServerFindItem}.
     */
    private static class ServerFindItemsTask extends AbstractFindItemsTask {
        private static final Map<Integer, ServerFindItemsTask> activeTasks = new HashMap<>();
        private static int nextRequestId = 0;

        private final boolean allLoadedChunks;
        private final int requestId = nextRequestId++;
        private boolean finished = false;

        public ServerFindItemsTask(String searchingForName, Predicate<ItemStackTemplate> searchingFor, boolean searchNested, boolean keepSearching, boolean allLoadedChunks) {
            super(searchingForName, searchingFor, searchNested, keepSearching);
            this.allLoadedChunks = allLoadedChunks;
        }

        @Override
        public void initialize() {
            activeTasks.put(requestId, this);
            ClientPlayNetworking.send(new FindItemPayloads.Request(requestId, searchingForName, searchNested, keepSearching, allLoadedChunks));
        }

        @Override
        public boolean condition() {
            return true;
        }

        @Override
        protected void onTick() {
            if (finished) {
                _break();
            }
        }

        private void onMatch(FindItemPayloads.Match match) {
            totalFound += match.count();
            CUtil.forEither(
                match.location(),
                pos -> printLocation(pos, match.count()),
                entity -> printEntityLocation(entity.uuid(), entity.name(), entity.pos(), match.count())
            );
        }

        private void onFinished(FindItemPayloads.Finished.Reason reason) {
            finished = true;
            activeTasks.remove(requestId);
            switch (reason) {
                case COMPLETED -> {}
                case CANCELLED -> ClientCommandHelper.sendError(Component.translatable("commands.cfinditem.server.cancelled"));
                case NO_PERMISSION -> ClientCommandHelper.sendError(Component.translatable("commands.cfinditem.server.noPermission"));
                case INVALID_ITEM -> ClientCommandHelper.sendError(Component.translatable("commands.cfinditem.server.invalidItem"));
            }
            _break();
        }

        @Override
        public void onCompleted() {
            if (activeTasks.remove(requestId) != null && ClientPlayNetworking.canSend(FindItemPayloads.Cancel.TYPE)) {
                ClientPlayNetworking.send(new FindItemPayloads.Cancel(requestId));
            }
            super.onCompleted();
        }
    }
}
