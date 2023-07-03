package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
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
        String taskName = TaskManager.addTask("cfinditem", new FindItemsTask(item.getLeft(), item.getRight(), !noSearchShulkerBox, keepSearching));
        if (keepSearching) {
            ctx.getSource().sendFeedback(Text.translatable("commands.cfinditem.starting.keepSearching", item.getLeft())
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));
        } else {
            ctx.getSource().sendFeedback(Text.translatable("commands.cfinditem.starting", item.getLeft()));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static class FindItemsTask extends SimpleTask {
        private final String searchingForName;
        private final Predicate<ItemStack> searchingFor;
        private final boolean searchShulkerBoxes;
        private final boolean keepSearching;

        private int totalFound = 0;
        private final Set<BlockPos> searchedBlocks = new HashSet<>();
        private BlockPos currentlySearching = null;
        private int currentlySearchingTimeout;
        private boolean hasSearchedEnderChest = false;

        public FindItemsTask(String searchingForName, Predicate<ItemStack> searchingFor, boolean searchShulkerBoxes, boolean keepSearching) {
            this.searchingForName = searchingForName;
            this.searchingFor = searchingFor;
            this.searchShulkerBoxes = searchShulkerBoxes;
            this.keepSearching = keepSearching;
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
                    if (!(screen instanceof ScreenHandlerProvider handlerProvider)) {
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
                                    NbtCompound blockEntityTag = stack.getSubNbt("BlockEntityTag");
                                    if (blockEntityTag != null && blockEntityTag.contains("Items")) {
                                        DefaultedList<ItemStack> boxInv = DefaultedList.ofSize(27, ItemStack.EMPTY);
                                        Inventories.readNbt(blockEntityTag, boxInv);
                                        for (ItemStack stackInBox : boxInv) {
                                            if (searchingFor.test(stackInBox)) {
                                                matchingItems += stackInBox.getCount();
                                            }
                                        }
                                    }
                                }
                            }
                            if (matchingItems > 0) {
                                sendFeedback(Text.translatable("commands.cfinditem.match.left", matchingItems, searchingForName)
                                        .append(getLookCoordsTextComponent(currentlySearching))
                                        .append(Text.translatable("commands.cfinditem.match.right", matchingItems, searchingForName)));
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

        @Override
        public void onCompleted() {
            sendFeedback(Text.translatable("commands.cfinditem.total", totalFound, searchingForName).formatted(Formatting.BOLD));
        }
    }
}
