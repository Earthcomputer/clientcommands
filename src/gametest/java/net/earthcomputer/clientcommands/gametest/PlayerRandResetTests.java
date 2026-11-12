package net.earthcomputer.clientcommands.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker.RNGCallType;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.earthcomputer.clientcommands.gametest.ClientCommandsClientGameTest.*;

// Player is at 0, 0, facing south (towards +Z)
@SuppressWarnings("UnstableApiUsage")
final class PlayerRandResetTests {
    private static final long KNOWN_SEED = 0x1234_5678_9abcL;
    private static final BlockPos FRONT_POS = SPAWN_POS.offset(0, 1, 1);

    // ===== MAINTAINABLE RNG CALLS ===== //

    @TestCase
    private static void testDropItem(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                give(singleplayerContext, Items.COBBLESTONE, 8);
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.DROP_ITEM, maintenance, () ->
                    context.getInput().pressKey(options -> options.keyDrop)
                );
            });
        }
    }

    @TestCase
    private static void testDropItemOnContainerClose(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                // fill the player's inventory so the item in the crafting grid gets dropped
                give(singleplayerContext, Items.COBBLESTONE, 64 * Inventory.INVENTORY_SIZE);
                openCraftingTable(context, singleplayerContext);
                putInCraftingGrid(singleplayerContext, new ItemStack(Items.STICK));
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.DROP_ITEM, maintenance, () -> closeContainerAndCheckDrop(context, singleplayerContext, true));
            });
        }
        // the item in the crafting grid gets put back in the inventory if there's space
        runTest(context, singleplayerContext, "space", () -> {
            give(singleplayerContext, Items.COBBLESTONE, 64 * (Inventory.INVENTORY_SIZE - 1));
            openCraftingTable(context, singleplayerContext);
            putInCraftingGrid(singleplayerContext, new ItemStack(Items.STICK));
            expectUnaffected(context, singleplayerContext, () -> closeContainerAndCheckDrop(context, singleplayerContext, false));
        });
        // before 1.12, items in the crafting grid were always dropped
        runTest(context, singleplayerContext, "1.11.1", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_11_1, "1.11.1");
            disableMaintenance(context);
            openCraftingTable(context, singleplayerContext);
            putInCraftingGrid(singleplayerContext, new ItemStack(Items.STICK));
            expectReset(context, singleplayerContext, RNGCallType.DROP_ITEM, () -> closeContainerAndCheckDrop(context, singleplayerContext, null));
        });
    }

    @TestCase
    private static void testDropCarriedItemOnContainerClose(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // since 1.17.1, the carried item gets put back in the inventory if there's space
        runTest(context, singleplayerContext, "current", () -> {
            openCraftingTable(context, singleplayerContext);
            setCarried(singleplayerContext, new ItemStack(Items.STICK));
            expectUnaffected(context, singleplayerContext, () -> closeContainerAndCheckDrop(context, singleplayerContext, false));
        });
        runTest(context, singleplayerContext, "full", () -> {
            disableMaintenance(context);
            give(singleplayerContext, Items.COBBLESTONE, 64 * Inventory.INVENTORY_SIZE);
            openCraftingTable(context, singleplayerContext);
            setCarried(singleplayerContext, new ItemStack(Items.STICK));
            expectReset(context, singleplayerContext, RNGCallType.DROP_ITEM, () -> closeContainerAndCheckDrop(context, singleplayerContext, true));
        });
        runTest(context, singleplayerContext, "1.17", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_17, "1.17");
            disableMaintenance(context);
            openCraftingTable(context, singleplayerContext);
            setCarried(singleplayerContext, new ItemStack(Items.STICK));
            expectReset(context, singleplayerContext, RNGCallType.DROP_ITEM, () -> closeContainerAndCheckDrop(context, singleplayerContext, null));
        });
    }

    @TestCase
    private static void testDropBeaconPaymentOnContainerClose(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                BlockPos beaconPos = FRONT_POS.below();
                setBlock(singleplayerContext, beaconPos, Blocks.BEACON);
                singleplayerContext.getConnection().waitForClientboundPackets();
                useItemOnBlock(context, beaconPos);
                singleplayerContext.getConnection().waitForServerboundPackets();
                singleplayerContext.getConnection().waitForClientboundPackets();
                context.waitForScreen(BeaconScreen.class);
                singleplayerContext.getServer().runOnServer(_ -> {
                    AbstractContainerMenu menu = singleplayerContext.getConnection().getServerPlayer().containerMenu;
                    menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT));
                    menu.broadcastChanges();
                });
                singleplayerContext.getConnection().waitForClientboundPackets();
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.DROP_ITEM, maintenance, () -> {
                    context.runOnClient(client -> Objects.requireNonNull(client.player).closeContainer());
                    singleplayerContext.getConnection().waitForServerboundPackets();
                    assertTrue(hasDroppedItem(singleplayerContext, Items.IRON_INGOT), () -> "The server didn't drop the iron ingot, the test is broken");
                });
            });
        }
    }

    @TestCase
    private static void testFood(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().getFoodData().setFoodLevel(10));
                give(singleplayerContext, Items.BREAD);
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.FOOD, maintenance, () -> useUntilConsumed(context, singleplayerContext, Items.BREAD));
            });
        }
    }

    @TestCase
    private static void testConsumeStatusEffects(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // the status effect may or may not be applied, but the random call is made either way
        for (float probability : new float[] {1, 0.5f}) {
            for (boolean maintenance : new boolean[] {true, false}) {
                runTest(context, singleplayerContext, probability + "_" + maintenanceVariant(maintenance), () -> {
                    singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().getFoodData().setFoodLevel(10));
                    give(singleplayerContext, breadWithStatusEffects(probability));
                    expectResetOrMaintained(context, singleplayerContext, RNGCallType.CONSUME_STATUS_EFFECTS, maintenance, () -> useUntilConsumed(context, singleplayerContext, Items.BREAD));
                });
            }
        }
        // Between 1.20.5 and 1.21.2, each status effect was rolled separately
        runTest(context, singleplayerContext, "1.21", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_21, "1.21");
            singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().getFoodData().setFoodLevel(10));
            give(singleplayerContext, breadWithStatusEffects(1));
            expectMaintained(context, singleplayerContext, RNGCallType.CONSUME_STATUS_EFFECTS, () -> useUntilConsumed(context, singleplayerContext, Items.BREAD));
        });
        // Food status effects used the level random before 1.20.5
        runTest(context, singleplayerContext, "1.20", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20, "1.20");
            singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().getFoodData().setFoodLevel(10));
            give(singleplayerContext, breadWithStatusEffects(1));
            List<RNGCallType> rngCalls = expectMaintained(context, singleplayerContext, RNGCallType.FOOD, () -> useUntilConsumed(context, singleplayerContext, Items.BREAD));
            assertTrue(!rngCalls.contains(RNGCallType.CONSUME_STATUS_EFFECTS), () -> "Expected no status effect RNG call, got " + rngCalls);
        });
    }

    // The status effects have no particles, which would otherwise reset the cracker
    private static ItemStack breadWithStatusEffects(float probability) {
        ItemStack bread = new ItemStack(Items.BREAD);
        bread.set(DataComponents.CONSUMABLE, Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                new MobEffectInstance(MobEffects.LUCK, 200, 0, false, false),
                new MobEffectInstance(MobEffects.HASTE, 200, 0, false, false)
            ), probability))
            .build());
        return bread;
    }

    @TestCase
    private static void testTeleport(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                give(singleplayerContext, Items.CHORUS_FRUIT);
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.TELEPORT, maintenance, () -> useUntilConsumed(context, singleplayerContext, Items.CHORUS_FRUIT));
            });
        }
    }

    @TestCase
    private static void testDrink(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                give(singleplayerContext, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.DRINK, maintenance, () -> useUntilConsumed(context, singleplayerContext, Items.POTION));
            });
        }
        // Drinking didn't use the player RNG before 1.21.2
        runTest(context, singleplayerContext, "1.21", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_21, "1.21");
            disableMaintenance(context);
            give(singleplayerContext, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            expectUnaffected(context, singleplayerContext, () -> useUntilConsumed(context, singleplayerContext, Items.POTION));
        });
    }

    @TestCase
    private static void testConsume(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                ItemStack stick = new ItemStack(Items.STICK);
                stick.set(DataComponents.CONSUMABLE, Consumable.builder().animation(ItemUseAnimation.TOOT_HORN).build());
                give(singleplayerContext, stick);
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.CONSUME, maintenance, () -> useUntilConsumed(context, singleplayerContext, Items.STICK));
            });
        }
    }

    @TestCase
    private static void testEquipItem(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                give(singleplayerContext, Items.IRON_HELMET);
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.EQUIP_ITEM, maintenance, () -> rightClick(context, singleplayerContext));
            });
        }
        // Equipping items didn't use the player RNG before 1.20.6
        runTest(context, singleplayerContext, "1.20", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20, "1.20");
            disableMaintenance(context);
            give(singleplayerContext, Items.IRON_HELMET);
            expectUnaffected(context, singleplayerContext, () -> rightClick(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testAnvil(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                BlockPos anvilPos = FRONT_POS.below();
                setBlock(singleplayerContext, anvilPos, Blocks.ANVIL);
                singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().setExperienceLevels(10));
                useItemOnBlock(context, anvilPos);
                singleplayerContext.getConnection().waitForServerboundPackets();
                singleplayerContext.getConnection().waitForClientboundPackets();
                context.waitForScreen(AnvilScreen.class);
                singleplayerContext.getServer().runOnServer(_ -> {
                    AbstractContainerMenu menu = singleplayerContext.getConnection().getServerPlayer().containerMenu;
                    menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.IRON_SWORD));
                    menu.broadcastChanges();
                });
                singleplayerContext.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> {
                    AnvilMenu menu = (AnvilMenu) Objects.requireNonNull(client.player).containerMenu;
                    if (menu.setItemName("Renamed")) {
                        client.player.connection.send(new ServerboundRenameItemPacket("Renamed"));
                    }
                });
                singleplayerContext.getConnection().waitForServerboundPackets();
                singleplayerContext.getConnection().waitForClientboundPackets();
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.ANVIL, maintenance, () -> context.runOnClient(client -> {
                    AbstractContainerMenu menu = Objects.requireNonNull(client.player).containerMenu;
                    Objects.requireNonNull(client.gameMode).handleContainerInput(menu.containerId, AnvilMenu.RESULT_SLOT, InputConstants.MOUSE_BUTTON_LEFT, ContainerInput.PICKUP, client.player);
                }));
            });
        }
    }

    @TestCase
    private static void testCrossbow(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, maintenanceVariant(maintenance), () -> {
                ItemStack crossbow = new ItemStack(Items.CROSSBOW);
                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
                give(singleplayerContext, crossbow);
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.CROSSBOW, maintenance, () -> rightClick(context, singleplayerContext));
            });
        }
        // since 1.20.5, each extra projectile from multishot uses the player RNG
        runTest(context, singleplayerContext, "multishot", () -> {
            ItemStack crossbow = enchantedItem(singleplayerContext, Items.CROSSBOW, Enchantments.MULTISHOT, 1);
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW), new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
            give(singleplayerContext, crossbow);
            expectMaintained(context, singleplayerContext, RNGCallType.CROSSBOW, () -> rightClick(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testUnbreaking(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // Unbreaking used the player RNG before 1.21
        for (boolean maintenance : new boolean[] {true, false}) {
            runTest(context, singleplayerContext, "1.20.6," + maintenanceVariant(maintenance), () -> {
                setProtocolVersion(context, MultiVersionCompat.V1_20_6, "1.20.6");
                giveUnbreakingShovel(singleplayerContext);
                setBlock(singleplayerContext, FRONT_POS, Blocks.DIRT);
                expectResetOrMaintained(context, singleplayerContext, RNGCallType.UNBREAKING, maintenance, () -> leftClick(context, singleplayerContext));
            });
        }
        runTest(context, singleplayerContext, "current", () -> {
            disableMaintenance(context);
            giveUnbreakingShovel(singleplayerContext);
            setBlock(singleplayerContext, FRONT_POS, Blocks.DIRT);
            expectUnaffected(context, singleplayerContext, () -> leftClick(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testUnbreakingFishingRod(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // The amount of damage done by reeling in a fishing rod isn't known, so this always resets
        runTest(context, singleplayerContext, () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20_6, "1.20.6");
            give(singleplayerContext, enchantedItem(singleplayerContext, Items.FISHING_ROD, Enchantments.UNBREAKING, 3));
            rightClick(context, singleplayerContext);
            context.waitTicks(5);
            expectReset(context, singleplayerContext, RNGCallType.UNBREAKING, () -> rightClick(context, singleplayerContext));
        });
    }

    // ===== RNG CALLS THAT ALWAYS RESET ===== //

    @TestCase
    private static void testBaneOfArthropods(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // Bane of arthropods used the player RNG before 1.21
        runTest(context, singleplayerContext, "1.20.6", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20_6, "1.20.6");
            disableMaintenance(context);
            Entity silverfish = setUpBaneOfArthropodsAttack(singleplayerContext);
            expectReset(context, singleplayerContext, RNGCallType.BANE_OF_ARTHROPODS, () -> attack(context, singleplayerContext, silverfish));
        });
        runTest(context, singleplayerContext, "current", () -> {
            Entity silverfish = setUpBaneOfArthropodsAttack(singleplayerContext);
            expectUnaffected(context, singleplayerContext, () -> attack(context, singleplayerContext, silverfish));
        });
    }

    @TestCase
    private static void testXp(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, "current", () ->
            expectReset(context, singleplayerContext, RNGCallType.XP, () -> pickUpXpOrb(context, singleplayerContext))
        );
        // XP orbs didn't use the player RNG before 1.17
        runTest(context, singleplayerContext, "1.16", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_16, "1.16");
            expectUnaffected(context, singleplayerContext, () -> pickUpXpOrb(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testMending(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // Test on 1.16 so that the XP orb on its own doesn't reset the cracker
        runTest(context, singleplayerContext, () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_16, "1.16");
            ItemStack boots = enchantedItem(singleplayerContext, Items.IRON_BOOTS, Enchantments.MENDING, 1);
            boots.setDamageValue(50);
            equip(singleplayerContext, EquipmentSlot.FEET, boots);
            expectReset(context, singleplayerContext, RNGCallType.MENDING, () -> pickUpXpOrb(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testGive(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            expectReset(context, singleplayerContext, RNGCallType.GIVE, () -> runClientCommand(context, "give @s minecraft:stone"))
        );
    }

    @TestCase
    private static void testRecreated(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            expectReset(context, singleplayerContext, RNGCallType.RECREATED, () -> {
                teleport(singleplayerContext, Level.NETHER, new Vec3(0.5, 128, 0.5));
                context.waitFor(client -> Objects.requireNonNull(client.level).dimension() == Level.NETHER);
            });
            teleport(singleplayerContext, Level.OVERWORLD, Vec3.atBottomCenterOf(SPAWN_POS));
            context.waitFor(client -> Objects.requireNonNull(client.level).dimension() == Level.OVERWORLD);
        });
    }

    @TestCase
    private static void testEnterWater(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            fill(singleplayerContext, new BlockPos(-2, GRASS_Y - 2, -2), new BlockPos(2, GRASS_Y, 2), Blocks.WATER);
            expectReset(context, singleplayerContext, RNGCallType.ENTER_WATER, () -> {
                teleport(singleplayerContext, Vec3.atBottomCenterOf(SPAWN_POS.above(5)));
                context.waitFor(client -> Objects.requireNonNull(client.player).isInWater());
            });
        });
    }

    @TestCase
    private static void testSwim(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            expectReset(context, singleplayerContext, RNGCallType.SWIM, () -> {
                fill(singleplayerContext, SPAWN_POS.offset(-2, 0, -2), SPAWN_POS.offset(2, 2, 2), Blocks.WATER);
                singleplayerContext.getConnection().waitForClientboundPackets();
                context.waitTicks(5);
            })
        );
    }

    @TestCase
    private static void testShield(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // before 1.20.5, blocking an axe had a random chance of disabling the shield
        runTest(context, singleplayerContext, "1.20", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20, "1.20");
            Mob attacker = setUpShieldBlock(context, singleplayerContext, Items.IRON_AXE);
            try {
                expectReset(context, singleplayerContext, RNGCallType.SHIELD, () -> attackPlayer(singleplayerContext, attacker, true));
            } finally {
                context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            }
        });
        runTest(context, singleplayerContext, "1.20,sword", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20, "1.20");
            Mob attacker = setUpShieldBlock(context, singleplayerContext, Items.IRON_SWORD);
            try {
                expectUnaffected(context, singleplayerContext, () -> attackPlayer(singleplayerContext, attacker, false));
            } finally {
                context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            }
        });
        runTest(context, singleplayerContext, "1.20,not blocking", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20, "1.20");
            Mob attacker = summon(singleplayerContext, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(SPAWN_POS.offset(0, 0, 2)));
            singleplayerContext.getServer().runOnServer(_ -> attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE)));
            singleplayerContext.getConnection().waitForClientboundPackets();
            expectUnaffected(context, singleplayerContext, () -> context.waitTicks(5));
        });
        // since 1.20.5, axes always disable the shield
        runTest(context, singleplayerContext, "current", () -> {
            Mob attacker = setUpShieldBlock(context, singleplayerContext, Items.IRON_AXE);
            try {
                expectUnaffected(context, singleplayerContext, () -> attackPlayer(singleplayerContext, attacker, true));
            } finally {
                context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            }
        });
    }

    @TestCase
    private static void testAmethystChime(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            fill(singleplayerContext, new BlockPos(-2, GRASS_Y, 0), new BlockPos(2, GRASS_Y, 10), Blocks.AMETHYST_BLOCK);
            expectReset(context, singleplayerContext, RNGCallType.AMETHYST_CHIME, () -> context.getInput().holdKeyFor(options -> options.keyUp, 30));
        });
    }

    @TestCase
    private static void testSprint(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            expectReset(context, singleplayerContext, RNGCallType.SPRINT, () -> {
                context.getInput().holdKey(options -> options.keySprint);
                try {
                    context.getInput().holdKeyFor(options -> options.keyUp, 20);
                } finally {
                    context.getInput().releaseKey(options -> options.keySprint);
                }
            })
        );
    }

    @TestCase
    private static void testEntityCramming(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            expectReset(context, singleplayerContext, RNGCallType.ENTITY_CRAMMING, () -> {
                for (int i = 0; i < 24; i++) {
                    summon(singleplayerContext, EntityTypes.PIG, Vec3.atBottomCenterOf(SPAWN_POS));
                }
                singleplayerContext.getConnection().waitForClientboundPackets();
                context.waitTicks(5);
            })
        );
    }

    @TestCase
    private static void testPlayerHurt(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            expectReset(context, singleplayerContext, RNGCallType.PLAYER_HURT, () -> singleplayerContext.getServer().runOnServer(_ -> {
                ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
                ServerLevel level = player.level();
                player.setInvulnerableTime(0);
                player.hurtServer(level, level.damageSources().generic(), 1);
            }))
        );
    }

    @TestCase
    private static void testPotion(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () ->
            expectReset(context, singleplayerContext, RNGCallType.POTION, () -> {
                addEffect(singleplayerContext, MobEffects.SPEED, 0);
                singleplayerContext.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
            })
        );
    }

    @TestCase
    private static void testFallFlying(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, "current", () ->
            expectReset(context, singleplayerContext, RNGCallType.FALL_FLYING, () -> startFallFlying(context, singleplayerContext))
        );
        // Fall flying didn't use the player RNG before 1.21.2
        runTest(context, singleplayerContext, "1.21", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_21, "1.21");
            expectUnaffected(context, singleplayerContext, () -> startFallFlying(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testFrostWalker(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // Frost walker used the player RNG before 1.21
        runTest(context, singleplayerContext, "1.20.6", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_20_6, "1.20.6");
            setUpFrostWalker(singleplayerContext);
            expectReset(context, singleplayerContext, RNGCallType.FROST_WALKER, () -> context.getInput().holdKeyFor(options -> options.keyUp, 10));
        });
        runTest(context, singleplayerContext, "current", () -> {
            setUpFrostWalker(singleplayerContext);
            expectUnaffected(context, singleplayerContext, () -> context.getInput().holdKeyFor(options -> options.keyUp, 10));
        });
    }

    @TestCase
    private static void testSoulSpeed(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            fill(singleplayerContext, new BlockPos(-2, GRASS_Y, -2), new BlockPos(2, GRASS_Y, 2), Blocks.SOUL_SAND);
            singleplayerContext.getConnection().waitForClientboundPackets();
            expectReset(context, singleplayerContext, RNGCallType.SOUL_SPEED, () -> {
                equip(singleplayerContext, EquipmentSlot.FEET, enchantedItem(singleplayerContext, Items.IRON_BOOTS, Enchantments.SOUL_SPEED, 1));
                singleplayerContext.getConnection().waitForClientboundPackets();
                context.waitTicks(5);
            });
        });
    }

    @TestCase
    private static void testShoulderParrot(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, "current", () ->
            expectReset(context, singleplayerContext, RNGCallType.SHOULDER_PARROT, () -> addShoulderParrot(context, singleplayerContext))
        );
        // Shoulder parrots didn't use the player RNG before 1.21.9
        runTest(context, singleplayerContext, "1.21", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_21, "1.21");
            expectUnaffected(context, singleplayerContext, () -> addShoulderParrot(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testItemBreak(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        // Breaking items used the player RNG before 1.14
        runTest(context, singleplayerContext, "1.13.2", () -> {
            setProtocolVersion(context, MultiVersionCompat.V1_13_2, "1.13.2");
            giveAlmostBrokenShovel(singleplayerContext);
            setBlock(singleplayerContext, FRONT_POS, Blocks.DIRT);
            expectReset(context, singleplayerContext, RNGCallType.ITEM_BREAK, () -> leftClick(context, singleplayerContext));
        });
        runTest(context, singleplayerContext, "current", () -> {
            giveAlmostBrokenShovel(singleplayerContext);
            setBlock(singleplayerContext, FRONT_POS, Blocks.DIRT);
            expectUnaffected(context, singleplayerContext, () -> leftClick(context, singleplayerContext));
        });
    }

    @TestCase
    private static void testDesync(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        runTest(context, singleplayerContext, () -> {
            EnchantmentCrackingTests.setUpEnchantingTable(singleplayerContext, 15);
            EnchantmentCrackingTests.primeXpSeed(context, singleplayerContext, KNOWN_SEED);
            // Change the XP seed on the server without the client knowing
            singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().onEnchantmentPerformed(ItemStack.EMPTY, 0));
            clearRngCalls(context);
            EnchantmentCrackingTests.openEnchantingTable(context, singleplayerContext);
            EnchantmentCrackingTests.putItemInTable(context, singleplayerContext, new ItemStack(Items.DIAMOND_SWORD));
            assertTrue(getRngCalls(context).contains(RNGCallType.DESYNC), () -> "Expected a desync, got RNG calls " + getRngCalls(context));
            assertPlayerCrackState(context, PlayerRandCracker.CrackState.UNCRACKED);
        });
    }

    // ===== HELPERS ===== //

    private static String maintenanceVariant(boolean maintenance) {
        return maintenance ? "maintained" : "reset";
    }

    private static void disableMaintenance(ClientGameTestContext context) {
        context.runOnClient(_ -> Configs.playerRNGMaintenance = false);
    }

    private static void expectResetOrMaintained(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, RNGCallType type, boolean maintenance, Runnable action) {
        if (maintenance) {
            expectMaintained(context, singleplayerContext, type, action);
        } else {
            disableMaintenance(context);
            expectReset(context, singleplayerContext, type, action);
        }
    }

    // Expects the action to make the given RNG call and reset the player cracker
    private static List<RNGCallType> expectReset(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, RNGCallType type, Runnable action) {
        List<RNGCallType> rngCalls = performWithKnownSeed(context, singleplayerContext, action);
        assertTrue(rngCalls.contains(type), () -> "Expected RNG call " + type + ", got " + rngCalls);
        assertPlayerCrackState(context, PlayerRandCracker.CrackState.UNCRACKED);
        return rngCalls;
    }

    // Expects the action to make the given RNG call, but for the client to correctly predict it
    private static List<RNGCallType> expectMaintained(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, RNGCallType type, Runnable action) {
        List<RNGCallType> rngCalls = performWithKnownSeed(context, singleplayerContext, action);
        assertTrue(rngCalls.contains(type), () -> "Expected RNG call " + type + ", got " + rngCalls);
        assertPlayerCrackState(context, PlayerRandCracker.CrackState.CRACKED);
        // The server is always on the current version, so the seeds only match if we're not pretending to be on another version
        if (!isProtocolVersionOverridden(context)) {
            assertPlayerSeedInSync(context, singleplayerContext);
        }
        return rngCalls;
    }

    // Expects the action not to affect the player RNG
    private static void expectUnaffected(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, Runnable action) {
        performWithKnownSeed(context, singleplayerContext, action);
        assertPlayerCrackState(context, PlayerRandCracker.CrackState.CRACKED);
        if (!isProtocolVersionOverridden(context)) {
            assertPlayerSeedInSync(context, singleplayerContext);
        }
    }

    private static List<RNGCallType> performWithKnownSeed(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, Runnable action) {
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitTick();
        setKnownPlayerSeed(context, singleplayerContext, KNOWN_SEED);
        clearRngCalls(context);
        action.run();
        singleplayerContext.getConnection().waitForServerboundPackets();
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitTick();
        return getRngCalls(context);
    }

    private static void openCraftingTable(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        setBlock(singleplayerContext, FRONT_POS.below(), Blocks.CRAFTING_TABLE);
        singleplayerContext.getConnection().waitForClientboundPackets();
        useItemOnBlock(context, FRONT_POS.below());
        singleplayerContext.getConnection().waitForServerboundPackets();
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitForScreen(CraftingScreen.class);
    }

    private static void putInCraftingGrid(TestSingleplayerContext singleplayerContext, ItemStack stack) {
        singleplayerContext.getServer().runOnServer(_ -> {
            AbstractContainerMenu menu = singleplayerContext.getConnection().getServerPlayer().containerMenu;
            menu.getSlot(1).set(stack);
            menu.broadcastChanges();
        });
        singleplayerContext.getConnection().waitForClientboundPackets();
    }

    private static void setCarried(TestSingleplayerContext singleplayerContext, ItemStack stack) {
        singleplayerContext.getServer().runOnServer(_ -> {
            AbstractContainerMenu menu = singleplayerContext.getConnection().getServerPlayer().containerMenu;
            menu.setCarried(stack);
            menu.broadcastChanges();
        });
        singleplayerContext.getConnection().waitForClientboundPackets();
    }

    // Closes the container, and checks whether the server dropped the stick. If expectedDrop is null, the server's behavior isn't checked
    private static void closeContainerAndCheckDrop(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, @Nullable Boolean expectedDrop) {
        context.runOnClient(client -> Objects.requireNonNull(client.player).closeContainer());
        singleplayerContext.getConnection().waitForServerboundPackets();
        if (expectedDrop != null) {
            boolean dropped = hasDroppedItem(singleplayerContext, Items.STICK);
            assertEquals(expectedDrop, dropped, () -> "Expected the server " + (expectedDrop ? "to" : "not to") + " drop the stick, the test is broken");
        }
    }

    private static boolean hasDroppedItem(TestSingleplayerContext singleplayerContext, Item item) {
        return singleplayerContext.getServer().computeOnServer(_ ->
            !singleplayerContext.getConnection().getServerLevel().getEntities(EntityTypes.ITEM, itemEntity -> itemEntity.getItem().is(item)).isEmpty()
        );
    }

    private static void equipRespirationHelmet(TestSingleplayerContext singleplayerContext) {
        equip(singleplayerContext, EquipmentSlot.HEAD, enchantedItem(singleplayerContext, Items.IRON_HELMET, Enchantments.RESPIRATION, 3));
        singleplayerContext.getConnection().waitForClientboundPackets();
    }

    private static void submerge(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        fill(singleplayerContext, SPAWN_POS.offset(-2, 0, -2), SPAWN_POS.offset(2, 2, 2), Blocks.WATER);
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    // Has the player block with a shield, with a mob holding the given weapon in front of them
    private static Mob setUpShieldBlock(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, Item weapon) {
        give(singleplayerContext, Items.SHIELD);
        Mob attacker = summon(singleplayerContext, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(SPAWN_POS.offset(0, 0, 2)));
        singleplayerContext.getServer().runOnServer(_ -> attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon)));
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(10);
        assertTrue(context.computeOnClient(client -> Objects.requireNonNull(client.player).isBlocking()), () -> "The player isn't blocking, the test is broken");
        return attacker;
    }

    private static void attackPlayer(TestSingleplayerContext singleplayerContext, Mob attacker, boolean expectShieldDisabled) {
        boolean shieldDisabled = singleplayerContext.getServer().computeOnServer(_ -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            // mobs don't necessarily swing when they attack
            attacker.doHurtTarget(player.level(), player);
            return player.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD));
        });
        assertEquals(expectShieldDisabled, shieldDisabled, () -> "Expected the shield " + (expectShieldDisabled ? "to" : "not to") + " be disabled, the test is broken");
        singleplayerContext.getConnection().waitForClientboundPackets();
    }

    private static void rightClick(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        singleplayerContext.getConnection().waitForServerboundPackets();
    }

    private static void leftClick(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        singleplayerContext.getConnection().waitForServerboundPackets();
    }

    private static void giveUnbreakingShovel(TestSingleplayerContext singleplayerContext) {
        ItemStack shovel = enchantedItem(singleplayerContext, Items.NETHERITE_SHOVEL, Enchantments.UNBREAKING, 3);
        shovel.enchant(singleplayerContext.getServer().computeOnServer(server -> server.registryAccess().getOrThrow(Enchantments.EFFICIENCY)), 5);
        give(singleplayerContext, shovel);
    }

    private static void giveAlmostBrokenShovel(TestSingleplayerContext singleplayerContext) {
        ItemStack shovel = enchantedItem(singleplayerContext, Items.NETHERITE_SHOVEL, Enchantments.EFFICIENCY, 5);
        shovel.setDamageValue(shovel.getMaxDamage() - 1);
        give(singleplayerContext, shovel);
    }

    private static Entity setUpBaneOfArthropodsAttack(TestSingleplayerContext singleplayerContext) {
        give(singleplayerContext, enchantedItem(singleplayerContext, Items.IRON_SWORD, Enchantments.BANE_OF_ARTHROPODS, 5));
        Entity silverfish = summon(singleplayerContext, EntityTypes.SILVERFISH, Vec3.atBottomCenterOf(SPAWN_POS.offset(0, 0, 2)));
        singleplayerContext.getConnection().waitForClientboundPackets();
        return silverfish;
    }

    private static void attack(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, Entity target) {
        context.runOnClient(client -> {
            Entity clientEntity = Objects.requireNonNull(Objects.requireNonNull(client.level).getEntity(target.getId()));
            Objects.requireNonNull(client.gameMode).attack(Objects.requireNonNull(client.player), clientEntity);
        });
        singleplayerContext.getConnection().waitForServerboundPackets();
    }

    private static void pickUpXpOrb(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        singleplayerContext.getServer().runOnServer(_ -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            player.level().addFreshEntity(new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), 1));
        });
        singleplayerContext.getServer().waitFor(_ -> singleplayerContext.getConnection().getServerLevel().getEntities(EntityTypes.EXPERIENCE_ORB, _ -> true).isEmpty(), 100);
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitTick();
    }

    private static void startFallFlying(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        equip(singleplayerContext, EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        teleport(singleplayerContext, Vec3.atBottomCenterOf(SPAWN_POS.above(30)));
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
        singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().startFallFlying());
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    // A stone path to walk along, with water either side
    private static void setUpFrostWalker(TestSingleplayerContext singleplayerContext) {
        fill(singleplayerContext, new BlockPos(-3, GRASS_Y, -3), new BlockPos(3, GRASS_Y, 10), Blocks.WATER);
        fill(singleplayerContext, new BlockPos(0, GRASS_Y, -3), new BlockPos(0, GRASS_Y, 10), Blocks.STONE);
        equip(singleplayerContext, EquipmentSlot.FEET, enchantedItem(singleplayerContext, Items.IRON_BOOTS, Enchantments.FROST_WALKER, 2));
        singleplayerContext.getConnection().waitForClientboundPackets();
    }

    private static void addShoulderParrot(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().setShoulderParrotLeft(Optional.of(Parrot.Variant.RED_BLUE)));
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
    }
}
