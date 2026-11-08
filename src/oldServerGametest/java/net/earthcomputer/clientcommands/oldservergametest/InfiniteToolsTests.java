package net.earthcomputer.clientcommands.oldservergametest;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.earthcomputer.clientcommands.oldservergametest.OldServerClientGameTest.*;

@SuppressWarnings("UnstableApiUsage")
final class InfiniteToolsTests {
    private static final int BLOCKS_TO_MINE = 10;
    private static final int MAX_MINING_TICKS = 100;
    // from 1.21, unbreaking uses the level random instead of the player random, so it can't be manipulated
    private static final int INFINITE_TOOLS_MAX_VERSION = MultiVersionCompat.V1_20_6;

    private record EnchantmentLevel(String id, int legacyId, int level) {
        static EnchantmentLevel unbreaking(int level) {
            return new EnchantmentLevel("unbreaking", 34, level);
        }

        static EnchantmentLevel efficiency(int level) {
            return new EnchantmentLevel("efficiency", 32, level);
        }
    }

    @TestCase(maxVersion = INFINITE_TOOLS_MAX_VERSION)
    private static void testInfiniteTools(ClientGameTestContext context, OldServer server) {
        // unbreaking 1 uses the power of 2 path of nextInt, unbreaking 2 uses the other path
        for (int unbreakingLevel = 1; unbreakingLevel <= 2; unbreakingLevel++) {
            int level = unbreakingLevel;
            runTest(context, server, "unbreaking=" + level, () -> {
                setUpInfiniteTools(context, server, List.of(EnchantmentLevel.unbreaking(level)));

                BlockPos pos = context.computeOnClient(client -> Objects.requireNonNull(client.player).blockPosition()).north(2);
                // The damage is only known about after the server has decided it, so infinite tools manipulates the RNG
                // for the next damage. Therefore, the first block isn't protected.
                int initialDamage = -1;
                for (int i = 0; i <= BLOCKS_TO_MINE; i++) {
                    int blockNumber = i;
                    // this command has the same syntax in every version
                    server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:netherrack");
                    context.waitFor(client -> Objects.requireNonNull(client.level).getBlockState(pos).is(Blocks.NETHERRACK));
                    mineBlock(context, pos);
                    fence(context);

                    assertPlayerCracked(context, "mining block " + blockNumber);
                    int damage = getPickaxeDamage(context);
                    if (i == 0) {
                        initialDamage = damage;
                    } else {
                        int expectedDamage = initialDamage;
                        assertTrue(damage == expectedDamage, () -> "The pickaxe took " + (damage - expectedDamage) + " damage by mining block " + blockNumber);
                    }
                }

                assertPlayerSeedMatchesServer(context, server);
            });
        }
    }

    @TestCase(maxVersion = INFINITE_TOOLS_MAX_VERSION)
    private static void testInfiniteToolsInstamining(ClientGameTestContext context, OldServer server) {
        for (int unbreakingLevel = 1; unbreakingLevel <= 2; unbreakingLevel++) {
            int level = unbreakingLevel;
            runTest(context, server, "unbreaking=" + level, () -> {
                // efficiency 5 is enough to instamine netherrack with a diamond pickaxe in every version
                setUpInfiniteTools(context, server, List.of(EnchantmentLevel.unbreaking(level), EnchantmentLevel.efficiency(5)));

                BlockPos playerPos = context.computeOnClient(client -> Objects.requireNonNull(client.player).blockPosition());

                // The first block isn't protected, see testInfiniteTools
                BlockPos firstPos = playerPos.north(2).above(2);
                server.runCommand("setblock " + firstPos.getX() + " " + firstPos.getY() + " " + firstPos.getZ() + " minecraft:netherrack");
                context.waitFor(client -> Objects.requireNonNull(client.level).getBlockState(firstPos).is(Blocks.NETHERRACK));
                instamineBlock(context, firstPos);
                fence(context);
                int initialDamage = getPickaxeDamage(context);

                // a 5x2 wall of blocks in front of the player
                BlockPos from = playerPos.north(2).west(2);
                BlockPos to = playerPos.north(2).east(2).above();
                List<BlockPos> positions = new ArrayList<>();
                BlockPos.betweenClosed(from, to).forEach(pos -> positions.add(pos.immutable()));
                assertTrue(positions.size() == BLOCKS_TO_MINE, () -> "Expected " + BLOCKS_TO_MINE + " blocks to mine, got " + positions.size());
                // this command has the same syntax in every version
                server.runCommand("fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " minecraft:netherrack");
                context.waitFor(client -> positions.stream().allMatch(pos -> Objects.requireNonNull(client.level).getBlockState(pos).is(Blocks.NETHERRACK)));

                // one block per tick, the fastest that blocks can be mined
                for (BlockPos pos : positions) {
                    instamineBlock(context, pos);
                    context.waitTick();
                }
                fence(context);

                // the server would have put back any blocks that it didn't let us break
                String unbrokenBlocks = context.computeOnClient(client -> positions.stream()
                    .filter(pos -> !Objects.requireNonNull(client.level).getBlockState(pos).isAir())
                    .map(BlockPos::toShortString)
                    .collect(Collectors.joining("], [")));
                assertTrue(unbrokenBlocks.isEmpty(), () -> "The server didn't break the blocks at [" + unbrokenBlocks + "]");
                assertPlayerCracked(context, "instamining");
                int damage = getPickaxeDamage(context);
                assertTrue(damage == initialDamage, () -> "The pickaxe took " + (damage - initialDamage) + " damage by instamining " + BLOCKS_TO_MINE + " blocks");
                assertPlayerSeedMatchesServer(context, server);
            });
        }
    }

    private static void setUpInfiniteTools(ClientGameTestContext context, OldServer server, List<EnchantmentLevel> enchantments) {
        // the pickaxe goes in the first hotbar slot, so that it's held
        givePickaxe(server, enchantments);
        context.waitFor(client -> Objects.requireNonNull(client.player).getInventory().getItem(0).is(Items.DIAMOND_PICKAXE));
        giveItemsToThrow(context, server);

        long serverSeed = getServerPlayerSeed(server);
        context.runOnClient(client -> {
            Objects.requireNonNull(client.player).getInventory().setSelectedSlot(0);
            PlayerRandCracker.setSeed(serverSeed);
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
            Configs.infiniteTools = true;
        });
    }

    private static void givePickaxe(OldServer server, List<EnchantmentLevel> enchantments) {
        ProtocolVersion version = server.version().protocolVersion();
        String player = server.playerName();
        if (version.olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            String nbt = enchantments.stream().map(ench -> "{id:" + ench.legacyId + "s,lvl:" + ench.level + "s}").collect(Collectors.joining(","));
            server.runCommand("give " + player + " minecraft:diamond_pickaxe 1 0 {ench:[" + nbt + "]}");
        } else if (version.olderThan(ProtocolVersion.v1_20_5)) {
            String nbt = enchantments.stream().map(ench -> "{id:\"minecraft:" + ench.id + "\",lvl:" + ench.level + "s}").collect(Collectors.joining(","));
            server.runCommand("give " + player + " minecraft:diamond_pickaxe{Enchantments:[" + nbt + "]}");
        } else {
            String levels = enchantments.stream().map(ench -> "\"minecraft:" + ench.id + "\":" + ench.level).collect(Collectors.joining(","));
            server.runCommand("give " + player + " minecraft:diamond_pickaxe[minecraft:enchantments={levels:{" + levels + "}}]");
        }
    }

    private static void mineBlock(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(client -> Objects.requireNonNull(client.gameMode).startDestroyBlock(pos, Direction.SOUTH));
        for (int tick = 0; context.computeOnClient(client -> !Objects.requireNonNull(client.level).getBlockState(pos).isAir()); tick++) {
            assertTrue(tick < MAX_MINING_TICKS, () -> "Failed to mine the block at " + pos);
            context.runOnClient(client -> Objects.requireNonNull(client.gameMode).continueDestroyBlock(pos, Direction.SOUTH));
            context.waitTick();
        }
    }

    private static void instamineBlock(ClientGameTestContext context, BlockPos pos) {
        boolean broken = context.computeOnClient(client -> {
            Objects.requireNonNull(client.gameMode).startDestroyBlock(pos, Direction.SOUTH);
            return Objects.requireNonNull(client.level).getBlockState(pos).isAir();
        });
        assertTrue(broken, () -> "The block at " + pos + " wasn't instamined");
    }

    private static int getPickaxeDamage(ClientGameTestContext context) {
        return context.computeOnClient(client -> Objects.requireNonNull(client.player).getInventory().getItem(0).getDamageValue());
    }
}
