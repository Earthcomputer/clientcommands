package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.earthcomputer.clientcommands.ClientcommandsDataQueryHandler;
import net.earthcomputer.clientcommands.GuiBlocker;
import net.earthcomputer.clientcommands.MathUtil;
import net.earthcomputer.clientcommands.mixin.ScreenHandlerAccessor;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static dev.xpple.clientarguments.arguments.CItemPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FindItemCommand {
    private static final int FLAG_NO_SEARCH_SHULKER_BOX = 1;
    private static final int FLAG_KEEP_SEARCHING = 2;

    @SuppressWarnings("unchecked")
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        var cfinditem = dispatcher.register(literal("cfinditem"));
        dispatcher.register(literal("cfinditem")
                .then(literal("--no-search-shulker-box")
                        .redirect(cfinditem, ctx -> withFlags(ctx.getSource(), FLAG_NO_SEARCH_SHULKER_BOX, true)))
                .then(literal("--keep-searching")
                        .redirect(cfinditem, ctx -> withFlags(ctx.getSource(), FLAG_KEEP_SEARCHING, true)))
                .then(argument("item", withString(itemPredicate(registryAccess)))
                        .executes(ctx ->
                                findItem(ctx,
                                        getFlag(ctx, FLAG_NO_SEARCH_SHULKER_BOX),
                                        getFlag(ctx, FLAG_KEEP_SEARCHING),
                                        getWithString(ctx, "item", (Class<Predicate<ItemStack>>) (Class<?>) Predicate.class)))));
    }

    private static int findItem(CommandContext<FabricClientCommandSource> ctx, boolean noSearchShulkerBox, boolean keepSearching, Pair<String, Predicate<ItemStack>> item) {
        String taskName = TaskManager.addTask("cfinditem", makeFindItemsTask(item.getLeft(), item.getRight(), !noSearchShulkerBox, keepSearching));
        if (keepSearching) {
            ctx.getSource().sendFeedback(Text.translatable("commands.cfinditem.starting.keepSearching", item.getLeft())
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));
        } else {
            ctx.getSource().sendFeedback(Text.translatable("commands.cfinditem.starting", item.getLeft()));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static SimpleTask makeFindItemsTask(String searchingForName, Predicate<ItemStack> searchingFor, boolean searchShulkerBoxes, boolean keepSearching) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;
        if (player.hasPermissionLevel(2)) {
            return new NbtQueryFindItemsTask(searchingForName, searchingFor, searchShulkerBoxes, keepSearching);
        } else {
            return new ClickInventoriesFindItemsTask(searchingForName, searchingFor, searchShulkerBoxes, keepSearching);
        }
    }

    private static abstract class AbstractFindItemsTask extends SimpleTask {
        protected final String searchingForName;
        protected final Predicate<ItemStack> searchingFor;
        protected final boolean searchShulkerBoxes;
        protected final boolean keepSearching;

        protected int totalFound = 0;

        private AbstractFindItemsTask(String searchingForName, Predicate<ItemStack> searchingFor, boolean searchShulkerBoxes, boolean keepSearching) {
            this.searchingForName = searchingForName;
            this.searchingFor = searchingFor;
            this.searchShulkerBoxes = searchShulkerBoxes;
            this.keepSearching = keepSearching;
        }

        protected int countItems(NbtList inventory) {
            int result = 0;
            for (int i = 0; i < inventory.size(); i++) {
                NbtCompound compound = inventory.getCompound(i);
                ItemStack stack = ItemStack.fromNbt(compound);
                if (searchingFor.test(stack)) {
                    result += stack.getCount();
                }
                if (searchShulkerBoxes && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) {
                    NbtCompound blockEntityNbt = BlockItem.getBlockEntityNbt(stack);
                    if (blockEntityNbt != null && blockEntityNbt.contains("Items", NbtElement.LIST_TYPE)) {
                        result += countItems(blockEntityNbt.getList("Items", NbtElement.COMPOUND_TYPE));
                    }
                }
            }
            return result;
        }

        protected void printLocation(BlockPos pos, int count) {
            sendFeedback(Text.translatable("commands.cfinditem.match.left", count, searchingForName)
                .append(getLookCoordsTextComponent(pos))
                .append(" ")
                .append(getGlowCoordsTextComponent(Text.translatable("commands.cfindblock.success.glow"), pos))
                .append(Text.translatable("commands.cfinditem.match.right", count, searchingForName)));
        }

        @Override
        public void onCompleted() {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable("commands.cfinditem.total", totalFound, searchingForName).formatted(Formatting.BOLD));
        }
    }

    private static class ClickInventoriesFindItemsTask extends AbstractFindItemsTask {
        private final Set<BlockPos> searchedBlocks = new HashSet<>();
        private BlockPos currentlySearching = null;
        private int currentlySearchingTimeout;
        private boolean hasSearchedEnderChest = false;

        public ClickInventoriesFindItemsTask(String searchingForName, Predicate<ItemStack> searchingFor, boolean searchShulkerBoxes, boolean keepSearching) {
            super(searchingForName, searchingFor, searchShulkerBoxes, keepSearching);
        }

        @Override
        public boolean condition() {
            return true;
        }

        @Override
        protected void onTick() {
            Entity entity = MinecraftClient.getInstance().cameraEntity;
            if (entity == null) {
                _break();
                return;
            }
            World world = MinecraftClient.getInstance().world;
            assert world != null;
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            assert player != null;
            ClientPlayerInteractionManager interactionManager = MinecraftClient.getInstance().interactionManager;
            assert interactionManager != null;
            if (currentlySearchingTimeout > 0) {
                currentlySearchingTimeout--;
                return;
            }
            if (player.isSneaking()) {
                return;
            }
            Vec3d origin = entity.getCameraPosVec(0);
            float reachDistance = interactionManager.getReachDistance();
            int minX = MathHelper.floor(origin.x - reachDistance);
            int minY = MathHelper.floor(origin.y - reachDistance);
            int minZ = MathHelper.floor(origin.z - reachDistance);
            int maxX = MathHelper.floor(origin.x + reachDistance);
            int maxY = MathHelper.floor(origin.y + reachDistance);
            int maxZ = MathHelper.floor(origin.z + reachDistance);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!canSearch(world, pos)) {
                            continue;
                        }
                        if (searchedBlocks.contains(pos)) {
                            continue;
                        }
                        BlockState state = world.getBlockState(pos);
                        Vec3d closestPos = MathUtil.getClosestPoint(pos, state.getOutlineShape(world, pos), origin);
                        if (closestPos.squaredDistanceTo(origin) > reachDistance * reachDistance) {
                            continue;
                        }
                        searchedBlocks.add(pos);
                        if (state.getBlock() == Blocks.ENDER_CHEST) {
                            if (hasSearchedEnderChest) {
                                continue;
                            }
                            hasSearchedEnderChest = true;
                        } else if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                            BlockPos offsetPos = pos.offset(ChestBlock.getFacing(state));
                            if (world.getBlockState(offsetPos).getBlock() == state.getBlock()) {
                                searchedBlocks.add(offsetPos);
                            }
                        }
                        startSearch(pos, origin, closestPos);
                        scheduleDelay();
                        return;
                    }
                }
            }
            if (!keepSearching) {
                _break();
            }
        }

        private boolean canSearch(World world, BlockPos pos) {
            BlockState state = world.getBlockState(pos);
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (!(blockEntity instanceof Inventory) && state.getBlock() != Blocks.ENDER_CHEST) {
                return false;
            }
            if (state.getBlock() instanceof ChestBlock || state.getBlock() == Blocks.ENDER_CHEST) {
                if (ChestBlock.isChestBlocked(world, pos)) {
                    return false;
                }
                if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                    BlockPos offsetPos = pos.offset(ChestBlock.getFacing(state));
                    return world.getBlockState(offsetPos).getBlock() != state.getBlock() || !ChestBlock.isChestBlocked(world, offsetPos);
                }
            }
            return true;
        }

        private void startSearch(BlockPos pos, Vec3d cameraPos, Vec3d clickPos) {
            MinecraftClient mc = MinecraftClient.getInstance();
            currentlySearching = pos;
            currentlySearchingTimeout = 100;
            GuiBlocker.addBlocker(new GuiBlocker() {
                @Override
                public boolean accept(Screen screen) {
                    if (!(screen instanceof ScreenHandlerProvider<?> handlerProvider)) {
                        return true;
                    }
                    assert mc.player != null;
                    ScreenHandler container = handlerProvider.getScreenHandler();
                    Set<Integer> playerInvSlots = new HashSet<>();
                    for (Slot slot : container.slots) {
                        if (slot.inventory instanceof PlayerInventory) {
                            playerInvSlots.add(slot.id);
                        }
                    }
                    mc.player.currentScreenHandler = new ScreenHandler(((ScreenHandlerAccessor) container).getNullableType(), container.syncId) {
                        @Override
                        public boolean canUse(PlayerEntity var1) {
                            return true;
                        }

                        @Override
                        public ItemStack quickMove(PlayerEntity player, int index) {
                            return ItemStack.EMPTY;
                        }

                        @Override
                        public void updateSlotStacks(int revision, List<ItemStack> stacks, ItemStack cursorStack) {
                            int matchingItems = 0;
                            for (int slot = 0; slot < stacks.size(); slot++) {
                                if (playerInvSlots.contains(slot)) {
                                    continue;
                                }
                                ItemStack stack = stacks.get(slot);
                                if (searchingFor.test(stack)) {
                                    matchingItems += stack.getCount();
                                }
                                if (searchShulkerBoxes && stack.getItem() instanceof BlockItem && ((BlockItem) stack.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                                    NbtCompound blockEntityTag = BlockItem.getBlockEntityNbt(stack);
                                    if (blockEntityTag != null && blockEntityTag.contains("Items", NbtElement.LIST_TYPE)) {
                                        matchingItems += countItems(blockEntityTag.getList("Items", NbtElement.COMPOUND_TYPE));
                                    }
                                }
                            }
                            if (matchingItems > 0) {
                                printLocation(currentlySearching, matchingItems);
                                totalFound += matchingItems;
                            }
                            currentlySearching = null;
                            currentlySearchingTimeout = 0;
                            mc.player.closeHandledScreen();
                        }
                    };
                    return false;
                }
            });
            assert mc.interactionManager != null;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(clickPos,
                            Direction.getFacing((float) (clickPos.x - cameraPos.x), (float) (clickPos.y - cameraPos.y), (float) (clickPos.z - cameraPos.z)),
                            pos, false));
        }
    }

    private static class NbtQueryFindItemsTask extends AbstractFindItemsTask {
        private static final long MAX_SCAN_TIME = 30_000_000L; // 30ms
        private static final int NO_RESPONSE_TIMEOUT = 100; // ticks


        private final Set<BlockPos> searchedBlocks = new HashSet<>();
        private boolean isScanning = true;
        private Iterator<BlockPos.Mutable> scanningIterator;
        private final Set<BlockPos> waitingOnBlocks = new HashSet<>();
        private int currentlySearchingTimeout;
        @Nullable
        private BlockPos enderChestPosition = null;
        @Nullable
        private Integer numItemsInEnderChest = null;
        private boolean hasPrintedEnderChest = false;

        public NbtQueryFindItemsTask(String searchingForName, Predicate<ItemStack> searchingFor, boolean searchShulkerBoxes, boolean keepSearching) {
            super(searchingForName, searchingFor, searchShulkerBoxes, keepSearching);
        }

        @Override
        public boolean condition() {
            return true;
        }

        @Override
        protected void onTick() {
            Entity cameraEntity = MinecraftClient.getInstance().cameraEntity;
            if (cameraEntity == null) {
                _break();
                return;
            }
            ClientWorld world = MinecraftClient.getInstance().world;
            assert world != null;

            if (isScanning) {
                long startTime = System.nanoTime();
                if (scanningIterator == null) {
                    Vec3d cameraPos = cameraEntity.getCameraPosVec(0);
                    scanningIterator = BlockPos.iterateInSquare(new BlockPos(MathHelper.floor(cameraPos.x) >> 4, 0, MathHelper.floor(cameraPos.z) >> 4), MinecraftClient.getInstance().options.getViewDistance().getValue(), Direction.EAST, Direction.SOUTH).iterator();
                }
                while (scanningIterator.hasNext()) {
                    BlockPos chunkPosAsBlockPos = scanningIterator.next();
                    if (world.getChunk(chunkPosAsBlockPos.getX(), chunkPosAsBlockPos.getZ(), ChunkStatus.FULL, false) != null) {
                        scanChunk(new ChunkPos(chunkPosAsBlockPos.getX(), chunkPosAsBlockPos.getZ()), cameraEntity);
                    }

                    if (System.nanoTime() - startTime > MAX_SCAN_TIME) {
                        // wait a tick
                        return;
                    }
                }
                isScanning = false;
            }

            if (waitingOnBlocks.isEmpty() && (enderChestPosition == null || numItemsInEnderChest != null)) {
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

        private void scanChunk(ChunkPos chunkToScan, Entity cameraEntity) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            assert player != null;
            ClientWorld world = MinecraftClient.getInstance().world;
            assert world != null;
            ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
            assert networkHandler != null;

            // check if we can possibly find a closer ender chest
            if (enderChestPosition != null && numItemsInEnderChest != null && !hasPrintedEnderChest) {
                Vec3d cameraPos = cameraEntity.getCameraPosVec(0);
                double enderChestDistanceSq = enderChestPosition.getSquaredDistance(cameraPos);
                int cameraChunkX = MathHelper.floor(cameraPos.x) >> 4;
                int cameraChunkZ = MathHelper.floor(cameraPos.z) >> 4;
                int currentChunkRadius = Math.max(Math.abs(cameraChunkX - chunkToScan.x), Math.abs(cameraChunkZ - chunkToScan.z));
                double closestPossibleDistance = ((currentChunkRadius - 1) << 4) + Math.min(
                    Math.min(cameraPos.x - (cameraChunkX << 4), cameraPos.z - (cameraChunkZ << 4)),
                    Math.min(((cameraChunkX + 1) << 4) - cameraPos.x, ((cameraChunkZ + 1) << 4) - cameraPos.z));
                if (enderChestDistanceSq < closestPossibleDistance * closestPossibleDistance) {
                    hasPrintedEnderChest = true;
                    if (numItemsInEnderChest > 0) {
                        printLocation(enderChestPosition, numItemsInEnderChest);
                    }
                }
            }

            WorldChunk chunk = world.getChunk(chunkToScan.x, chunkToScan.z);

            int minSection = chunk.getBottomSectionCoord();
            int maxSection = chunk.getTopSectionCoord();
            for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                if (!chunk.getSection(chunk.sectionCoordToIndex(sectionY)).hasAny(state -> state.isOf(Blocks.ENDER_CHEST) || state.hasBlockEntity())) {
                    continue;
                }

                for (BlockPos pos : BlockPos.iterate(chunkToScan.getStartX(), sectionY << 4, chunkToScan.getStartZ(), chunkToScan.getEndX(), (sectionY << 4) + 15, chunkToScan.getEndZ())) {
                    if (searchedBlocks.contains(pos)) {
                        continue;
                    }
                    BlockState state = chunk.getBlockState(pos);

                    if (state.isOf(Blocks.ENDER_CHEST)) {
                        BlockPos currentPos = pos.toImmutable();
                        searchedBlocks.add(currentPos);
                        if (enderChestPosition == null) {
                            enderChestPosition = currentPos;
                            currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
                            ClientcommandsDataQueryHandler.get(networkHandler).queryEntityNbt(player.getId(), playerNbt -> {
                                int numItemsInEnderChest = 0;
                                if (playerNbt != null && playerNbt.contains("EnderItems", NbtElement.LIST_TYPE)) {
                                    numItemsInEnderChest = countItems(playerNbt.getList("EnderItems", NbtElement.COMPOUND_TYPE));
                                }
                                this.numItemsInEnderChest = numItemsInEnderChest;
                                totalFound += numItemsInEnderChest;
                                currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
                            });
                        } else if (!hasPrintedEnderChest) {
                            Vec3d cameraPos = cameraEntity.getCameraPosVec(0);
                            double currentDistanceSq = enderChestPosition.getSquaredDistance(cameraPos);
                            double newDistanceSq = currentPos.getSquaredDistance(cameraPos);
                            if (newDistanceSq < currentDistanceSq) {
                                enderChestPosition = currentPos;
                            }
                        }
                    } else if (chunk.getBlockEntity(pos) instanceof Inventory) {
                        BlockPos currentPos = pos.toImmutable();
                        searchedBlocks.add(currentPos);
                        waitingOnBlocks.add(currentPos);
                        currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
                        ClientcommandsDataQueryHandler.get(networkHandler).queryBlockNbt(currentPos, blockNbt -> {
                            waitingOnBlocks.remove(currentPos);
                            if (blockNbt != null && blockNbt.contains("Items", NbtElement.LIST_TYPE)) {
                                int count = countItems(blockNbt.getList("Items", NbtElement.COMPOUND_TYPE));
                                if (count > 0) {
                                    totalFound += count;
                                    printLocation(currentPos, count);
                                }
                            }
                            currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
                        });
                    }
                }
            }
        }

        @Override
        public void onCompleted() {
            if (enderChestPosition != null && numItemsInEnderChest != null && numItemsInEnderChest > 0 && !hasPrintedEnderChest) {
                printLocation(enderChestPosition, numItemsInEnderChest);
            }
            super.onCompleted();
        }
    }
}
