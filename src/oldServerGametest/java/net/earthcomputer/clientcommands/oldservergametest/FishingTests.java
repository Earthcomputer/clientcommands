package net.earthcomputer.clientcommands.oldservergametest;

import com.mojang.logging.LogUtils;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.ObjectReference;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.event.MoreClientEntityEvents;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedClass;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedField;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedMethod;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;

import static net.earthcomputer.clientcommands.oldservergametest.OldServerClientGameTest.*;

// The player throws the bobber north into a pool of water, where it ends up under some lily pads
@SuppressWarnings("UnstableApiUsage")
public final class FishingTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Fishing manipulation was patched in 1.20. Before 1.9, the loot was generated using the level's random.
    private static final int FISHING_MIN_VERSION = MultiVersionCompat.V1_9;
    private static final int FISHING_MAX_VERSION = MultiVersionCompat.V1_19_4;

    private static final int MAX_ATTEMPTS = 5;
    // The fish is caught on the target tick by chance, depending on network timing, so the target tick success rate of a
    // round of tests is unreliable. If it's too low, the round is rerun, and only fails if no round has a high enough
    // success rate.
    private static final double MIN_TARGET_TICK_SUCCESS_RATE = 0.5;
    private static final int MAX_ROUNDS = 15;
    private static final int MAX_TIMING_CORRECTION = 1000;
    private static final int RETRIEVE_TIMEOUT = 10 * SharedConstants.TICKS_PER_SECOND;
    // the server sends a time update every second, and fishing manipulation uses the last 5
    private static final int SERVER_TICK_RATE_MEASUREMENT_TIME = 6 * SharedConstants.TICKS_PER_SECOND;

    // the pitch the bobber is thrown at, which makes the bobber land just in front of the lily pads, then go under them
    private static final int THROW_PITCH = 24;
    private static final int POOL_NEAR_DISTANCE = 3;
    private static final int POOL_FAR_DISTANCE = 14;
    // While a fish bites, the bobber is pulled down, and open water is checked around wherever the bobber is. The
    // superflat ground isn't deep enough, so the pool is built up, and the player stands on a pillar.
    private static final int POOL_DEPTH = 5;
    private static final int POOL_HEIGHT = 2;
    // Open water needs a 5x5 area of water around the bobber. Without open water, the pool is too narrow for this.
    private static final int OPEN_WATER_POOL_RADIUS = 4;
    private static final int NOT_OPEN_WATER_POOL_RADIUS = 1;
    // The bobber would bob slightly out of the water, which the client can't simulate, so it goes under lily pads,
    // which hold it under the water.
    private static final int LILY_PADS_NEAR_DISTANCE = 5;
    private static final int LILY_PADS_FAR_DISTANCE = 8;
    // before 1.11, the bobber was thrown faster, and sank further, so it went under the lily pads further away
    private static final int LEGACY_LILY_PADS_NEAR_DISTANCE = 8;
    private static final int LEGACY_LILY_PADS_FAR_DISTANCE = 11;
    private static final int LILY_PADS_RADIUS = 1;
    private static final int BOBBER_DISTANCE = 5;
    // Before 1.14.1, some positions were truncated rather than floored, which only differs at negative coordinates, so
    // the fishing is done at positive coordinates, so that the seeds behave the same wherever the player spawned
    private static final int FISHING_ORIGIN_X = 32;
    private static final int FISHING_ORIGIN_Z = 32;
    private static final int BIOME_RADIUS = 8;

    private static final MappedClass FISHING_HOOK = MappedClass.of("net.minecraft.world.entity.projectile.FishingHook")
        .until(ProtocolVersion.v1_15_2, "net.minecraft.world.entity.fishing.FishingHook")
        .until(ProtocolVersion.v1_14_3, "net.minecraft.entity.projectile.FishingBobberEntity")
        .until(ProtocolVersion.v1_13_2, "net.minecraft.entity.projectile.EntityFishHook");
    private static final MappedMethod FISHING_HOOK_RETRIEVE = MappedMethod.of(FISHING_HOOK, "retrieve")
        .until(ProtocolVersion.v1_14_3, "func_146034_e");
    private static final MappedField ENTITY_TICK_COUNT = MappedField.of(ENTITY, "tickCount")
        .until(ProtocolVersion.v1_14_3, "field_70173_aa");
    private static final MappedField FISHING_HOOK_NIBBLE = MappedField.of(FISHING_HOOK, "nibble")
        .until(ProtocolVersion.v1_14_3, "field_146045_ax");

    // Lure makes the fish bite sooner, so that the tests are faster. Luck of the sea makes treasure more likely.
    private static final List<EnchantmentLevel> LURE = List.of(EnchantmentLevel.lure(3));
    private static final List<EnchantmentLevel> LURE_AND_LUCK = List.of(EnchantmentLevel.lure(3), EnchantmentLevel.luckOfTheSea(3));

    // the seed used from the given version, until the version of the next seed
    private record VersionedSeed(int minVersion, long seed) {
    }

    // the seeds are ordered from newest to oldest version, and there's no seed for versions older than the last one
    private record FishingCase(String goal, List<EnchantmentLevel> rodEnchantments, boolean openWater, boolean jungle, List<VersionedSeed> seeds) {
        @Nullable Long getSeed(int protocolVersion) {
            return seeds.stream().filter(seed -> protocolVersion >= seed.minVersion).findFirst().map(VersionedSeed::seed).orElse(null);
        }

        String getName(long seed) {
            return goal + "[seed=0x" + Long.toHexString(seed) + ",openWater=" + openWater + ",jungle=" + jungle + "]";
        }
    }

    // The seeds are chosen so that the goal is likely to be caught on the target tick and the ticks around it, and so that
    // the target tick isn't near the edge of the bite. The simulation differs between versions, so they need different seeds.
    private static final List<FishingCase> FISHING_CASES = List.of(
        // enchanted items, which are all treasure, so need open water
        new FishingCase("minecraft:enchanted_book", LURE_AND_LUCK, true, false, List.of(
            new VersionedSeed(MultiVersionCompat.V1_19, 1982),
            new VersionedSeed(MultiVersionCompat.V1_16, 497),
            new VersionedSeed(MultiVersionCompat.V1_13, 11394),
            new VersionedSeed(MultiVersionCompat.V1_11, 9303),
            new VersionedSeed(MultiVersionCompat.V1_9, 1315)
        )),
        new FishingCase("minecraft:bow", LURE_AND_LUCK, true, false, List.of(
            new VersionedSeed(MultiVersionCompat.V1_19, 12156),
            new VersionedSeed(MultiVersionCompat.V1_16, 579),
            new VersionedSeed(MultiVersionCompat.V1_13, 33341),
            new VersionedSeed(MultiVersionCompat.V1_11, 740),
            new VersionedSeed(MultiVersionCompat.V1_9, 63964)
        )),
        new FishingCase("minecraft:fishing_rod", LURE_AND_LUCK, true, false, List.of(
            new VersionedSeed(MultiVersionCompat.V1_19, 7903),
            new VersionedSeed(MultiVersionCompat.V1_16, 5113),
            new VersionedSeed(MultiVersionCompat.V1_13, 3061),
            new VersionedSeed(MultiVersionCompat.V1_11, 3427),
            new VersionedSeed(MultiVersionCompat.V1_9, 29591)
        )),
        // unenchanted items
        new FishingCase("minecraft:name_tag", LURE_AND_LUCK, true, false, List.of(
            new VersionedSeed(MultiVersionCompat.V1_19, 3116),
            new VersionedSeed(MultiVersionCompat.V1_16, 11730),
            new VersionedSeed(MultiVersionCompat.V1_13, 3820),
            new VersionedSeed(MultiVersionCompat.V1_11, 12021),
            new VersionedSeed(MultiVersionCompat.V1_9, 81978)
        )),
        new FishingCase("minecraft:nautilus_shell", LURE_AND_LUCK, true, true, List.of(
            new VersionedSeed(MultiVersionCompat.V1_19, 8790)
        )),
        new FishingCase("minecraft:pufferfish", LURE, false, false, List.of(
            new VersionedSeed(MultiVersionCompat.V1_19, 149),
            new VersionedSeed(MultiVersionCompat.V1_16, 53),
            new VersionedSeed(MultiVersionCompat.V1_13, 6),
            new VersionedSeed(MultiVersionCompat.V1_11, 6),
            new VersionedSeed(MultiVersionCompat.V1_9, 638)
        )),
        // bamboo can only be caught in a jungle
        new FishingCase("minecraft:bamboo", LURE, false, true, List.of(
            new VersionedSeed(MultiVersionCompat.V1_19, 2338)
        ))
    );

    // the catches seen by the client, which are only accessed on the client thread
    private static final List<ItemEntity> caughtItems = new ArrayList<>();
    private static final List<Integer> caughtXp = new ArrayList<>();
    private static boolean registeredEvents = false;
    // the entity data received by the client is logged during each cast, to diagnose catches that the client didn't see
    private static volatile boolean loggingEntityData = false;

    private static final class TargetTickStats {
        int attempts;
        int hits;
    }

    @TestCase(minVersion = FISHING_MIN_VERSION, maxVersion = FISHING_MAX_VERSION)
    private static void testFishingPredictions(ClientGameTestContext context, OldServer server) {
        context.runOnClient(_ -> registerEvents());
        // Fishing manipulation measures the server's tick rate from the last few time updates, so let it measure the
        // normal tick rate, rather than the server catching up after the player joined or being paused by the debugger
        context.waitTicks(SERVER_TICK_RATE_MEASUREMENT_TIME);

        List<String> successRates = new ArrayList<>();
        boolean succeeded = false;
        for (int round = 1; round <= MAX_ROUNDS && !succeeded; round++) {
            TargetTickStats stats = runFishingRound(context, server, round);
            LOGGER.info("The fish was caught on the target tick {}/{} times in round {}", stats.hits, stats.attempts, round);
            successRates.add(stats.hits + "/" + stats.attempts);
            succeeded = stats.attempts > 0 && stats.hits >= stats.attempts * MIN_TARGET_TICK_SUCCESS_RATE;
        }

        boolean success = succeeded;
        runTest(context, server, "targetTickSuccessRate", () -> assertTrue(success, () -> "The fish was caught on the target tick too few times in every round: " + successRates));
    }

    private static TargetTickStats runFishingRound(ClientGameTestContext context, OldServer server, int round) {
        TargetTickStats stats = new TargetTickStats();
        for (FishingCase fishingCase : FISHING_CASES) {
            Long seed = fishingCase.getSeed(server.version().protocolVersion().getVersion());
            // there's no way to change the biome before /fillbiome was added
            if (seed == null || fishingCase.jungle && server.version().protocolVersion().olderThan(ProtocolVersion.v1_19_3)) {
                continue;
            }
            runTest(context, server, fishingCase.getName(seed) + "(round=" + round + ")", () -> {
                BlockPos origin = moveToFishingOrigin(context, server);
                try {
                    setUpFishing(context, server, origin, fishingCase);
                    for (int attempt = 1; !fishAndCheckPrediction(context, server, seed, stats); attempt++) {
                        int attempts = attempt;
                        assertTrue(attempts < MAX_ATTEMPTS, () -> "The fish was caught outside the predicted ticks " + attempts + " times");
                    }
                } finally {
                    tearDownFishing(server, origin, fishingCase);
                }
            });
        }
        return stats;
    }

    private static void registerEvents() {
        if (registeredEvents) {
            return;
        }
        registeredEvents = true;

        MoreClientEntityEvents.POST_ADD.register(packet -> {
            if (packet.getType() == EntityTypes.FISHING_BOBBER || packet.getType() == EntityTypes.ITEM || packet.getType() == EntityTypes.EXPERIENCE_ORB) {
                LOGGER.info("Client added {} id={}, uuid={}, data={}, pos=({}, {}, {})", packet.getType().builtInRegistryHolder().getRegisteredName(), packet.getId(), packet.getUUID(), packet.getData(), packet.getX(), packet.getY(), packet.getZ());
            }
            // the item stack is set later, so keep the entity
            if (packet.getType() == EntityTypes.ITEM && Objects.requireNonNull(Minecraft.getInstance().level).getEntity(packet.getId()) instanceof ItemEntity item) {
                caughtItems.add(item);
            }
            // ViaVersion puts the value of experience orbs from servers older than 1.21.5 in the packet data
            if (packet.getType() == EntityTypes.EXPERIENCE_ORB && packet.getData() > 0) {
                caughtXp.add(packet.getData());
            }
        });
        MoreClientEntityEvents.POST_SET_INITIAL_XP_ORB_VALUE.register(orb -> caughtXp.add(orb.getValue()));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, _) -> {
            if (entity instanceof FishingHook || entity instanceof ItemEntity) {
                LOGGER.info("Client removed {}, reason={}", describeEntity(entity), entity.getRemovalReason());
            }
        });
    }

    private static BlockPos moveToFishingOrigin(ClientGameTestContext context, OldServer server) {
        // the ground is at the same height everywhere in a superflat world
        int groundY = context.computeOnClient(client -> Objects.requireNonNull(client.player).blockPosition().getY());
        BlockPos origin = new BlockPos(FISHING_ORIGIN_X, groundY, FISHING_ORIGIN_Z);
        server.runCommand("tp " + server.playerName() + " " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() + 0.5));
        // the chunks have been loaded by the server once the client has received the ground
        context.waitFor(client -> {
            var level = Objects.requireNonNull(client.level);
            return Objects.requireNonNull(client.player).blockPosition().equals(origin)
                && !level.getBlockState(origin.offset(-BIOME_RADIUS, -1, BIOME_RADIUS)).isAir()
                && !level.getBlockState(origin.offset(BIOME_RADIUS, -1, -POOL_FAR_DISTANCE - BIOME_RADIUS)).isAir();
        });
        return origin;
    }

    private static void setUpFishing(ClientGameTestContext context, OldServer server, BlockPos origin, FishingCase fishingCase) {
        // rain makes fish bite sooner, so it would change when the fish bites if it started raining
        if (server.version().protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_11)) {
            server.runCommand("gamerule doWeatherCycle false");
        }
        // the weather cycle can't be turned off before 1.11, so clear the weather for long enough instead
        server.runCommand("weather clear 1000000");
        // face north, so that the bobber is thrown the same way each time
        double playerX = origin.getX() + 0.5;
        double playerZ = origin.getZ() + 0.5;
        double playerY = origin.getY() + POOL_HEIGHT;
        fill(server, origin, origin.above(POOL_HEIGHT - 1), "minecraft:stone");
        server.runCommand("tp " + server.playerName() + " " + playerX + " " + playerY + " " + playerZ + " 180 " + THROW_PITCH);

        BlockPos poolFrom = getPoolFrom(origin, fishingCase);
        BlockPos poolTo = getPoolTo(origin, fishingCase);
        fill(server, poolFrom.offset(-1, POOL_DEPTH, -1), poolTo.offset(1, 3, 1), "minecraft:air");
        // the walls of the part of the pool above the ground
        fill(server, poolFrom.offset(-1, POOL_DEPTH - POOL_HEIGHT, -1), poolTo.offset(1, 0, 1), "minecraft:stone");
        fill(server, poolFrom, poolTo, "minecraft:water");
        fill(server, getLilyPadsFrom(server, origin), getLilyPadsTo(server, origin), server.version().protocolVersion().olderThan(ProtocolVersion.v1_13) ? "minecraft:waterlily" : "minecraft:lily_pad");
        BlockPos bobberPos = origin.offset(0, POOL_HEIGHT - 1, -BOBBER_DISTANCE);
        if (fishingCase.jungle) {
            fillBiome(server, origin, "minecraft:jungle");
        }

        giveEnchantedItem(server, "fishing_rod", fishingCase.rodEnchantments);
        context.waitFor(client -> {
            var player = Objects.requireNonNull(client.player);
            var level = Objects.requireNonNull(client.level);
            return player.getInventory().getItem(0).is(Items.FISHING_ROD)
                && player.getX() == playerX && player.getY() == playerY && player.getZ() == playerZ && Mth.wrapDegrees(player.getYRot()) == -180 && player.getXRot() == THROW_PITCH
                && level.getBlockState(poolFrom).is(Blocks.WATER) && level.getBlockState(poolTo).is(Blocks.WATER)
                && level.getBlockState(getLilyPadsFrom(server, origin)).is(Blocks.LILY_PAD) && level.getBlockState(getLilyPadsTo(server, origin)).is(Blocks.LILY_PAD)
                && level.getBiome(bobberPos).is(fishingCase.jungle ? Biomes.JUNGLE : Biomes.PLAINS)
                // clearing the weather makes the rain fade out gradually
                && level.getRainLevel(1) == 0
                // giving the fishing rod creates a fake item entity, which the simulated bobber could collide with
                && StreamSupport.stream(level.entitiesForRendering().spliterator(), false).noneMatch(entity -> entity instanceof ItemEntity);
        });

        context.runOnClient(client -> {
            Objects.requireNonNull(client.player).getInventory().setSelectedSlot(0);
            Configs.fishingManipulation = Configs.FishingManipulation.MANUAL;
        });
        runClientCommand(context, "cfish add-goal " + fishingCase.goal);
        context.waitFor(_ -> FishingCracker.goals.size() == 1);
    }

    private static void tearDownFishing(OldServer server, BlockPos origin, FishingCase fishingCase) {
        // put back the superflat layers, removing the lily pads first so that they don't drop when the water's removed
        BlockPos poolFrom = getPoolFrom(origin, fishingCase);
        BlockPos poolTo = getPoolTo(origin, fishingCase);
        fill(server, getLilyPadsFrom(server, origin), getLilyPadsTo(server, origin), "minecraft:air");
        fill(server, poolFrom.offset(-1, POOL_DEPTH - POOL_HEIGHT, -1), poolTo.offset(1, 0, 1), "minecraft:air");
        fill(server, poolFrom, poolTo.atY(origin.getY() - 2), "minecraft:dirt");
        fill(server, poolFrom.atY(origin.getY() - 1), poolTo.atY(origin.getY() - 1), server.version().protocolVersion().olderThan(ProtocolVersion.v1_13) ? "minecraft:grass" : "minecraft:grass_block");
        fill(server, origin, origin.above(POOL_HEIGHT - 1), "minecraft:air");
        server.runCommand("tp " + server.playerName() + " " + (origin.getX() + 0.5) + " " + origin.getY() + " " + (origin.getZ() + 0.5));
        if (fishingCase.jungle) {
            fillBiome(server, origin, "minecraft:plains");
        }
    }

    private static BlockPos getPoolFrom(BlockPos origin, FishingCase fishingCase) {
        int radius = fishingCase.openWater ? OPEN_WATER_POOL_RADIUS : NOT_OPEN_WATER_POOL_RADIUS;
        return origin.offset(-radius, POOL_HEIGHT - POOL_DEPTH, -POOL_FAR_DISTANCE);
    }

    private static BlockPos getPoolTo(BlockPos origin, FishingCase fishingCase) {
        int radius = fishingCase.openWater ? OPEN_WATER_POOL_RADIUS : NOT_OPEN_WATER_POOL_RADIUS;
        return origin.offset(radius, POOL_HEIGHT - 1, -POOL_NEAR_DISTANCE);
    }

    private static BlockPos getLilyPadsFrom(OldServer server, BlockPos origin) {
        boolean legacy = server.version().protocolVersion().olderThan(ProtocolVersion.v1_11);
        return origin.offset(-LILY_PADS_RADIUS, POOL_HEIGHT, -(legacy ? LEGACY_LILY_PADS_FAR_DISTANCE : LILY_PADS_FAR_DISTANCE));
    }

    private static BlockPos getLilyPadsTo(OldServer server, BlockPos origin) {
        boolean legacy = server.version().protocolVersion().olderThan(ProtocolVersion.v1_11);
        return origin.offset(LILY_PADS_RADIUS, POOL_HEIGHT, -(legacy ? LEGACY_LILY_PADS_NEAR_DISTANCE : LILY_PADS_NEAR_DISTANCE));
    }

    private static void fill(OldServer server, BlockPos from, BlockPos to, String block) {
        server.runCommand("fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + block);
    }

    private static void fillBiome(OldServer server, BlockPos origin, String biome) {
        BlockPos from = origin.offset(-BIOME_RADIUS, POOL_HEIGHT - POOL_DEPTH, -POOL_FAR_DISTANCE - BIOME_RADIUS);
        BlockPos to = origin.offset(BIOME_RADIUS, POOL_HEIGHT + BIOME_RADIUS / 2, BIOME_RADIUS);
        server.runCommand("fillbiome " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + biome);
    }

    /**
     * Throws the bobber with the given seed, waits for the fish to be caught, and checks that the prediction for the
     * tick it was caught on matches what was caught. Returns false if the fish was caught outside the predicted ticks.
     */
    private static boolean fishAndCheckPrediction(ClientGameTestContext context, OldServer server, long seed, TargetTickStats stats) {
        ServerDebugger debugger = server.debugger();

        // the previous catch has been received once the previous bobber has been removed
        context.waitFor(client -> Objects.requireNonNull(client.player).fishing == null);
        context.runOnClient(_ -> {
            caughtItems.clear();
            caughtXp.clear();
            // a run of bad catches can make the timing correction drift so far that the catches are always outside
            // the predicted ticks, where it can't learn from them
            if (Math.abs(FishingCracker.getMagicMillisecondsCorrection()) > MAX_TIMING_CORRECTION) {
                LOGGER.warn("Resetting the timing correction of {}ms", FishingCracker.getMagicMillisecondsCorrection());
                FishingCracker.resetMagicMillisecondsCorrection();
            }
        });

        // The client cracks the bobber's seed from its UUID, which is created from the bobber's random in the Entity
        // constructor, so the seed has to be set as soon as the random has been created.
        CompletableFuture<ObjectReference> hookFuture = debugger.runAfterFieldAssignedInConstructor(FISHING_HOOK, ENTITY_RANDOM, hook -> setEntityRandomSeed(debugger, hook, scrambleSeed(seed)));
        context.runOnClient(client -> Objects.requireNonNull(client.gameMode).useItem(Objects.requireNonNull(client.player), InteractionHand.MAIN_HAND));
        ObjectReference hook = waitForFuture(context, hookFuture, SharedConstants.TICKS_PER_SECOND, "The bobber wasn't created");
        LOGGER.info("The server created the bobber with seed {}, expected UUID {}", seed, Mth.createInsecureUUID(RandomSource.create(seed)));
        loggingEntityData = true;
        try {
            record Retrieval(int tickCount, boolean biting) {
            }
            CompletableFuture<Retrieval> retrieveFuture = debugger.computeOnMethodEntry(FISHING_HOOK_RETRIEVE, hook, () -> new Retrieval(
                ((IntegerValue) debugger.getField(hook, ENTITY_TICK_COUNT)).value(),
                // the server only generates loot while the fish is biting
                ((IntegerValue) debugger.getField(hook, FISHING_HOOK_NIBBLE)).value() > 0
            ));

            record Prediction(UUID bobberUuid, FishingCracker.State state, int targetTick, FishingCracker.Catch[] catches) {
            }

            // the client simulates the bobber as soon as it's added
            context.waitFor(client -> Objects.requireNonNull(client.player).fishing != null);
            // the simulation runs asynchronously, and can take a while if the goal is rare
            context.waitFor(_ -> FishingCracker.state != FishingCracker.State.WAITING_FOR_FIRST_BOBBER_TICK);
            Prediction prediction = context.computeOnClient(client -> new Prediction(
                Objects.requireNonNull(Objects.requireNonNull(client.player).fishing).getUUID(),
                FishingCracker.state,
                FishingCracker.getTotalTicksToWait(),
                FishingCracker.getExpectedCatches()
            ));
            LOGGER.info("Prediction: bobber={}, state={}, targetTick={}, catches={}", prediction.bobberUuid, prediction.state, prediction.targetTick, Arrays.toString(prediction.catches));
            UUID expectedUuid = Mth.createInsecureUUID(RandomSource.create(seed));
            assertTrue(prediction.bobberUuid.equals(expectedUuid), () -> "The bobber's UUID " + prediction.bobberUuid + " doesn't match the seed, expected " + expectedUuid);
            assertTrue(prediction.state == FishingCracker.State.WAITING_FOR_FISH || prediction.state == FishingCracker.State.ASYNC_WAITING_FOR_FISH, () -> "Fishing manipulation didn't find the goal in the first bite, state=" + prediction.state);

            // The bobber's tick count is incremented before each tick, and the bobber is retrieved between ticks, so
            // the loot is generated after tickCount ticks. The target tick counts from 0 for the first tick.
            Retrieval retrieval = waitForFuture(context, retrieveFuture, prediction.targetTick + RETRIEVE_TIMEOUT, "The bobber wasn't retrieved");
            int caughtTick = retrieval.tickCount - 1;
            int tickOffset = caughtTick - prediction.targetTick;
            stats.attempts++;
            if (tickOffset == 0) {
                stats.hits++;
            }

            int index = tickOffset + prediction.catches.length / 2;
            if (!retrieval.biting || index < 0 || index >= prediction.catches.length || prediction.catches[index] == null) {
                LOGGER.warn("The fish was caught {} ticks after the target tick, outside the predicted ticks, retrying", tickOffset);
                return false;
            }
            FishingCracker.Catch expectedCatch = prediction.catches[index];

            try {
                context.waitFor(client -> getCaughtItem(client) != null && !caughtXp.isEmpty());
            } catch (AssertionError e) {
                String seen = context.computeOnClient(client -> {
                    var level = Objects.requireNonNull(client.level);
                    return "items=" + caughtItems.stream().map(item -> describeEntity(item)
                        + " removed=" + item.getRemovalReason()
                        + " inLevel=" + (level.getEntity(item.getId()) == item)
                        + " entityWithId=" + (level.getEntity(item.getId()) instanceof ItemEntity other ? describeEntity(other) : level.getEntity(item.getId()))
                    ).toList() + ", xp=" + caughtXp + ", player=" + Objects.requireNonNull(client.player).position();
                });
                throw new AssertionError("The catch wasn't seen by the client: " + seen, e);
            }
            FishingCracker.Catch actualCatch = context.computeOnClient(client -> new FishingCracker.Catch(Objects.requireNonNull(getCaughtItem(client)), caughtXp.getFirst()));
            LOGGER.info("The fish was caught {} ticks after the target tick, predicted {}, caught {}", tickOffset, expectedCatch, actualCatch);
            assertTrue(actualCatch.equals(expectedCatch), () -> "The fish was caught " + tickOffset + " ticks after the target tick, where " + describeCatch(expectedCatch) + " was predicted, but " + describeCatch(actualCatch) + " was caught");
            return true;
        } finally {
            loggingEntityData = false;
            hook.enableCollection();
        }
    }

    // called on the client thread before the entity data is applied
    public static void onSetEntityData(ClientboundSetEntityDataPacket packet) {
        if (!loggingEntityData) {
            return;
        }
        Entity entity = Objects.requireNonNull(Minecraft.getInstance().level).getEntity(packet.id());
        // the data of an unknown entity is logged too, since it would be dropped if it arrived before the entity was added
        if (entity != null && !(entity instanceof ItemEntity) && !(entity instanceof FishingHook)) {
            return;
        }
        LOGGER.info("Client received entity data for {}: {}", entity == null ? "unknown entity id=" + packet.id() : describeEntity(entity), packet.packedItems().stream().map(value -> value.id() + "=" + value.value()).toList());
    }

    // the caught item spawns at the bobber, which is away from the player, unlike items the player drops
    private static @Nullable ItemStack getCaughtItem(Minecraft client) {
        var player = Objects.requireNonNull(client.player);
        return caughtItems.stream().filter(item -> !item.getItem().isEmpty() && item.position().distanceTo(player.position()) > 2).map(ItemEntity::getItem).findFirst().orElse(null);
    }

    private static String describeEntity(Entity entity) {
        String description = entity.getType().builtInRegistryHolder().getRegisteredName() + " id=" + entity.getId() + " uuid=" + entity.getUUID() + " pos=" + entity.position();
        return entity instanceof ItemEntity item ? description + " item=" + item.getItem() : description;
    }

    private static String describeCatch(FishingCracker.Catch fishingCatch) {
        return fishingCatch + " " + fishingCatch.getLoot().getComponentsPatch();
    }

    // the internal seed of a Random constructed with the given seed
    private static long scrambleSeed(long seed) {
        return (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
    }

    private static <T> T waitForFuture(ClientGameTestContext context, CompletableFuture<T> future, int timeout, String message) {
        try {
            context.waitFor(_ -> future.isDone(), timeout);
        } catch (AssertionError e) {
            throw new AssertionError(message, e);
        } finally {
            // stops the debugger from waiting for it
            future.cancel(false);
        }
        return future.join();
    }
}
