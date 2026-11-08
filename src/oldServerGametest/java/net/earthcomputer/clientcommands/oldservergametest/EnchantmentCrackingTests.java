package net.earthcomputer.clientcommands.oldservergametest;

import com.sun.jdi.ObjectReference;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedClass;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedField;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.earthcomputer.clientcommands.oldservergametest.OldServerClientGameTest.*;

// The enchanting table is placed 4 blocks south of the player, so that the bookshelves don't intersect the player
@SuppressWarnings("UnstableApiUsage")
final class EnchantmentCrackingTests {
    private static final MappedClass PLAYER = MappedClass.of("net.minecraft.world.entity.player.Player")
        .until(ProtocolVersion.v1_14_3, "net.minecraft.entity.player.PlayerEntity")
        .until(ProtocolVersion.v1_13_2, "net.minecraft.entity.player.EntityPlayer");
    private static final MappedField PLAYER_ENCHANTMENT_SEED = MappedField.of(PLAYER, "enchantmentSeed")
        .until(ProtocolVersion.v1_14_3, "field_175152_f");

    private record EnchantableItem(Item item, ProtocolVersion addedIn) {
    }

    // One of each type of enchantable item that exists in the server version, with a variety of materials
    private static final List<EnchantableItem> ENCHANTABLE_ITEMS = List.of(
        new EnchantableItem(Items.BOOK, ProtocolVersion.v1_8),
        new EnchantableItem(Items.IRON_SWORD, ProtocolVersion.v1_8),
        new EnchantableItem(Items.DIAMOND_PICKAXE, ProtocolVersion.v1_8),
        new EnchantableItem(Items.GOLDEN_AXE, ProtocolVersion.v1_8),
        new EnchantableItem(Items.STONE_SHOVEL, ProtocolVersion.v1_8),
        new EnchantableItem(Items.LEATHER_HELMET, ProtocolVersion.v1_8),
        new EnchantableItem(Items.CHAINMAIL_CHESTPLATE, ProtocolVersion.v1_8),
        new EnchantableItem(Items.IRON_LEGGINGS, ProtocolVersion.v1_8),
        new EnchantableItem(Items.DIAMOND_BOOTS, ProtocolVersion.v1_8),
        new EnchantableItem(Items.BOW, ProtocolVersion.v1_8),
        new EnchantableItem(Items.FISHING_ROD, ProtocolVersion.v1_8),
        new EnchantableItem(Items.TURTLE_HELMET, ProtocolVersion.v1_13),
        new EnchantableItem(Items.TRIDENT, ProtocolVersion.v1_13),
        new EnchantableItem(Items.CROSSBOW, ProtocolVersion.v1_14),
        new EnchantableItem(Items.NETHERITE_HOE, ProtocolVersion.v1_16),
        new EnchantableItem(Items.MACE, ProtocolVersion.v1_21),
        new EnchantableItem(Items.COPPER_SPEAR, ProtocolVersion.v1_21_11)
    );

    private record SeedConfig(int xpSeed, int bookshelves) {
        @Override
        public String toString() {
            return "xpSeed=0x" + Integer.toHexString(xpSeed) + ",bookshelves=" + bookshelves;
        }
    }

    // At least 2 bookshelves, so that the third slot always has an enchantment
    private static final List<SeedConfig> SEED_CONFIGS = List.of(
        new SeedConfig(12345, 15),
        new SeedConfig(-8_675_309, 8),
        new SeedConfig(0xc0ffee, 2)
    );

    @TestCase
    private static void testEnchantingPredictions(ClientGameTestContext context, OldServer server) {
        for (SeedConfig config : SEED_CONFIGS) {
            for (EnchantableItem item : ENCHANTABLE_ITEMS) {
                if (server.version().protocolVersion().olderThan(item.addedIn)) {
                    continue;
                }
                for (int slot = 0; slot < 3; slot++) {
                    int enchantmentSlot = slot;
                    String testCase = BuiltInRegistries.ITEM.getKey(item.item) + "[" + config + ",slot=" + enchantmentSlot + "]";
                    runTest(context, server, testCase, () -> {
                        BlockPos tablePos = context.computeOnClient(client -> Objects.requireNonNull(client.player).blockPosition()).south(4);
                        try {
                            giveLevels(context, server);
                            setUpEnchantingTable(context, server, tablePos, config.bookshelves);
                            enchantAndCheckPrediction(context, server, tablePos, config.xpSeed, item.item, enchantmentSlot);
                        } finally {
                            closeContainer(context);
                        }
                    });
                }
            }
        }
    }

    private static void giveLevels(ClientGameTestContext context, OldServer server) {
        if (server.version().protocolVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            server.runCommand("xp 1000L " + server.playerName());
        } else {
            server.runCommand("experience add " + server.playerName() + " 1000 levels");
        }
        context.waitFor(client -> Objects.requireNonNull(client.player).experienceLevel >= 1000);
    }

    private static void giveLapis(OldServer server, int count) {
        if (server.version().protocolVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            server.runCommand("give " + server.playerName() + " minecraft:dye " + count + " 4");
        } else {
            server.runCommand("give " + server.playerName() + " minecraft:lapis_lazuli " + count);
        }
    }

    private static void setUpEnchantingTable(ClientGameTestContext context, OldServer server, BlockPos tablePos, int bookshelves) {
        closeContainer(context);

        // these commands have the same syntax in every version
        BlockPos from = tablePos.offset(-2, 0, -2);
        BlockPos to = tablePos.offset(2, 1, 2);
        server.runCommand("fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " minecraft:air");
        server.runCommand("setblock " + tablePos.getX() + " " + tablePos.getY() + " " + tablePos.getZ() + " minecraft:enchanting_table");
        List<BlockPos> offsets = EnchantingTableBlock.BOOKSHELF_OFFSETS;
        for (int i = 0; i < bookshelves; i++) {
            BlockPos pos = tablePos.offset(offsets.get(i));
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:bookshelf");
        }

        context.waitFor(client -> {
            var level = Objects.requireNonNull(client.level);
            return level.getBlockState(tablePos).is(Blocks.ENCHANTING_TABLE)
                && offsets.stream().filter(offset -> level.getBlockState(tablePos.offset(offset)).is(Blocks.BOOKSHELF)).count() == bookshelves;
        });
    }

    private static void enchantAndCheckPrediction(ClientGameTestContext context, OldServer server, BlockPos tablePos, int xpSeed, Item item, int slot) {
        // The enchanting menu only reads the player's XP seed when it's opened, so set it before opening
        closeContainer(context);
        setXpSeed(context, server, xpSeed);
        openEnchantingTable(context, tablePos);

        // The server has opened the table, so it has processed everything we sent before, including closing the
        // previous table and returning its items to the inventory
        server.runCommand("clear " + server.playerName());
        context.waitFor(client -> Objects.requireNonNull(client.player).getInventory().isEmpty());
        server.runCommand("give " + server.playerName() + " " + BuiltInRegistries.ITEM.getKey(item));
        giveLapis(server, 3);
        context.waitFor(client -> {
            var inventory = Objects.requireNonNull(client.player).getInventory();
            return inventory.countItem(item) == 1 && inventory.countItem(Items.LAPIS_LAZULI) == 3;
        });
        quickMoveToTable(context, item);
        quickMoveToTable(context, Items.LAPIS_LAZULI);
        // the costs are all sent at the same time, and the third slot always has a cost
        context.waitFor(client -> getEnchantmentMenu(client).costs[2] > 0);

        record Prediction(int cost, String enchantments) {
        }

        Prediction prediction = context.computeOnClient(client -> {
            EnchantmentMenu menu = getEnchantmentMenu(client);
            assertTrue(Configs.enchCrackState == EnchantmentCracker.CrackState.CRACKED, () -> "Client reset the XP seed, it doesn't match the server's: menu seed=0x" + Integer.toHexString(menu.getEnchantmentSeed()));
            List<EnchantmentInstance> enchantments = Objects.requireNonNull(EnchantmentCracker.getEnchantmentsInTable(slot, menu));
            return new Prediction(menu.costs[slot], describeEnchantments(enchantments));
        });

        if (prediction.cost == 0) {
            return;
        }

        context.runOnClient(client -> {
            LocalPlayer player = Objects.requireNonNull(client.player);
            EnchantmentMenu menu = getEnchantmentMenu(client);
            assertTrue(menu.clickMenuButton(player, slot), () -> "Could not click enchantment slot " + slot);
            Objects.requireNonNull(client.gameMode).handleInventoryButtonClick(menu.containerId, slot);
        });
        if (prediction.enchantments.isEmpty()) {
            // the server doesn't change the item, so there's nothing to wait for
            context.waitTicks(20);
        } else {
            context.waitFor(client -> !getEnchantments(getEnchantmentMenu(client).getSlot(0).getItem()).isEmpty());
        }

        String actual = context.computeOnClient(client -> describeEnchantments(getEnchantments(getEnchantmentMenu(client).getSlot(0).getItem())));
        assertTrue(prediction.enchantments.equals(actual), () -> "Enchantment prediction for slot " + slot + " (cost " + prediction.cost + ") was \"" + prediction.enchantments + "\", but the actual enchantments were \"" + actual + "\"");
    }

    private static void setXpSeed(ClientGameTestContext context, OldServer server, int xpSeed) {
        ServerDebugger debugger = server.debugger();
        ObjectReference player = server.serverPlayer();
        debugger.runAtNextTick(() -> debugger.setField(player, PLAYER_ENCHANTMENT_SEED, xpSeed));
        context.runOnClient(_ -> EnchantmentCracker.setKnownXpSeed(xpSeed));
    }

    private static void openEnchantingTable(ClientGameTestContext context, BlockPos tablePos) {
        context.runOnClient(client -> {
            BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false);
            Objects.requireNonNull(client.gameMode).useItemOn(Objects.requireNonNull(client.player), InteractionHand.MAIN_HAND, hitResult);
        });
        context.waitForScreen(EnchantmentScreen.class);
    }

    private static void closeContainer(ClientGameTestContext context) {
        context.runOnClient(client -> {
            LocalPlayer player = Objects.requireNonNull(client.player);
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
        });
        context.waitForScreen(null);
    }

    private static void quickMoveToTable(ClientGameTestContext context, Item item) {
        context.runOnClient(client -> {
            LocalPlayer player = Objects.requireNonNull(client.player);
            EnchantmentMenu menu = getEnchantmentMenu(client);
            Slot slot = menu.slots.stream()
                .filter(s -> s.container == player.getInventory() && s.getItem().is(item))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + item + " in the inventory"));
            Objects.requireNonNull(client.gameMode).handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player);
        });
    }

    private static EnchantmentMenu getEnchantmentMenu(Minecraft client) {
        if (!(Objects.requireNonNull(client.player).containerMenu instanceof EnchantmentMenu menu)) {
            throw new AssertionError("The enchanting table is not open");
        }
        return menu;
    }

    private static List<EnchantmentInstance> getEnchantments(ItemStack stack) {
        List<EnchantmentInstance> enchantments = new ArrayList<>();
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            enchantments.add(new EnchantmentInstance(entry.getKey(), entry.getIntValue()));
        }
        return enchantments;
    }

    private static String describeEnchantments(List<EnchantmentInstance> enchantments) {
        return enchantments.stream()
            .map(ench -> enchantmentId(ench.enchantment()) + " " + ench.level())
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private static String enchantmentId(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().orElseThrow().identifier().getPath();
    }
}
