package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.earthcomputer.clientcommands.ClientcommandsDataQueryHandler;
import net.earthcomputer.clientcommands.GuiBlocker;
import net.earthcomputer.clientcommands.MathUtil;
import net.earthcomputer.clientcommands.mixin.AbstractContainerMenuAccessor;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
    private static final Flag<Boolean> FLAG_NO_SEARCH_SHULKER_BOX = Flag.ofFlag("no-search-shulker-box").withShortName('s').build();
    private static final Flag<Boolean> FLAG_KEEP_SEARCHING = Flag.ofFlag("keep-searching").build();

    @SuppressWarnings("unchecked")
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        var cfinditem = dispatcher.register(literal("cfinditem")
            .then(argument("item", withString(itemPredicate(context)))
                .executes(ctx ->
                    findItem(ctx,
                        getFlag(ctx, FLAG_NO_SEARCH_SHULKER_BOX),
                        getFlag(ctx, FLAG_KEEP_SEARCHING),
                        getWithString(ctx, "item", (Class<Predicate<ItemStack>>) (Class<?>) Predicate.class)))));
        FLAG_NO_SEARCH_SHULKER_BOX.addToCommand(dispatcher, cfinditem, ctx -> true);
        FLAG_KEEP_SEARCHING.addToCommand(dispatcher, cfinditem, ctx -> true);
    }

    private static int findItem(CommandContext<FabricClientCommandSource> ctx, boolean noSearchShulkerBox, boolean keepSearching, Pair<String, Predicate<ItemStack>> item) {
        String taskName = TaskManager.addTask("cfinditem", makeFindItemsTask(item.getLeft(), item.getRight(), !noSearchShulkerBox, keepSearching));
        if (keepSearching) {
            ctx.getSource().sendFeedback(Component.translatable("commands.cfinditem.starting.keepSearching", item.getLeft())
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));
        } else {
            ctx.getSource().sendFeedback(Component.translatable("commands.cfinditem.starting", item.getLeft()));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static SimpleTask makeFindItemsTask(String searchingForName, Predicate<ItemStack> searchingFor, boolean searchShulkerBoxes, boolean keepSearching) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        if (player.hasPermissions(2)) {
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

        protected int countItems(ListTag inventory) {
            int result = 0;
            for (int i = 0; i < inventory.size(); i++) {
                CompoundTag compound = inventory.getCompound(i);
                ItemStack stack = ItemStack.of(compound);
                if (searchingFor.test(stack)) {
                    result += stack.getCount();
                }
                if (searchShulkerBoxes && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) {
                    CompoundTag blockEntityNbt = BlockItem.getBlockEntityData(stack);
                    if (blockEntityNbt != null && blockEntityNbt.contains("Items", Tag.TAG_LIST)) {
                        result += countItems(blockEntityNbt.getList("Items", Tag.TAG_COMPOUND));
                    }
                }
            }
            return result;
        }

        protected void printLocation(BlockPos pos, int count) {
            sendFeedback(Component.translatable("commands.cfinditem.match.left", count, searchingForName)
                .append(getLookCoordsTextComponent(pos))
                .append(" ")
                .append(getGlowCoordsTextComponent(Component.translatable("commands.cfindblock.success.glow"), pos))
                .append(Component.translatable("commands.cfinditem.match.right", count, searchingForName)));
        }

        @Override
        public void onCompleted() {
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.cfinditem.total", totalFound, searchingForName).withStyle(ChatFormatting.BOLD));
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
            Entity entity = Minecraft.getInstance().cameraEntity;
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
            float reachDistance = gameMode.getPickRange();
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
                        if (state.getBlock() == Blocks.ENDER_CHEST) {
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

        private boolean canSearch(Level level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof Container) && state.getBlock() != Blocks.ENDER_CHEST) {
                return false;
            }
            if (state.getBlock() instanceof ChestBlock || state.getBlock() == Blocks.ENDER_CHEST) {
                if (ChestBlock.isChestBlockedAt(level, pos)) {
                    return false;
                }
                if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos offsetPos = pos.relative(ChestBlock.getConnectedDirection(state));
                    return level.getBlockState(offsetPos).getBlock() != state.getBlock() || !ChestBlock.isChestBlockedAt(level, offsetPos);
                }
            }
            return true;
        }

        private void startSearch(BlockPos pos, Vec3 cameraPos, Vec3 clickPos) {
            Minecraft mc = Minecraft.getInstance();
            currentlySearching = pos;
            currentlySearchingTimeout = 100;
            GuiBlocker.addBlocker(new GuiBlocker() {
                @Override
                public boolean accept(Screen screen) {
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
                    mc.player.containerMenu = new AbstractContainerMenu(((AbstractContainerMenuAccessor) container).getNullableType(), container.containerId) {
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
                                    CompoundTag blockEntityTag = BlockItem.getBlockEntityData(stack);
                                    if (blockEntityTag != null && blockEntityTag.contains("Items", Tag.TAG_LIST)) {
                                        matchingItems += countItems(blockEntityTag.getList("Items", Tag.TAG_COMPOUND));
                                    }
                                }
                            }
                            if (matchingItems > 0) {
                                printLocation(currentlySearching, matchingItems);
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
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(clickPos,
                            Direction.getNearest((float) (clickPos.x - cameraPos.x), (float) (clickPos.y - cameraPos.y), (float) (clickPos.z - cameraPos.z)),
                            pos, false));
        }
    }

    private static class NbtQueryFindItemsTask extends AbstractFindItemsTask {
        private static final long MAX_SCAN_TIME = 30_000_000L; // 30ms
        private static final int NO_RESPONSE_TIMEOUT = 100; // ticks


        private final Set<BlockPos> searchedBlocks = new HashSet<>();
        private boolean isScanning = true;
        private Iterator<BlockPos.MutableBlockPos> scanningIterator;
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
            Entity cameraEntity = Minecraft.getInstance().cameraEntity;
            if (cameraEntity == null) {
                _break();
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;

            if (isScanning) {
                long startTime = System.nanoTime();
                if (scanningIterator == null) {
                    Vec3 cameraPos = cameraEntity.getEyePosition(0);
                    scanningIterator = BlockPos.spiralAround(new BlockPos(Mth.floor(cameraPos.x) >> 4, 0, Mth.floor(cameraPos.z) >> 4), Minecraft.getInstance().options.renderDistance().get(), Direction.EAST, Direction.SOUTH).iterator();
                }
                while (scanningIterator.hasNext()) {
                    BlockPos chunkPosAsBlockPos = scanningIterator.next();
                    if (level.getChunk(chunkPosAsBlockPos.getX(), chunkPosAsBlockPos.getZ(), ChunkStatus.FULL, false) != null) {
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
            LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;
            ClientPacketListener packetListener = Minecraft.getInstance().getConnection();
            assert packetListener != null;

            // check if we can possibly find a closer ender chest
            if (enderChestPosition != null && numItemsInEnderChest != null && !hasPrintedEnderChest) {
                Vec3 cameraPos = cameraEntity.getEyePosition(0);
                double enderChestDistanceSq = enderChestPosition.distToCenterSqr(cameraPos);
                int cameraChunkX = Mth.floor(cameraPos.x) >> 4;
                int cameraChunkZ = Mth.floor(cameraPos.z) >> 4;
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

            LevelChunk chunk = level.getChunk(chunkToScan.x, chunkToScan.z);

            int minSection = chunk.getMinSection();
            int maxSection = chunk.getMaxSection();
            for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                if (!chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY)).maybeHas(state -> state.is(Blocks.ENDER_CHEST) || state.hasBlockEntity())) {
                    continue;
                }

                for (BlockPos pos : BlockPos.betweenClosed(chunkToScan.getMinBlockX(), sectionY << 4, chunkToScan.getMinBlockZ(), chunkToScan.getMaxBlockX(), (sectionY << 4) + 15, chunkToScan.getMaxBlockZ())) {
                    if (searchedBlocks.contains(pos)) {
                        continue;
                    }
                    BlockState state = chunk.getBlockState(pos);

                    if (state.is(Blocks.ENDER_CHEST)) {
                        BlockPos currentPos = pos.immutable();
                        searchedBlocks.add(currentPos);
                        if (enderChestPosition == null) {
                            enderChestPosition = currentPos;
                            currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
                            ClientcommandsDataQueryHandler.get(packetListener).queryEntityNbt(player.getId(), playerNbt -> {
                                int numItemsInEnderChest = 0;
                                if (playerNbt != null && playerNbt.contains("EnderItems", Tag.TAG_LIST)) {
                                    numItemsInEnderChest = countItems(playerNbt.getList("EnderItems", Tag.TAG_COMPOUND));
                                }
                                this.numItemsInEnderChest = numItemsInEnderChest;
                                totalFound += numItemsInEnderChest;
                                currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
                            });
                        } else if (!hasPrintedEnderChest) {
                            Vec3 cameraPos = cameraEntity.getEyePosition(0);
                            double currentDistanceSq = enderChestPosition.distToCenterSqr(cameraPos);
                            double newDistanceSq = currentPos.distToCenterSqr(cameraPos);
                            if (newDistanceSq < currentDistanceSq) {
                                enderChestPosition = currentPos;
                            }
                        }
                    } else if (chunk.getBlockEntity(pos) instanceof Container) {
                        BlockPos currentPos = pos.immutable();
                        searchedBlocks.add(currentPos);
                        waitingOnBlocks.add(currentPos);
                        currentlySearchingTimeout = NO_RESPONSE_TIMEOUT;
                        ClientcommandsDataQueryHandler.get(packetListener).queryBlockNbt(currentPos, blockNbt -> {
                            waitingOnBlocks.remove(currentPos);
                            if (blockNbt != null && blockNbt.contains("Items", Tag.TAG_LIST)) {
                                int count = countItems(blockNbt.getList("Items", Tag.TAG_COMPOUND));
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
