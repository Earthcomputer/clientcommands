package net.earthcomputer.clientcommands.gametest;

import com.google.common.collect.Lists;
import net.earthcomputer.clientcommands.server.ServerTaskQueue;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

import static net.earthcomputer.clientcommands.gametest.ClientCommandsClientGameTest.*;

// In singleplayer, clientcommands is also installed on the integrated server, so /cfinditem uses the server-side search
@SuppressWarnings("UnstableApiUsage")
final class FindItemTests {
    // further than any render distance
    private static final int FAR_CHUNK_X = 40;
    private static final BlockPos FAR_CHEST_POS = new BlockPos(FAR_CHUNK_X * 16, PLAYER_Y, 0);

    @TestCase
    private static void testServerSearch(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            BlockPos lootChestPos = SPAWN_POS.offset(0, 0, 5);
            setBlock(singleplayerContext, lootChestPos, Blocks.CHEST);
            singleplayerContext.getServer().runOnServer(_ ->
                ((RandomizableContainer) getContainer(singleplayerContext.getConnection().getServerLevel(), lootChestPos)).setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 0));

            testFindsContainers(context, singleplayerContext, "", SPAWN_POS.offset(3, 0, 0), SPAWN_POS.offset(-3, 0, 2), SPAWN_POS.offset(-5, 0, -5));

            boolean lootGenerated = singleplayerContext.getServer().computeOnServer(_ ->
                ((RandomizableContainer) getContainer(singleplayerContext.getConnection().getServerLevel(), lootChestPos)).getLootTable() == null);
            assertTrue(!lootGenerated, () -> "Searching generated the loot of an unopened chest");
        });
    }

    @TestCase
    private static void testNbtQuerySearch(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            // without permission to use the server-side search, operators query the NBT of the inventories instead
            setServerFindItem(singleplayerContext, false);
            setOp(singleplayerContext, true);
            try {
                singleplayerContext.getConnection().waitForClientboundPackets();
                boolean isOp = context.computeOnClient(client -> Objects.requireNonNull(client.player).permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
                assertTrue(isOp, () -> "Expected the player to be an operator");
                testFindsContainers(context, singleplayerContext, "", SPAWN_POS.offset(3, 0, 0), SPAWN_POS.offset(-3, 0, 2), SPAWN_POS.offset(-5, 0, -5));
            } finally {
                setOp(singleplayerContext, false);
            }
        });
    }

    @TestCase
    private static void testClickInventoriesSearch(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            // the inventories have to be within reach to be clicked
            testFindsContainers(context, singleplayerContext, "--click-inventories ", SPAWN_POS.offset(2, 0, 0), SPAWN_POS.offset(-2, 0, 1), SPAWN_POS.offset(0, 0, -1))
        );
    }

    // Puts 5 diamonds in a chest, 3 diamonds in a shulker box in a barrel and 2 diamonds in a chest minecart, and checks that they are found
    private static void testFindsContainers(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, String flags, BlockPos chestPos, BlockPos barrelPos, BlockPos minecartPos) {
        setBlock(singleplayerContext, chestPos, Blocks.CHEST);
        setBlock(singleplayerContext, barrelPos, Blocks.BARREL);
        MinecartChest minecart = summon(singleplayerContext, EntityTypes.CHEST_MINECART, Vec3.atBottomCenterOf(minecartPos));
        singleplayerContext.getServer().runOnServer(_ -> {
            ServerLevel level = singleplayerContext.getConnection().getServerLevel();
            getContainer(level, chestPos).setItem(0, new ItemStack(Items.DIAMOND, 5));
            ItemStack shulkerBox = new ItemStack(Items.SHULKER_BOX);
            shulkerBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3))));
            getContainer(level, barrelPos).setItem(0, shulkerBox);
            minecart.setItem(0, new ItemStack(Items.DIAMOND, 2));
        });

        assertEquals(10, findItem(context, singleplayerContext, "cfinditem " + flags + "minecraft:diamond"), () -> "Expected to find 10 diamonds");
        assertEquals(1, findItem(context, singleplayerContext, "cfinditem " + flags + "minecraft:shulker_box"), () -> "Expected to find the shulker box itself");
        assertEquals(7, findItem(context, singleplayerContext, "cfinditem " + flags + "--no-search-nested minecraft:diamond"), () -> "Expected to find 7 diamonds outside of the shulker box");
    }

    @TestCase
    private static void testAllLoadedChunks(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            singleplayerContext.getServer().runOnServer(_ -> {
                ServerLevel level = singleplayerContext.getConnection().getServerLevel();
                level.setChunkForced(FAR_CHUNK_X, 0, true);
                level.setBlockAndUpdate(FAR_CHEST_POS, Blocks.CHEST.defaultBlockState());
                getContainer(level, FAR_CHEST_POS).setItem(0, new ItemStack(Items.EMERALD, 7));
            });
            try {
                assertEquals(0, findItem(context, singleplayerContext, "cfinditem minecraft:emerald"), () -> "Expected not to find the chest outside of render distance");
                assertEquals(7, findItem(context, singleplayerContext, "cfinditem --all-loaded-chunks minecraft:emerald"), () -> "Expected to find the chest in a loaded chunk");
            } finally {
                singleplayerContext.getServer().runOnServer(_ -> {
                    ServerLevel level = singleplayerContext.getConnection().getServerLevel();
                    level.removeBlock(FAR_CHEST_POS, false);
                    level.setChunkForced(FAR_CHUNK_X, 0, false);
                });
            }
        });
    }

    @TestCase
    private static void testCancel(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            runClientCommand(context, "cfinditem --keep-searching minecraft:diamond");
            singleplayerContext.getServer().waitFor(_ -> ServerTaskQueue.getTaskCount() == 1, 20);
            context.runOnClient(_ -> {
                for (String taskName : Lists.newArrayList(TaskManager.getTaskNames())) {
                    TaskManager.removeTask(taskName);
                }
            });
            singleplayerContext.getServer().waitFor(_ -> ServerTaskQueue.getTaskCount() == 0, 20);
        });
    }

    @TestCase
    private static void testPermissionRevoked(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            clearChat(context);
            runClientCommand(context, "cfinditem --keep-searching minecraft:diamond");
            singleplayerContext.getServer().waitFor(_ -> ServerTaskQueue.getTaskCount() == 1, 20);
            setServerFindItem(singleplayerContext, false);
            context.waitFor(_ -> TaskManager.getTaskCount() == 0, 20);
            assertEquals(0, singleplayerContext.getServer().computeOnServer(_ -> ServerTaskQueue.getTaskCount()), () -> "Expected the server search to be cancelled");
            assertTrue(chatContains(context, "commands.cfinditem.server.noPermission"), () -> "Expected a no permission message");

            // falls back to a client-side search, which can't search all loaded chunks
            runClientCommand(context, "cfinditem --all-loaded-chunks minecraft:diamond");
            context.waitTick();
            assertTrue(chatContains(context, "commands.cfinditem.allLoadedChunks.requiresServer"), () -> "Expected searching all loaded chunks to require the server");
            assertEquals(0, TaskManager.getTaskCount(), () -> "Expected no task to be started");
        });
    }

    private static void setOp(TestSingleplayerContext singleplayerContext, boolean op) {
        singleplayerContext.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            if (op) {
                server.getPlayerList().op(player.nameAndId());
            } else {
                server.getPlayerList().deop(player.nameAndId());
            }
        });
    }

    private static Container getContainer(ServerLevel level, BlockPos pos) {
        return (Container) Objects.requireNonNull(level.getBlockEntity(pos), () -> "No block entity at " + pos);
    }

    // Runs the command, waits for it to finish and returns the total number of items found
    private static int findItem(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, String command) {
        singleplayerContext.getConnection().waitForClientboundPackets();
        clearChat(context);
        runClientCommand(context, command);
        context.waitTick();
        context.waitFor(_ -> TaskManager.getTaskCount() == 0, 200);
        Object total = findTranslationArg(context, "commands.cfinditem.total", 0);
        return Integer.parseInt(String.valueOf(total));
    }
}
