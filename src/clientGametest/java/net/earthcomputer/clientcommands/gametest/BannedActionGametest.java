package net.earthcomputer.clientcommands.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public final class BannedActionGametest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            singleplayer.getServer().runOnServer(server -> setInventoryItem(server, 0, new ItemStack(Items.COBBLESTONE, 64)));
            context.waitTick();

            runClientCommand(context, "cconfig clientcommands playerRNGMaintenance set false");
            runClientCommand(context, "ccrackrng");
            context.waitFor(client -> Configs.playerCrackState.knowsSeed());

            long playerSeed = getPlayerSeed(singleplayer);
            if (playerSeed != PlayerRandCracker.getSeed()) {
                throw new AssertionError("Seed found by ccrackrng (%012x) does not match actual seed (%012x)".formatted(PlayerRandCracker.getSeed(), playerSeed));
            }

            context.waitTicks(100);

            if (getPlayerSeed(singleplayer) != playerSeed) {
                throw new AssertionError("Player seed has changed after doing nothing, something is very wrong!");
            }

            testAmethystChime(context, singleplayer);
            testAnvil(context, singleplayer);
            testBaneOfArthropods(context, singleplayer);
            testCrossbow(context, singleplayer);
            testDrink(context, singleplayer);
            testDropItem(context);
            testEnterWater(context, singleplayer);
            testEquipItem(context, singleplayer);
            testFallFlying(context, singleplayer);
            testFood(context, singleplayer);
            testFrostWalker(context, singleplayer);
            testGiveCommand(context);
            testItemBreak(context, singleplayer);

            context.waitTicks(100);
        }
    }

    private static void testAmethystChime(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos pos = getPlayerPos(context);
        singleplayer.getServer().runOnServer(server -> {
            for (int i = 1; i <= 2; i++) {
                server.overworld().setBlockAndUpdate(pos.south(i).below(), Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
        });
        context.getInput().holdKey(options -> options.keyUp);
        context.waitFor(client -> client.player.blockPosition().getZ() >= pos.getZ() + 3);
        context.getInput().releaseKey(options -> options.keyUp);
        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger amethyst chime detection");
        }
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testAnvil(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("xp set GametestUser 1 levels");
        BlockPos pos = getPlayerPos(context);
        singleplayer.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos.south(), Blocks.ANVIL.defaultBlockState()));
        context.waitTick();
        runClientCommand(context, "clook block %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ() + 1));
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);
        context.runOnClient(client -> client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, 30, 0, ClickType.QUICK_MOVE, client.player));
        context.getInput().holdControl();
        context.getInput().pressKey(InputConstants.KEY_A);
        context.getInput().releaseControl();
        context.getInput().typeChars("test");
        context.runOnClient(client -> client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, 2, 0, ClickType.QUICK_MOVE, client.player));
        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger anvil detection");
        }
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        singleplayer.getServer().runOnServer(server -> server.overworld().removeBlock(pos.south(), false));
        context.waitTick();
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testBaneOfArthropods(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        MultiVersionCompat.setProtocolVersion(MultiVersionCompat.V1_20_6, "1.20.6");

        BlockPos pos = getPlayerPos(context);

        Spider spider = singleplayer.getServer().computeOnServer(server -> {
            ItemStack baneOfArthropodsSword = new ItemStack(Items.DIAMOND_SWORD);
            EnchantmentHelper.updateEnchantments(baneOfArthropodsSword, enchantments -> {
                Registry<Enchantment> enchantmentRegistry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                enchantments.set(enchantmentRegistry.getOrThrow(Enchantments.BANE_OF_ARTHROPODS), 1);
            });
            setInventoryItem(server, 0, baneOfArthropodsSword);

            Spider spider1 = EntityType.SPIDER.create(server.overworld(), EntitySpawnReason.COMMAND);
            spider1.setNoAi(true);
            spider1.moveTo(Vec3.atCenterOf(pos.south()));
            server.overworld().addFreshEntity(spider1);
            return spider1;
        });

        context.waitTick();
        context.getInput().pressKey(options -> options.keyAttack);
        context.waitTicks(2);

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger bane of arthropods detection");
        }

        singleplayer.getServer().runOnServer(server -> spider.remove(Entity.RemovalReason.DISCARDED));

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        MultiVersionCompat.setLatestProtocol();
    }

    private static void testCrossbow(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            setInventoryItem(server, 0, new ItemStack(Items.CROSSBOW));
            setInventoryItem(server, 1, new ItemStack(Items.ARROW));
        });

        context.waitTick();
        useItemUntilCompleted(context, singleplayer);
        context.waitTick();
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTick();

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger crossbow detection");
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testDrink(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> setInventoryItem(server, 0, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
        context.waitTick();
        useItemUntilCompleted(context, singleplayer);
        context.waitTick();

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger drink detection");
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testDropItem(ClientGameTestContext context) {
        context.getInput().pressKey(options -> options.keyDrop);
        context.waitTick();

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger drop detection");
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testEnterWater(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos pos = getPlayerPos(context);
        singleplayer.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState()));
        context.waitTicks(2);
        singleplayer.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
        context.waitTick();

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger enter water detection");
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testEquipItem(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> setInventoryItem(server, 0, new ItemStack(Items.DIAMOND_CHESTPLATE)));
        context.waitTick();
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(2);

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger equip detection");
        }

        singleplayer.getServer().runOnServer(server -> setInventoryItem(server, 38, ItemStack.EMPTY));
        context.waitTick();
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testFallFlying(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            setInventoryItem(server, 38, new ItemStack(Items.ELYTRA));
            getServerPlayer(server).teleportRelative(0, 2, 0);
        });
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyJump, 1);

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger fall flying detection");
        }

        singleplayer.getServer().runOnServer(server -> setInventoryItem(server, 38, ItemStack.EMPTY));
        context.waitFor(client -> client.player.onGround());
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testFood(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> setInventoryItem(server, 0, new ItemStack(Items.GOLDEN_APPLE)));
        context.waitTick();
        context.getInput().holdKeyFor(options -> options.keyUse, 10);

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger eat detection");
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testFrostWalker(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        MultiVersionCompat.setProtocolVersion(MultiVersionCompat.V1_20_6, "1.20.6");
        BlockPos pos = getPlayerPos(context);
        singleplayer.getServer().runOnServer(server -> {
            ItemStack frostWalkerBoots = new ItemStack(Items.DIAMOND_BOOTS);
            EnchantmentHelper.updateEnchantments(frostWalkerBoots, enchantments -> {
                Registry<Enchantment> enchantmentRegistry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                enchantments.set(enchantmentRegistry.getOrThrow(Enchantments.FROST_WALKER), 1);
            });
            setInventoryItem(server, 36, frostWalkerBoots);
            server.overworld().setBlockAndUpdate(pos.south().below(), Blocks.WATER.defaultBlockState());
        });

        context.getInput().holdKey(options -> options.keyUp);
        context.waitFor(client -> !client.player.blockPosition().equals(pos));
        context.waitTick();
        context.getInput().releaseKey(options -> options.keyUp);

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger frost walker detection");
        }

        singleplayer.getServer().runOnServer(server -> {
            setInventoryItem(server, 36, ItemStack.EMPTY);
            server.overworld().setBlockAndUpdate(pos.south().below(), Blocks.GRASS_BLOCK.defaultBlockState());
        });
        context.waitTick();

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        MultiVersionCompat.setLatestProtocol();
    }

    private static void testGiveCommand(ClientGameTestContext context) {
        context.getInput().pressKey(options -> options.keyChat);
        context.waitTick();
        context.getInput().typeChars("/give @s cobblestone");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(2);

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger give command detection");
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
    }

    private static void testItemBreak(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        MultiVersionCompat.setProtocolVersion(MultiVersionCompat.V1_13, "1.13");

        singleplayer.getServer().runOnServer(server -> {
            ItemStack almostBrokenShovel = new ItemStack(Items.DIAMOND_SHOVEL);
            almostBrokenShovel.setDamageValue(almostBrokenShovel.getMaxDamage() - 1);
            setInventoryItem(server, 0, almostBrokenShovel);
        });

        runClientCommand(context, "clook cardinal down");
        context.waitTick();
        context.getInput().pressKey(options -> options.keyUse);

        if (Configs.playerCrackState.knowsSeed()) {
            throw new AssertionError("Didn't trigger item break detection");
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        MultiVersionCompat.setLatestProtocol();
    }

    private static void runClientCommand(ClientGameTestContext context, String command) {
        try {
            context.runOnClient(client -> {
                var dispatcher = Objects.requireNonNull(ClientCommandManager.getActiveDispatcher());
                ClientPacketListener connection = Objects.requireNonNull(client.getConnection());
                dispatcher.execute(command, (FabricClientCommandSource) connection.getSuggestionsProvider());
            });
        } catch (CommandSyntaxException e) {
            throw new RuntimeException("Exception while running command", e);
        }
    }

    private static long getPlayerSeed(TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> ((LegacyRandomSource) getServerPlayer(server).getRandom()).seed.get());
    }

    private static ServerPlayer getServerPlayer(MinecraftServer server) {
        return Objects.requireNonNull(server.getPlayerList().getPlayerByName("GametestUser"), "Couldn't find player in server");
    }

    private static void setInventoryItem(MinecraftServer server, int slot, ItemStack stack) {
        ServerPlayer player = getServerPlayer(server);
        player.getInventory().setItem(slot, stack);
        player.containerMenu.broadcastChanges();
    }

    private static void useItemUntilCompleted(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTick();
        context.waitTicks(singleplayer.getServer().computeOnServer(server -> getServerPlayer(server).getUseItemRemainingTicks()));
        context.waitTick(); // TODO: this is sometimes needed and sometimes not, fix this inconsistency in FAPI
        context.getInput().releaseKey(options -> options.keyUse);
    }

    private static BlockPos getPlayerPos(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.player.blockPosition());
    }
}
