package net.earthcomputer.clientcommands.features;

import com.mojang.logging.LogUtils;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.LootGenerator;
import com.seedfinding.mcfeature.loot.LootPool;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.MCLootTables;
import com.seedfinding.mcfeature.loot.condition.BiomeCondition;
import com.seedfinding.mcfeature.loot.condition.LootCondition;
import com.seedfinding.mcfeature.loot.condition.OpenWaterCondition;
import com.seedfinding.mcfeature.loot.entry.ItemEntry;
import com.seedfinding.mcfeature.loot.entry.LootEntry;
import com.seedfinding.mcfeature.loot.entry.TableEntry;
import com.seedfinding.mcfeature.loot.function.ApplyDamageFunction;
import com.seedfinding.mcfeature.loot.function.LootFunction;
import com.seedfinding.mcfeature.loot.function.SetCountFunction;
import com.seedfinding.mcfeature.loot.roll.ConstantRoll;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.PingCommand;
import net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgument;
import net.earthcomputer.clientcommands.command.arguments.WithStringArgument;
import net.earthcomputer.clientcommands.event.MoreClientEntityEvents;
import net.earthcomputer.clientcommands.event.MoreClientEvents;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.earthcomputer.clientcommands.util.CUtil;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.earthcomputer.clientcommands.util.SeedfindingUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FishingCracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    // enabled by the old server gametests, to diagnose failures on CI
    private static final boolean DEBUG_LOGGING = Boolean.getBoolean("clientcommands.debugFishing");

    // goals
    public static final List<WithStringArgument.Result<ClientItemPredicateArgument.ClientItemPredicate>> goals = new ArrayList<>();
    private static boolean hasWarnedMultipleEnchants = false;

    // loot state
    private static ItemEntity caughtItem;
    private static final Catch[] expectedCatches = new Catch[21]; // must be an odd number

    // bobber state
    private static ItemStack tool;
    private static float throwYRot;
    private static float throwXRot;
    private static Vec3 bobberDestPos;
    private static int bobberNumTicks;

    // rethrow bobber
    public static final int RETHROW_COOLDOWN = 20;

    // timing
    private static volatile long throwTime;
    private static long bobberStartTime;
    private static int totalTicksToWait;
    private static int estimatedTicksElapsed;
    private static final long[] timeSyncTimes = new long[5];
    private static int serverMspt = 50;
    private static volatile int averageTimeToEndOfTick = 0;
    private static final int DEFAULT_MAGIC_MILLISECONDS_CORRECTION = -100;
    private static volatile int magicMillisecondsCorrection = DEFAULT_MAGIC_MILLISECONDS_CORRECTION;
    private static final int MAX_MAGIC_MILLISECONDS_CORRECTION_CHANGE = 200;
    private static final ScheduledExecutorService DELAY_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    // state
    public static volatile State state = State.NOT_MANIPULATING;
    private static final Object STATE_LOCK = new Object();

    // fishing rod uses
    private static int expectedFishingRodUses = 0;

    private static void debug(String message, Object... args) {
        if (DEBUG_LOGGING) {
            LOGGER.info("[cfish] " + message, args);
        }
    }

    // region LOOT SIMULATION

    // seedfinding's fishing loot table is only correct for recent versions
    private static LootTable getFishingLootTable() {
        MCVersion version = SeedfindingUtil.getMCVersion();
        return new LootTable(new LootPool(new ConstantRoll(1),
            new TableEntry(() -> getFishingJunk(version), 10, -2),
            version.isNewerOrEqualTo(MCVersion.v1_16)
                ? new TableEntry(() -> getFishingTreasure(version), 5, 2).when(_ -> new OpenWaterCondition(true))
                : new TableEntry(() -> getFishingTreasure(version), 5, 2),
            new TableEntry(MCLootTables.FISHING_FISH, 85, -1)
        ));
    }

    private static LootTable getFishingJunk(MCVersion version) {
        List<LootEntry> entries = new ArrayList<>();
        // lily pads were treasure before 1.16
        if (version.isNewerOrEqualTo(MCVersion.v1_16)) {
            entries.add(new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.LILY_PAD, 17));
        }
        entries.addAll(List.of(
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.LEATHER_BOOTS, 10).apply(_ -> new ApplyDamageFunction()),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.LEATHER, 10),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.BONE, 10),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.POTION, 10),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.STRING, 5),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.FISHING_ROD, 2).apply(_ -> new ApplyDamageFunction()),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.BOWL, 10),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.STICK, 5),
            // before 1.11, ink sacs were more likely, but only one was caught
            version.isNewerOrEqualTo(MCVersion.v1_11)
                ? new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.INK_SAC, 1).apply(_ -> SetCountFunction.constant(10))
                : new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.INK_SAC, 10),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.TRIPWIRE_HOOK, 10),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.ROTTEN_FLESH, 10)
        ));
        if (version.isNewerOrEqualTo(MCVersion.v1_14)) {
            com.seedfinding.mcbiome.biome.Biome[] jungles = version.isNewerOrEqualTo(MCVersion.v1_18)
                ? new com.seedfinding.mcbiome.biome.Biome[]{Biomes.JUNGLE, Biomes.JUNGLE_EDGE, Biomes.BAMBOO_JUNGLE}
                : new com.seedfinding.mcbiome.biome.Biome[]{Biomes.JUNGLE, Biomes.JUNGLE_HILLS, Biomes.JUNGLE_EDGE, Biomes.BAMBOO_JUNGLE, Biomes.MODIFIED_JUNGLE, Biomes.MODIFIED_JUNGLE_EDGE, Biomes.BAMBOO_JUNGLE_HILLS};
            entries.add(new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.BAMBOO, 10).when(_ -> new BiomeCondition(jungles)));
        }
        return new LootTable(new LootPool(new ConstantRoll(1), entries.toArray(LootEntry[]::new)));
    }

    private static LootTable getFishingTreasure(MCVersion version) {
        List<LootEntry> entries = new ArrayList<>();
        if (version.isOlderThan(MCVersion.v1_16)) {
            entries.add(new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.LILY_PAD));
        }
        entries.addAll(List.of(
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.NAME_TAG),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.SADDLE),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.BOW).apply(_ -> new ApplyDamageFunction(), _ -> new EnchantWithLevelsFunction(30)),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.FISHING_ROD).apply(_ -> new ApplyDamageFunction(), _ -> new EnchantWithLevelsFunction(30)),
            new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.BOOK).apply(_ -> new EnchantWithLevelsFunction(30))
        ));
        if (version.isNewerOrEqualTo(MCVersion.v1_13)) {
            entries.add(new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.NAUTILUS_SHELL));
        }
        return new LootTable(new LootPool(new ConstantRoll(1), entries.toArray(LootEntry[]::new)));
    }

    /**
     * Enchants with treasure enchantments, using our enchantment simulation rather than seedfinding's, which doesn't
     * match some versions.
     */
    private record EnchantWithLevelsFunction(int level) implements LootFunction {
        @Override
        public com.seedfinding.mcfeature.loot.item.ItemStack process(com.seedfinding.mcfeature.loot.item.ItemStack stack, LootContext context) {
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(stack.getItem().getName())));
            // the internal seed is scrambled again by the constructor, which unscrambles it
            LegacyRandomSource random = new LegacyRandomSource(context.getSeed() ^ 0x5deece66dL);
            for (LegacyEnchantment.Instance enchantment : LegacyEnchantment.addRandomEnchantments(random, item, level, true, MultiVersionCompat.INSTANCE.getProtocolVersion())) {
                stack.getItem().addEnchantment(new Pair<>(SeedfindingUtil.toSeedfindingEnchantment(enchantment.ench().enchantmentKey), enchantment.level()));
            }
            context.setSeed(random.seed.get(), false);
            return stack;
        }
    }

    private static boolean isMatchingLoot(ItemStack loot, ClientItemPredicateArgument.ClientItemPredicate goal) {
        return goal.getPossibleItems().contains(loot.getItem());
    }

    private static List<com.seedfinding.mcfeature.loot.item.ItemStack> generateAllMatchingLoot(RegistryAccess registryAccess, LootTable table, @Nullable LootContext context, ClientItemPredicateArgument.ClientItemPredicate goal, Consumer<LootCondition> failedConditions) {
        var result = new ArrayList<com.seedfinding.mcfeature.loot.item.ItemStack>();
        for (LootPool lootPool : table.lootPools) {
            result.addAll(generateAllMatchingLootForPool(registryAccess, lootPool, context, goal, failedConditions));
        }

        if (!result.isEmpty() && !checkConditions(table, context, failedConditions)) {
            return Collections.emptyList();
        }

        return result;
    }

    private static List<com.seedfinding.mcfeature.loot.item.ItemStack> generateAllMatchingLootForPool(RegistryAccess registryAccess, LootPool pool, @Nullable LootContext context, ClientItemPredicateArgument.ClientItemPredicate goal, Consumer<LootCondition> failedConditions) {
        var result = new ArrayList<com.seedfinding.mcfeature.loot.item.ItemStack>();
        for (LootEntry lootEntry : pool.lootEntries) {
            result.addAll(generateAllMatchingLootForEntry(registryAccess, lootEntry, context, goal, failedConditions));
        }

        if (!result.isEmpty() && !checkConditions(pool, context, failedConditions)) {
            return Collections.emptyList();
        }

        return result;
    }

    private static List<com.seedfinding.mcfeature.loot.item.ItemStack> generateAllMatchingLootForEntry(RegistryAccess registryAccess, LootEntry entry, @Nullable LootContext context, ClientItemPredicateArgument.ClientItemPredicate goal, Consumer<LootCondition> failedConditions) {
        List<com.seedfinding.mcfeature.loot.item.ItemStack> result = Collections.emptyList();

        if (entry instanceof ItemEntry itemEntry) {
            ItemStack item = SeedfindingUtil.fromSeedfindingItem(itemEntry.item, registryAccess);
            // enchanting a book turns it into an enchanted book
            if (item.is(Items.BOOK) && itemEntry.lootFunctions != null && Arrays.stream(itemEntry.lootFunctions).anyMatch(function -> function instanceof EnchantWithLevelsFunction)) {
                item = new ItemStack(Items.ENCHANTED_BOOK);
            }
            if (isMatchingLoot(item, goal)) {
                result = Collections.singletonList(new com.seedfinding.mcfeature.loot.item.ItemStack(itemEntry.item));
            }
        } else if (entry instanceof TableEntry tableEntry) {
            result = generateAllMatchingLoot(registryAccess, tableEntry.table.get().apply(SeedfindingUtil.getMCVersion()), context, goal, failedConditions);
        }

        if (!result.isEmpty() && !checkConditions(entry, context, failedConditions)) {
            return Collections.emptyList();
        }

        return result;
    }

    private static boolean checkConditions(LootGenerator generator, @Nullable LootContext context, Consumer<LootCondition> failedConditions) {
        if (context == null || generator.lootConditions == null) {
            return true;
        }

        boolean result = true;
        for (LootCondition condition : generator.lootConditions) {
            if (!condition.is_valid(context)) {
                result = false;
                failedConditions.accept(condition);
            }
        }

        return result;
    }

    // endregion

    // region SEED CRACKING

    /**
     * Returns the internal seed of the Random the instant before it generates the UUID via {@link Mth#createInsecureUUID(RandomSource)}
     */
    private static OptionalLong getSeed(UUID uuid) {
        long uuidLower = uuid.getLeastSignificantBits();

        long hi = 0;
        do {
            long nextLongOutput = hi | (uuidLower & ~(3L << 62));
            long upperBits = nextLongOutput >>> 32;
            long lowerBits = nextLongOutput & ((1L << 32) - 1);

            long a = (24667315L * upperBits + 18218081L * lowerBits + 67552711L) >> 32;
            long b = (-4824621L * upperBits + 7847617L * lowerBits + 7847617L) >> 32;
            long seed = 7847617L * a - 18218081L * b;

            if ((seed >>> 16 << 32) + (int) (((seed * 0x5deece66dL + 0xbL) & ((1L << 48) - 1)) >>> 16) == nextLongOutput) {
                // advance by -3
                seed = (seed * 0x13A1F16F099DL + 0x95756C5D2097L) & ((1L << 48) - 1);
                RandomSource rand = RandomSource.create(seed ^ 0x5deece66dL);
                if (Mth.createInsecureUUID(rand).equals(uuid)) {
                    return OptionalLong.of(seed);
                }
            }

            hi += 1L << 62;
        } while (hi != 0);

        return OptionalLong.empty();
    }

    // endregion

    // region UTILITY

    private static boolean internalInteractFishingBobber() {
        LocalPlayer player = Minecraft.getInstance().player;
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (player != null && gameMode != null) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() == Items.FISHING_ROD) {
                debug("Using the fishing rod, hook={}, expectedFishingRodUses={}", player.fishing, expectedFishingRodUses);
                expectedFishingRodUses++;
                gameMode.useItem(player, InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    public static boolean retractFishingBobber() {
        return internalInteractFishingBobber();
    }

    public static boolean throwFishingBobber() {
        if (internalInteractFishingBobber()) {
            LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            ItemStack stack = player.getMainHandItem();
            handleFishingRodThrow(stack);
            return true;
        }
        return false;
    }

    public static boolean canManipulateFishing() {
        return Configs.fishingManipulation.isEnabled() && !goals.isEmpty();
    }

    private static void handleFishingRodThrow(ItemStack stack) {
        long time = System.nanoTime();
        synchronized (STATE_LOCK) {
            debug("Thrown the fishing rod, state {} -> WAITING_FOR_BOBBER", state);
            throwTime = time;
            estimatedTicksElapsed = 0;
            state = State.WAITING_FOR_BOBBER;
        }
        tool = stack;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            throwYRot = player.getYRot();
            throwXRot = player.getXRot();
            debug("Throw from {}, yRot={}, xRot={}, tool={}", player.position(), throwYRot, throwXRot, stack);
        }
    }

    public static void reset() {
        synchronized (STATE_LOCK) {
            if (DEBUG_LOGGING) {
                debug("Reset from state {}", state, new Throwable("reset() caller"));
            }
            state = State.NOT_MANIPULATING;

            if (canManipulateFishing() && Configs.fishingManipulation == Configs.FishingManipulation.AFK) {
                state = State.WAITING_FOR_RETRHOW;
                TaskManager.addNonConflictingTask("cfishRethrow", new LongTask() {
                    private int counter;
                    @Override
                    public void initialize() {
                        counter = RETHROW_COOLDOWN;
                    }

                    @Override
                    public boolean condition() {
                        synchronized (STATE_LOCK) {
                            return counter > 0 && state == State.WAITING_FOR_RETRHOW;
                        }
                    }

                    @Override
                    public void increment() {
                        counter--;
                    }

                    @Override
                    public void body() {
                        scheduleDelay();
                    }

                    @Override
                    public void onCompleted() {
                        synchronized (STATE_LOCK) {
                            if (throwFishingBobber()) {
                                state = State.WAITING_FOR_BOBBER;
                            } else {
                                state = State.NOT_MANIPULATING;
                            }
                        }
                    }
                });
            }
        }
    }

    // endregion

    // region EVENT HANDLERS

    public static void registerEvents() {
        MoreClientEntityEvents.POST_ADD.register(packet -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && canManipulateFishing()) {
                if (packet.getData() == player.getId() && packet.getType() == EntityTypes.FISHING_BOBBER) {
                    processBobberSpawn(packet.getUUID(), new Vec3(packet.getX(), packet.getY(), packet.getZ()));
                }
                // ViaVersion puts the value of experience orbs from servers older than 1.21.5 in the packet data,
                // rather than syncing it
                if (packet.getType() == EntityTypes.EXPERIENCE_ORB && packet.getData() > 0) {
                    processExperienceOrbSpawn(packet.getX(), packet.getY(), packet.getZ(), packet.getData());
                }
            }
        });
        MoreClientEntityEvents.PRE_ADD_MAYBE_ON_NETWORK_THREAD.register(packet -> {
            // Called on network thread first, FishingCracker.waitingForFishingRod
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }

            if (packet.getType() == EntityTypes.FISHING_BOBBER) {
                debug("Bobber add packet on thread {}: id={}, uuid={}, owner={}, player={}, pos=({}, {}, {}), state={}, canManipulate={}",
                    Thread.currentThread().getName(), packet.getId(), packet.getUUID(), packet.getData(), player.getId(),
                    packet.getX(), packet.getY(), packet.getZ(), state, canManipulateFishing());
            }

            if (!canManipulateFishing() || packet.getData() != player.getId() || packet.getType() != EntityTypes.FISHING_BOBBER) {
                return;
            }

            onFishingBobberEntity();
        });
        MoreClientEntityEvents.POST_SET_INITIAL_XP_ORB_VALUE.register(orb -> {
            if (canManipulateFishing()) {
                processExperienceOrbSpawn(orb.getX(), orb.getY(), orb.getZ(), orb.getValue());
            }
        });
        // the time syncs are always recorded, so that the server's tick rate is known as soon as fishing manipulation is enabled
        MoreClientEvents.TIME_SYNC_ON_NETWORK_THREAD.register(packet -> onTimeSync());
        ClientPlayConnectionEvents.JOIN.register((_, _, _) -> {
            // the timings measured on a previous server would be wrong for this one
            synchronized (timeSyncTimes) {
                Arrays.fill(timeSyncTimes, 0);
                serverMspt = 50;
            }
            averageTimeToEndOfTick = 0;
            resetMagicMillisecondsCorrection();
        });
    }

    private static void processBobberSpawn(UUID fishingBobberUUID, Vec3 pos) {
        // the state only changes to WAITING_FOR_FISH once the target tick is known, otherwise onTimeSync could use the
        // target tick of the previous bobber
        if (state != State.WAITING_FOR_FIRST_BOBBER_TICK) {
            debug("Not simulating bobber {}, state is {}", fishingBobberUUID, state);
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;

        OptionalLong optionalSeed = getSeed(fishingBobberUUID);
        if (optionalSeed.isEmpty()) {
            debug("Failed to crack the seed of bobber {}", fishingBobberUUID);
            Component error = Component.translatable("commands.cfish.error.crackFailed").withStyle(style -> style.withColor(ChatFormatting.RED));
            ClientCommandHelper.addOverlayMessage(error, 100);
            reset();
            return;
        }

        record ErrorEntry(int tick, boolean isBox) {}
        // clear last error
        for (int tick = 0; tick <= bobberNumTicks; tick++) {
            RenderQueue.remove(RenderQueue.Layer.ON_TOP, new ErrorEntry(tick, false));
            RenderQueue.remove(RenderQueue.Layer.ON_TOP, new ErrorEntry(tick, true));
        }
        bobberNumTicks = 0;

        long seed = optionalSeed.getAsLong();
        SimulatedFishingBobber fishingBobber = new SimulatedFishingBobber(seed, tool, pos, throwYRot, throwXRot);
        debug("Simulating bobber {}, seed={}, pos={}, yRot={}, xRot={}, deltaMovement={}, lure={}, luck={}, goals={}, hasLightWork={}",
            fishingBobberUUID, seed, pos, throwYRot, throwXRot, fishingBobber.deltaMovement, fishingBobber.lureLevel, fishingBobber.luckLevel,
            goals.stream().map(WithStringArgument.Result::string).toList(), level.getLightEngine().hasLightWork());

        boolean wasCatchingFish = false;
        int ticksUntilOurItem = -1;
        List<Catch> possibleExpectedCatches = new ArrayList<>();
        int ourExpectedCatchIndex = -1;

        List<Vec3> bobberPositions = new ArrayList<>();
        bobberPositions.add(pos);
        // TODO: get a smarter number of max ticks based on the rarity of the item
        for (int ticks = 0; ticks < 10000; ticks++) {
            fishingBobber.tick();
            bobberPositions.add(fishingBobber.position);
            if (fishingBobber.failedReason != null) {
                debug("Simulation failed on tick {}: {}, position={}, deltaMovement={}",
                    ticks, fishingBobber.failedReason, fishingBobber.position, fishingBobber.deltaMovement);
                bobberNumTicks = ticks;
                for (int i = 0; i < bobberPositions.size(); i++) {
                    int color = i == bobberPositions.size() - 1 ? 0xff0000 : 0x00ff00;
                    RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, new ErrorEntry(i, true), SimulatedFishingBobber.FISHING_BOBBER_DIMENSIONS.makeBoundingBox(bobberPositions.get(i)), color, 100);
                    if (i != 0) {
                        RenderQueue.addLine(RenderQueue.Layer.ON_TOP, new ErrorEntry(i, false), bobberPositions.get(i - 1), bobberPositions.get(i), color, 100);
                    }
                }
                Component error = Component.translatable("commands.cfish.error." + fishingBobber.failedReason).withStyle(style -> style.withColor(ChatFormatting.RED));
                ClientCommandHelper.addOverlayMessage(error, 100);
                reset();
                return;
            }

            if (fishingBobber.canCatchFish()) {
                List<Catch> catches = fishingBobber.generateLoot();

                if (ourExpectedCatchIndex == -1 && goals.stream().anyMatch(goal -> catches.stream().anyMatch(c -> goal.value().test(c.loot)))) {
                    bobberDestPos = fishingBobber.position;
                    ticksUntilOurItem = ticks;
                    ourExpectedCatchIndex = possibleExpectedCatches.size();
                }
                possibleExpectedCatches.add(catches.getFirst());
                wasCatchingFish = true;
            } else if (wasCatchingFish) {
                bobberNumTicks = ticks;
                break;
            }
        }

        if (ticksUntilOurItem == -1) {
            debug("Goal not found after {} ticks, wasCatchingFish={}, position={}, currentState={}, openWater={}, openWaterViolations={}, catches={}, {}",
                wasCatchingFish ? bobberNumTicks : "all", wasCatchingFish, fishingBobber.position, fishingBobber.currentState,
                fishingBobber.openWater, fishingBobber.openWaterViolations, possibleExpectedCatches, fishingBobber.describeFishingConditions());
            // diagnose issue
            Set<LootCondition> failedConditions = new HashSet<>();
            boolean impossible = true;
            LootTable fishingLootTable = getFishingLootTable().apply(SeedfindingUtil.getMCVersion());
            for (var goal : goals) {
                if (goal.value() instanceof ClientItemPredicateArgument.EnchantedItemPredicate predicate) {
                    if (predicate.isEnchantedBook() && predicate.predicate.numEnchantments() >= 2) {
                        if (!hasWarnedMultipleEnchants) {
                            ClientCommandHelper.sendHelp(Component.translatable("commands.cfish.help.tooManyEnchants"));
                            hasWarnedMultipleEnchants = true;
                        }
                    }
                }
                impossible &= generateAllMatchingLoot(level.registryAccess(), fishingLootTable, fishingBobber.getLootContext(), goal.value(), failedConditions::add).isEmpty();
            }

            if (impossible && failedConditions.isEmpty()) {
                Component error = Component.translatable("commands.cfish.error.impossibleLoot").withStyle(style -> style.withColor(ChatFormatting.RED));
                ClientCommandHelper.addOverlayMessage(error, 100);
                reset();
                return;
            }
            if (!failedConditions.isEmpty()) {
                if (failedConditions.stream().anyMatch(it -> it instanceof OpenWaterCondition)) {
                    Component error = Component.translatable("commands.cfish.error.openWater").withStyle(style -> style.withColor(ChatFormatting.RED));
                    ClientCommandHelper.addOverlayMessage(error, 100);
                    if (!fishingBobber.level.getBlockState(BlockPos.containing(fishingBobber.position).above()).is(Blocks.LILY_PAD)) {
                        ClientCommandHelper.sendHelp(Component.translatable("commands.cfish.error.openWater.lilyPad"));
                    }
                    boolean foundFlowingWater = false;
                    for (BlockPos openWaterViolation : fishingBobber.openWaterViolations) {
                        if (!foundFlowingWater
                            && fishingBobber.level.getBlockState(openWaterViolation).is(Blocks.WATER)
                            && !fishingBobber.level.getFluidState(openWaterViolation).isSource()
                        ) {
                            foundFlowingWater = true;
                        }
                        RenderQueue.addCuboid(
                                RenderQueue.Layer.ON_TOP,
                                UUID.randomUUID(),
                                Vec3.atLowerCornerOf(openWaterViolation),
                                Vec3.atLowerCornerOf(openWaterViolation.offset(1, 1, 1)),
                                0xff0000,
                                100
                        );
                    }
                    ClientCommandHelper.sendHelp(Component.translatable("commands.cfish.error.openWater.help"));
                    if (foundFlowingWater) {
                        ClientCommandHelper.sendHelp(Component.translatable("commands.cfish.error.openWater.flowingWater"));
                    }
                    reset();
                    return;
                }
                BiomeCondition biomeCondition = (BiomeCondition) failedConditions.stream().filter(it -> it instanceof BiomeCondition).findFirst().orElse(null);
                if (biomeCondition != null) {
                    Component error = Component.translatable(
                            "commands.cfish.error.biome",
                            Component.translatable("biome.minecraft." + biomeCondition.biomes.getFirst().getName())
                    );
                    ClientCommandHelper.addOverlayMessage(error, 100);
                    reset();
                    return;
                }
            }

            debug("Retracting and rethrowing the bobber");
            if (retractFishingBobber()) {
                if (!throwFishingBobber()) {
                    reset();
                }
            } else {
                reset();
            }
        } else {
            synchronized (STATE_LOCK) {
                if (state != State.WAITING_FOR_FIRST_BOBBER_TICK) {
                    debug("Found the goal, but the state changed to {} during the simulation", state);
                    return;
                }
                debug("Found the goal on tick {}, expected {}, bobber lands at {}, {}",
                    ticksUntilOurItem, possibleExpectedCatches.get(ourExpectedCatchIndex), bobberDestPos, fishingBobber.describeFishingConditions());
                totalTicksToWait = ticksUntilOurItem;
                Arrays.fill(expectedCatches, null);
                for (int i = Math.max(0, ourExpectedCatchIndex - expectedCatches.length / 2),
                         e = Math.min(possibleExpectedCatches.size(), ourExpectedCatchIndex + 1 + expectedCatches.length / 2);
                     i < e; i++) {
                    expectedCatches[i - ourExpectedCatchIndex + expectedCatches.length / 2] = possibleExpectedCatches.get(i);
                }
                state = State.WAITING_FOR_FISH;
            }
        }
    }

    public static void processItemSpawn(ItemEntity item) {
        synchronized (STATE_LOCK) {
            if (state != State.WAITING_FOR_ITEM) {
                return;
            }
            Vec3 pos = item.position();
            if (Math.abs(pos.x - bobberDestPos.x) >= 1 || Math.abs(pos.z - bobberDestPos.z) >= 1 || Math.abs(pos.y - bobberDestPos.y) >= 5) {
                debug("Ignoring item {} at {}, too far from the bobber at {}", item.getId(), pos, bobberDestPos);
                return;
            }
            state = State.WAITING_FOR_XP;
        }

        caughtItem = item;
    }

    private static void processExperienceOrbSpawn(double x, double y, double z, int experienceAmount) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        synchronized (STATE_LOCK) {
            if (state != State.WAITING_FOR_XP) {
                return;
            }
            if (Math.abs(x - player.getX()) >= 1 || Math.abs(z - player.getZ()) >= 1 || Math.abs(y - player.getY()) >= 1) {
                return;
            }
            reset();
        }

        // the item can be synced again before the experience orb spawns, for example it's first synced as stone on
        // servers older than 1.11
        Catch actualCatch = new Catch(caughtItem.getItem(), experienceAmount);
        Catch expectedCatch = expectedCatches[expectedCatches.length / 2];

        List<Integer> indices = IntStream.range(0, expectedCatches.length)
                .filter(i -> actualCatch.equals(expectedCatches[i]))
                .map(i -> i - expectedCatches.length / 2)
                .boxed()
                .collect(Collectors.toList());

        if (actualCatch.equals(expectedCatch)) {
            ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cfish.correctLoot", magicMillisecondsCorrection)
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)), 100);
        } else {
            ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cfish.wrongLoot", magicMillisecondsCorrection, indices)
                    .withStyle(style -> style.withColor(ChatFormatting.RED)), 100);
        }

        if (!indices.isEmpty()) {
            if (CombinedMedianEM.data.size() >= 10) {
                CombinedMedianEM.data.removeFirst();
            }
            ArrayList<Double> sample = new ArrayList<>();
            for (int index : indices) {
                sample.add((double) (index * serverMspt) + magicMillisecondsCorrection);
            }
            CombinedMedianEM.data.add(sample);

            CombinedMedianEM.begintime = magicMillisecondsCorrection - serverMspt / 2 - (expectedCatches.length / 2) * serverMspt;
            CombinedMedianEM.endtime = magicMillisecondsCorrection + serverMspt / 2 + (expectedCatches.length / 2) * serverMspt;
            CombinedMedianEM.width = serverMspt;
            CombinedMedianEM.run();
            // an ambiguous catch can make the estimate jump a long way, so only move part of the way towards it
            int change = (int) Math.round(CombinedMedianEM.mu) - magicMillisecondsCorrection;
            magicMillisecondsCorrection += Mth.clamp(change, -MAX_MAGIC_MILLISECONDS_CORRECTION_CHANGE, MAX_MAGIC_MILLISECONDS_CORRECTION_CHANGE);
        }
    }

    private static void onTimeSync() {
        long time = System.nanoTime();
        synchronized (timeSyncTimes) {
            //noinspection SuspiciousSystemArraycopy
            System.arraycopy(timeSyncTimes, 1, timeSyncTimes, 0, timeSyncTimes.length - 1);
            timeSyncTimes[timeSyncTimes.length - 1] = time;
            if (timeSyncTimes[0] != 0) {
                serverMspt = (int) ((time - timeSyncTimes[0]) / ((timeSyncTimes.length - 1) * 20 * 1000000));
            }
        }

        if (Configs.fishingManipulation.isEnabled() && state == State.WAITING_FOR_FISH) {
            if (estimatedTicksElapsed == 0) {
                estimatedTicksElapsed = (int) Math.ceil((double) (time - bobberStartTime)/(serverMspt * 1000000));
            } else {
                estimatedTicksElapsed += 20;
            }

            int latestReasonableArriveTick = estimatedTicksElapsed + 20 + PingCommand.getLocalPing() / serverMspt;
            if (latestReasonableArriveTick >= totalTicksToWait) {
                state = State.ASYNC_WAITING_FOR_FISH;
                int timeToStartOfTick = serverMspt - averageTimeToEndOfTick;
                int delay = (totalTicksToWait - estimatedTicksElapsed) * serverMspt - magicMillisecondsCorrection - PingCommand.getLocalPing() - timeToStartOfTick + serverMspt / 2;
                long targetTime = (delay) * 1000000L + System.nanoTime();
                debug("Retrieving in {}ms, estimatedTicksElapsed={}, totalTicksToWait={}, serverMspt={}, ping={}, averageTimeToEndOfTick={}, correction={}",
                    delay, estimatedTicksElapsed, totalTicksToWait, serverMspt, PingCommand.getLocalPing(), averageTimeToEndOfTick, magicMillisecondsCorrection);
                DELAY_EXECUTOR.schedule(() -> {
                    if (!Configs.fishingManipulation.isEnabled() || state != State.ASYNC_WAITING_FOR_FISH) {
                        return;
                    }
                    LocalPlayer oldPlayer = Minecraft.getInstance().player;
                    if (oldPlayer != null) {
                        ClientPacketListener packetListener = oldPlayer.connection;
                        while (System.nanoTime() - targetTime < 0) {
                            if (state != State.ASYNC_WAITING_FOR_FISH) {
                                return;
                            }
                        }
                        FishingHook oldFishingHook = oldPlayer.fishing;
                        packetListener.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, oldPlayer.getYRot(), oldPlayer.getXRot()));
                        synchronized (STATE_LOCK) {
                            state = State.WAITING_FOR_ITEM;
                        }
                        Minecraft.getInstance().schedule(() -> {
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player != null) {
                                ItemStack oldStack = player.getMainHandItem();

                                // If the player interaction packet gets handled before the next tick,
                                // then the fish hook would be null and the client would act as if the fishing rod is extending.
                                // Temporarily set to the previous fishing hook to fix this.
                                expectedFishingRodUses++;
                                FishingHook prevFishingHook = player.fishing;
                                player.fishing = oldFishingHook;
                                InteractionResult result = oldStack.use(player.level(), player, InteractionHand.MAIN_HAND);
                                player.fishing = prevFishingHook;

                                if (result instanceof InteractionResult.Success successResult) {
                                    if (oldStack != successResult.heldItemTransformedTo()) {
                                        player.setItemInHand(InteractionHand.MAIN_HAND, successResult.heldItemTransformedTo());
                                    }
                                    if (successResult.swingSource() == InteractionResult.SwingSource.PREDICTED) {
                                        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                                    }
                                }
                                //networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND));
                            }
                        });
                    }
                }, Math.max(0, delay - 100), TimeUnit.MILLISECONDS);
            }
        }
    }

    public static void onThrownFishingRod(ItemStack stack) {
        if (expectedFishingRodUses > 0) {
            debug("Expected throw, expectedFishingRodUses={}", expectedFishingRodUses);
            expectedFishingRodUses--;
            return;
        }

        handleFishingRodThrow(stack);
    }

    public static void onRetractedFishingRod() {
        if (expectedFishingRodUses > 0) {
            debug("Expected retract, expectedFishingRodUses={}", expectedFishingRodUses);
            expectedFishingRodUses--;
            return;
        }

        reset();
    }

    private static void onFishingBobberEntity() {
        long time = System.nanoTime();

        synchronized (STATE_LOCK) {
            // this is called again on the main thread, which is later
            if (state != State.WAITING_FOR_BOBBER) {
                debug("Not waiting for the bobber on thread {}, state is {}", Thread.currentThread().getName(), state);
                return;
            }
            state = State.WAITING_FOR_FIRST_BOBBER_TICK;
            bobberStartTime = time;

            int thrownItemDeltaMillis = (int) ((bobberStartTime - throwTime) / 1000000);
            int localPingMillis = PingCommand.getLocalPing();

            //The 1000 divided by 20 is the number milliseconds per tick there are
            int timeFromEndOfTick = thrownItemDeltaMillis - localPingMillis;

            averageTimeToEndOfTick = (averageTimeToEndOfTick * 3 + timeFromEndOfTick) / 4;
        }
    }

    public static void onBobOutOfWater() {
        // Before 1.14, the server skipped the first movement packet of an entity, but still treated it as sent, so all
        // later movement packets are relative to the wrong position, and the client's bobber is out of place.
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_14) {
            return;
        }
        Component message = Component.translatable("commands.cfish.error.outOfWater").withStyle(style -> style.withColor(ChatFormatting.RED));
        ClientCommandHelper.addOverlayMessage(message, 100);
    }

    @VisibleForTesting
    public static int getMagicMillisecondsCorrection() {
        return magicMillisecondsCorrection;
    }

    @VisibleForTesting
    public static void resetMagicMillisecondsCorrection() {
        magicMillisecondsCorrection = DEFAULT_MAGIC_MILLISECONDS_CORRECTION;
        CombinedMedianEM.data.clear();
    }

    @VisibleForTesting
    public static int getTotalTicksToWait() {
        return totalTicksToWait;
    }

    @VisibleForTesting
    public static Catch[] getExpectedCatches() {
        return expectedCatches.clone();
    }

    // endregion

    // region STRUCTS

    public enum State {
        NOT_MANIPULATING,
        WAITING_FOR_BOBBER,
        WAITING_FOR_FIRST_BOBBER_TICK,
        WAITING_FOR_FISH,
        ASYNC_WAITING_FOR_FISH,
        WAITING_FOR_ITEM,
        WAITING_FOR_XP,
        WAITING_FOR_RETRHOW,
    }

    public static class Catch {
        private final ItemStack loot;
        private final int experience;

        public Catch(ItemStack loot, int experience) {
            this.loot = loot.copy();
            if (this.loot.isDamageableItem()) {
                this.loot.setDamageValue(0);
            }
            // ViaFabricPlus keeps the original NBT of items from old servers, which includes the damage
            this.loot.remove(DataComponents.CUSTOM_DATA);
            this.experience = experience;
        }

        @VisibleForTesting
        public ItemStack getLoot() {
            return loot;
        }

        @Override
        public int hashCode() {
            return 7 * (31 * ItemStack.hashItemAndComponents(loot) + loot.getCount()) + experience;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof Catch that)) {
                return false;
            }
            return ItemStack.matches(loot, that.loot) && experience == that.experience;
        }

        @Override
        public String toString() {
            return loot + " + " + experience + "xp";
        }
    }

    // endregion

    // region NETWORK DELAY ESTIMATION

    /**
     * Taken from: https://gist.github.com/pseudogravity/294f12225c18bf319e4c1923dd664bd5
     *
     * @author MC (PseudoGravity)
     */
    private static class CombinedMedianEM {

        // a list of samples
        // each sample is actually a list of points
        // for each interval, convert it to a point by taking the median value
        // or... break the 50ms intervals into 2 25ms intervals for better accuracy
        static ArrayList<ArrayList<Double>> data = new ArrayList<>();

        // width of the intervals
        static double width = 50;

        // width of time window considered
        // all of the data points for all samples are within this window
        static int begintime = -1000;
        static int endtime = 1000;

        // three parameters and 2 constraints
        static double packetlossrate = 0.2;
        static double maxpacketlossrate = 0.5;
        static double minpacketlossrate = 0.01;
        static double mu = 0.0;
        static double sigma = 500;
        static double maxsigma = 1000;
        static double minsigma = 10;

        public static void run() {
            ArrayList<Double> droprate = new ArrayList<>();

            for (ArrayList<Double> sample : data) {
                droprate.add(sample.size() * width / (endtime - begintime));
            }

            ArrayList<Double> times = new ArrayList<>();
            for (int i = begintime; i <= endtime; i += 10) {
                times.add(i * 1.0);
            }

            double besttime = 0;
            double bestscore = Double.MAX_VALUE;

            for (double time : times) {
                // find score for each option for time
                double score = 0;
                for (int i = 0; i < data.size(); i++) {
                    // for each sample, find the point which adds the least to the score
                    ArrayList<Double> sample = data.get(i);
                    double lambda = droprate.get(i) / width;
                    double bestsubscore = Double.MAX_VALUE;
                    for (Double x : sample) {
                        double absdev = Math.abs(x - time);
                        absdev = (1 - Math.exp(-lambda * absdev)) / lambda; // further curbing outlier effects
                        if (absdev < bestsubscore) {
                            bestsubscore = absdev;
                        }
                    }
                    score += bestsubscore;
                }
                if (score < bestscore) {
                    bestscore = score;
                    besttime = time;
                }
            }

            mu = besttime;
            sigma = bestscore / data.size();
            sigma = Math.max(Math.min(sigma, maxsigma), minsigma);

            for (int repeat = 0; repeat < 1; repeat++) {
                // E step
                // calculate weights (and classifications)
                var masses = new ArrayList<ArrayList<Double>>();
                for (int i = 0; i < data.size(); i++) {

                    ArrayList<Double> sample = data.get(i);

                    // process each sample
                    double sum = 0;
                    for (double x : sample) {
                        sum += mass(x);
                    }
                    double pXandNorm = Math.min(sum, 1) * (1 - packetlossrate); // cap at 1

                    double pXandUnif = droprate.get(i) * packetlossrate;

                    double pNorm = pXandNorm / (pXandNorm + pXandUnif);

                    ArrayList<Double> mass = new ArrayList<>();
                    for (double x : sample) {
                        mass.add(mass(x) / sum * pNorm);
                    }

                    masses.add(mass);
                }

                // M step
                // compute new best estimate for parameters
                double weightedsum = 0;
                double sumofweights = 0;
                for (int i = 0; i < data.size(); i++) {
                    ArrayList<Double> sample = data.get(i);
                    ArrayList<Double> mass = masses.get(i);
                    for (int j = 0; j < sample.size(); j++) {
                        weightedsum += sample.get(j) * mass.get(j);
                        sumofweights += mass.get(j);
                    }
                }
                double muNext = weightedsum / sumofweights;

                double weightedsumofsquaredeviations = 0;
                for (int i = 0; i < data.size(); i++) {
                    ArrayList<Double> sample = data.get(i);
                    ArrayList<Double> mass = masses.get(i);
                    for (int j = 0; j < sample.size(); j++) {
                        weightedsumofsquaredeviations += Math.pow(sample.get(j) - muNext, 2) * mass.get(j);
                    }
                }
                double sigmaNext = Math.sqrt(weightedsumofsquaredeviations / sumofweights);
                sigmaNext = Math.max(Math.min(sigmaNext, maxsigma), minsigma);

                double packetlossrateNext = (data.size() - sumofweights) / data.size();
                packetlossrateNext = Math.max(Math.min(packetlossrateNext, maxpacketlossrate), minpacketlossrate);

                mu = muNext;
                sigma = sigmaNext;
                packetlossrate = packetlossrateNext;
            }
        }

        public static double mass(double x) {
            // should be cdf(x+width/2)-cdf(x-width/2) but is simplified to pdf(x)*width and
            // capped at 1
            // to avoid pesky erf() functions
            double pdf = 1 / (sigma * Math.sqrt(2 * Math.PI)) * Math.exp(-Math.pow((x - mu) / sigma, 2) / 2);
            return Math.min(pdf * width, 1);
        }
    }

    // endregion

    // region FISHING BOBBER SIMULATION

    private static class SimulatedFishingBobber {
        private static final EntityDimensions FISHING_BOBBER_DIMENSIONS = EntityTypes.FISHING_BOBBER.getDimensions();

        private static final float[] LEGACY_SIN_TABLE = Util.make(new float[65536], table -> {
            for (int i = 0; i < table.length; i++) {
                table[i] = (float) Math.sin(i * Math.PI * 2 / 65536);
            }
        });

        private final Level level = Objects.requireNonNull(Minecraft.getInstance().level);

        private final FishingHook fakeEntity;

        // state variables
        private Vec3 position;
        private AABB boundingBox;
        private Vec3 deltaMovement;
        private boolean onGround;
        private FishHookState currentState = FishHookState.FLYING;
        private int nibble;
        private int timeUntilHooked;
        private boolean openWater = true;
        private final Set<BlockPos> openWaterViolations = new LinkedHashSet<>(0);
        private int outOfWaterTime;
        private boolean biting;
        private boolean horizontalCollision;
        private boolean verticalCollision;
        private int timeUntilLured;
        private boolean wasTouchingWater;
        private boolean firstTick;
        private boolean inGroundLegacy;
        private float moveDistLegacy;
        private int nextStepLegacy = 1;

        private float fishAngle;

        // what affected how fast the fish came, for debug logging
        private int catchingFishTicks;
        private int outOfWaterTicks;
        private int rainingTicks;
        private int cantSeeSkyTicks;
        private final Map<BlockPos, Integer> skyLightAbove = new LinkedHashMap<>(0);

        private final RandomSource random;
        private final int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        private final ItemStack tool;
        private final int lureLevel;
        private final int luckLevel;

        // output variables
        @Nullable
        private String failedReason;

        public SimulatedFishingBobber(long seed, ItemStack tool, Vec3 position, float yRot, float xRot) {
            this.random = RandomSource.create(seed ^ 0x5deece66dL);
            // entity UUID
            Mth.createInsecureUUID(random);

            // the velocity in the spawn packet is rounded, so calculate the exact velocity like the server does instead
            this.deltaMovement = calculateInitialDeltaMovement(Mth.wrapDegrees(yRot), xRot);

            this.tool = tool;
            this.lureLevel = CUtil.getEnchantmentLevel(level.registryAccess(), Enchantments.LURE, tool);
            this.luckLevel = CUtil.getEnchantmentLevel(level.registryAccess(), Enchantments.LUCK_OF_THE_SEA, tool);
            this.position = position;
            this.boundingBox = FISHING_BOBBER_DIMENSIONS.makeBoundingBox(position.x, position.y, position.z);

            LocalPlayer player = Objects.requireNonNull(Minecraft.getInstance().player);
            FishingHook realHook = player.fishing;
            this.fakeEntity = new FishingHook(player, level, 0, 0);
            // the constructor replaces the player's hook with the fake one
            player.fishing = realHook;
        }

        private Vec3 calculateInitialDeltaMovement(float yRot, float xRot) {
            if (protocolVersion < MultiVersionCompat.V1_11) {
                float yRadians = yRot / 180.0F * (float) Math.PI;
                float xRadians = xRot / 180.0F * (float) Math.PI;
                double x = -legacySin(yRadians) * legacyCos(xRadians) * 0.4F;
                double z = legacyCos(yRadians) * legacyCos(xRadians) * 0.4F;
                double y = -legacySin(xRadians) * 0.4F;
                float dist = (float) Math.sqrt(x * x + y * y + z * z);
                x /= dist;
                y /= dist;
                z /= dist;
                x += random.nextGaussian() * 0.0075F;
                y += random.nextGaussian() * 0.0075F;
                z += random.nextGaussian() * 0.0075F;
                return new Vec3(x * 1.5F, y * 1.5F, z * 1.5F);
            }

            float yCos = legacyCos(-yRot * 0.017453292F - (float) Math.PI);
            float ySin = legacySin(-yRot * 0.017453292F - (float) Math.PI);
            float xCos = -legacyCos(-xRot * 0.017453292F);
            float xSin = legacySin(-xRot * 0.017453292F);
            Vec3 newMovement = new Vec3(-ySin, Mth.clamp(-(xSin / xCos), -5.0F, 5.0F), -yCos);
            // the length was calculated as a float before 1.14
            double dist = protocolVersion >= MultiVersionCompat.V1_14 ? newMovement.length() : (float) newMovement.length();
            double[] multipliers = new double[3];
            for (int i = 0; i < 3; i++) {
                if (protocolVersion >= MultiVersionCompat.V1_19) {
                    // the double overload, like the server
                    multipliers[i] = 0.6 / dist + random.triangle(0.5, 0.0103365);
                } else {
                    multipliers[i] = 0.6 / dist + 0.5 + random.nextGaussian() * 0.0045;
                }
            }
            return newMovement.multiply(multipliers[0], multipliers[1], multipliers[2]);
        }

        // the sine table used to be indexed using float arithmetic
        private static float legacySin(float radians) {
            return LEGACY_SIN_TABLE[(int) (radians * 10430.378F) & 65535];
        }

        private static float legacyCos(float radians) {
            return LEGACY_SIN_TABLE[(int) (radians * 10430.378F + 16384.0F) & 65535];
        }

        public boolean canCatchFish() {
            return nibble > 0;
        }

        public List<Catch> generateLoot() {
            fakeEntity.absSnapTo(position.x, position.y, position.z);
            fakeEntity.setDeltaMovement(deltaMovement);

            LootContext lootContext = getLootContext();

            List<Catch> catches = new ArrayList<>();
            for (var loot : getFishingLootTable().generate(lootContext)) {
                ItemStack item = SeedfindingUtil.fromSeedfindingItem(loot, level.registryAccess());
                // the potion that can be fished is a water bottle
                if (item.is(Items.POTION)) {
                    item.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
                }
                catches.add(new Catch(item, 1 + lootContext.nextInt(6)));
            }

            return catches;
        }

        private LootContext getLootContext() {
            return new LootContext(((LegacyRandomSource) random).seed.get() ^ 0x5deece66dL, SeedfindingUtil.getMCVersion())
                .withBiome(SeedfindingUtil.toSeedfindingBiome(level, level.getBiome(BlockPos.containing(position))))
                .withOpenWater(openWater)
                .withLuck(luckLevel);
        }

        public void tick() {
            if (protocolVersion < MultiVersionCompat.V1_11) {
                tickLegacy();
                return;
            }

            baseTick();

            if (this.onGround) {
                failedReason = "onGround";
            }

            float liquidHeight = 0.0F;
            BlockPos blockPos = BlockPos.containing(this.position);
            FluidState fluidState = this.level.getFluidState(blockPos);
            if (fluidState.is(FluidTags.WATER)) {
                liquidHeight = getFluidHeight(fluidState, blockPos);
            }

            boolean isInWater = liquidHeight > 0.0F;
            if (this.currentState == FishHookState.FLYING) {
                if (isInWater) {
                    this.deltaMovement = this.deltaMovement.multiply(0.3D, 0.2D, 0.3D);
                    this.currentState = FishHookState.BOBBING;
                    return;
                }

                this.checkCollision();
            } else {
                if (this.currentState == FishHookState.BOBBING) {
                    Vec3 movement = this.deltaMovement;
                    double force = this.position.y + movement.y - (double) blockPos.getY() - (double) liquidHeight;
                    if (Math.abs(force) < 0.01D) {
                        force += Math.signum(force) * 0.1D;
                    }

                    this.deltaMovement = new Vec3(movement.x * 0.9D, movement.y - force * (double) this.random.nextFloat() * 0.2D, movement.z * 0.9D);
                    if (this.nibble <= 0 && this.timeUntilHooked <= 0) {
                        this.openWater = true;
                    } else {
                        this.openWater &= this.outOfWaterTime < 10 & this.calculateOpenWater(blockPos);
                    }

                    if (isInWater) {
                        this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
                        if (this.biting) {
                            // this will just drag the bobber down which we don't care about
                            //this.deltaMovement = this.deltaMovement.add(0.0D, -0.1D * (double) this.syncronizedRandom.nextFloat() * (double) this.syncronizedRandom.nextFloat(), 0.0D);
                        }

                        this.catchingFish(blockPos);
                    } else {
                        this.outOfWaterTime = Math.min(10, this.outOfWaterTime + 1);
                        this.outOfWaterTicks++;
                    }
                }
            }

            if (!fluidState.is(FluidTags.WATER)) {
                this.deltaMovement = this.deltaMovement.add(0.0D, -0.03D, 0.0D);
            }

            this.move(this.deltaMovement);
            if (this.currentState == FishHookState.FLYING && (this.onGround || this.horizontalCollision)) {
                this.deltaMovement = Vec3.ZERO;
            }

            double inertia = 0.92D;
            this.deltaMovement = this.deltaMovement.scale(inertia);

            boundingBox = FISHING_BOBBER_DIMENSIONS.makeBoundingBox(position.x, position.y, position.z);
        }

        private void baseTick() {
            this.updateInWaterStateAndDoFluidPushing();

            this.firstTick = false;
        }

        private void updateInWaterStateAndDoFluidPushing() {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        private void updateInWaterStateAndDoWaterCurrentPushing() {
            if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014D)) {
                if (!this.wasTouchingWater && !this.firstTick) {
                    this.doWaterSplashEffect();
                }

                this.wasTouchingWater = true;
            } else {
                if (this.wasTouchingWater) {
                    failedReason = "outOfWater";
                }
                this.wasTouchingWater = false;
            }
        }

        private void doWaterSplashEffect() {
            float volumeModifier = 0.2F;
            Vec3 movement = deltaMovement;
            float speed = (float) Math.sqrt(movement.x * movement.x * 0.20000000298023224D + movement.y * movement.y + movement.z * movement.z * 0.20000000298023224D) * volumeModifier;
            if (speed > 1.0F) {
                speed = 1.0F;
            }

            if ((double) speed < 0.25D) {
                random.nextFloat();
                random.nextFloat();
                //this.playSound(this.getSwimSplashSound(), speed, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            } else {
                random.nextFloat();
                random.nextFloat();
                //this.playSound(this.getSwimHighSpeedSplashSound(), speed, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            }

            float yt = (float) Mth.floor(this.position.y);

            for (int i = 0; (float) i < 1.0F + FISHING_BOBBER_DIMENSIONS.width() * 20.0F; ++i) {
                double xo = (this.nextParticleRandom() * 2.0D - 1.0D) * (double) FISHING_BOBBER_DIMENSIONS.width();
                double zo = (this.nextParticleRandom() * 2.0D - 1.0D) * (double) FISHING_BOBBER_DIMENSIONS.width();
                nextParticleRandom();
                //this.level.addParticle(ParticleTypes.BUBBLE, this.position.x + xo, (double) (yt + 1.0F), this.position.z + zo, movement.x, movement.y - this.random.nextDouble() * 0.20000000298023224D, movement.z);
            }

            for (int i = 0; (float) i < 1.0F + FISHING_BOBBER_DIMENSIONS.width() * 20.0F; ++i) {
                double xo = (this.nextParticleRandom() * 2.0D - 1.0D) * (double) FISHING_BOBBER_DIMENSIONS.width();
                double zo = (this.nextParticleRandom() * 2.0D - 1.0D) * (double) FISHING_BOBBER_DIMENSIONS.width();
                //this.level.addParticle(ParticleTypes.SPLASH, this.position.x + xo, (double) (yt + 1.0F), this.position.z + zo, movement.x, movement.y, movement.z);
            }
        }

        // in 1.13, the fluid height didn't depend on whether there was fluid above
        private float getFluidHeight(FluidState fluidState, BlockPos blockPos) {
            return protocolVersion >= MultiVersionCompat.V1_13 && protocolVersion < MultiVersionCompat.V1_14 ? fluidState.getOwnHeight() : fluidState.getHeight(this.level, blockPos);
        }

        // Before 1.13, the bounding box was grown by -0.4 vertically, which turns it inside out, and any water in it
        // counted. The current water doesn't push the bobber.
        private boolean isInWaterLegacy() {
            double minY = Math.min(this.boundingBox.minY + 0.4F, this.boundingBox.maxY - 0.4F) + 0.001D;
            double maxY = Math.max(this.boundingBox.minY + 0.4F, this.boundingBox.maxY - 0.4F) - 0.001D;
            AABB box = new AABB(this.boundingBox.minX + 0.001D, minY, this.boundingBox.minZ + 0.001D, this.boundingBox.maxX - 0.001D, maxY, this.boundingBox.maxZ - 0.001D);
            for (BlockPos blockPos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.ceil(box.maxX) - 1, Mth.ceil(box.maxY) - 1, Mth.ceil(box.maxZ) - 1)) {
                if (this.level.getFluidState(blockPos).is(FluidTags.WATER)) {
                    return true;
                }
            }
            return false;
        }

        // the splash particles used floats before 1.16
        private double nextParticleRandom() {
            return protocolVersion >= MultiVersionCompat.V1_16 ? random.nextDouble() : random.nextFloat();
        }

        private boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> tag, double flowScale) {
            if (protocolVersion < MultiVersionCompat.V1_13) {
                return isInWaterLegacy();
            }
            AABB box = this.boundingBox.deflate(0.001D);
            int x0 = Mth.floor(box.minX);
            int x1 = Mth.ceil(box.maxX);
            int y0 = Mth.floor(box.minY);
            int y1 = Mth.ceil(box.maxY);
            int z0 = Mth.floor(box.minZ);
            int z1 = Mth.ceil(box.maxZ);
            if (!this.level.hasChunksAt(x0, y0, z0, x1, y1, z1)) {
                return false;
            } else {
                double height = 0.0D;
                boolean inFluid = false;
                Vec3 current = Vec3.ZERO;
                int currentCount = 0;
                BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

                for (int x = x0; x < x1; ++x) {
                    for (int y = y0; y < y1; ++y) {
                        for (int z = z0; z < z1; ++z) {
                            mutablePos.set(x, y, z);
                            FluidState fluidState = this.level.getFluidState(mutablePos);
                            if (fluidState.is(tag)) {
                                double fluidTop = (float) y + getFluidHeight(fluidState, mutablePos);
                                if (fluidTop >= box.minY) {
                                    inFluid = true;
                                    height = Math.max(fluidTop - box.minY, height);
                                }
                            }
                        }
                    }
                }

                if (current.length() > 0.0D) {
                    if (currentCount > 0) {
                        current = current.scale(1.0D / (double) currentCount);
                    }

                    current = current.normalize();

                    Vec3 movement = this.deltaMovement;
                    current = current.scale(flowScale);
                    if (Math.abs(movement.x) < 0.003D && Math.abs(movement.z) < 0.003D && current.length() < 0.0045000000000000005D) {
                        current = current.normalize().scale(0.0045000000000000005D);
                    }

                    this.deltaMovement = this.deltaMovement.add(current);
                }

                return inFluid;
            }
        }

        private void checkCollision() {
            if (this.getHitResultOnMoveVector().getType() != HitResult.Type.MISS) {
                failedReason = "collision";
            }
        }

        private HitResult getHitResultOnMoveVector() {
            fakeEntity.absSnapTo(position.x, position.y, position.z);
            fakeEntity.setDeltaMovement(deltaMovement);
            return ProjectileUtil.getHitResultOnMoveVector(fakeEntity, fakeEntity::canHitEntity);
        }

        private void move(Vec3 delta) {
            assert level != null;

            Vec3 movement = this.collide(delta);
            if (movement.lengthSqr() > 1.0E-7D) {
                this.boundingBox = this.boundingBox.move(movement);
                this.position = new Vec3((boundingBox.minX + boundingBox.maxX) / 2.0D, boundingBox.minY, (boundingBox.minZ + boundingBox.maxZ) / 2.0D);
            }

            this.horizontalCollision = !Mth.equal(delta.x, movement.x) || !Mth.equal(delta.z, movement.z);
            this.verticalCollision = delta.y != movement.y;
            this.onGround = this.verticalCollision && delta.y < 0.0D;
            //this.checkFallDamage(movement.y, this.onGround, state, pos);
            Vec3 oldDeltaMovement = this.deltaMovement;
            if (delta.x != movement.x) {
                this.deltaMovement = new Vec3(0.0D, oldDeltaMovement.y, oldDeltaMovement.z);
            }

            if (delta.z != movement.z) {
                this.deltaMovement = new Vec3(oldDeltaMovement.x, oldDeltaMovement.y, 0.0D);
            }

            if (delta.y != movement.y) {
                // block.updateEntityMovementAfterFallOn
                deltaMovement = deltaMovement.multiply(1.0D, 0.0D, 1.0D);
            }

            if (this.onGround) {
                // block.stepOn
            }

            //this.checkInsideBlocks(); // no interesting blocks

            float speedFactor = this.getBlockSpeedFactor();
            this.deltaMovement = this.deltaMovement.multiply(speedFactor, 1.0D, speedFactor);
            if (this.level.getBlockStatesIfLoaded(this.boundingBox.deflate(0.001D)).anyMatch((state) -> state.is(BlockTags.FIRE) || state.is(Blocks.LAVA))) {
                failedReason = "fire";
            }
        }

        private Vec3 collide(Vec3 movement) {
            AABB aabb = this.boundingBox;
            fakeEntity.absSnapTo(position.x, position.y, position.z);
            fakeEntity.setDeltaMovement(deltaMovement);
            assert level != null;

            VoxelShape worldBorderShape = this.level.getWorldBorder().getCollisionShape();
            List<VoxelShape> colliders = new ArrayList<>();
            if (!Shapes.joinIsNotEmpty(worldBorderShape, Shapes.create(aabb.deflate(1.0E-7D)), BooleanOp.AND)) {
                colliders.add(worldBorderShape);
            }
            colliders.addAll(this.level.getEntityCollisions(fakeEntity, aabb.expandTowards(movement)));

            return movement.lengthSqr() == 0.0D ? movement : Entity.collideBoundingBox(fakeEntity, movement, aabb, this.level, colliders);
        }

        private float getBlockSpeedFactor() {
            assert level != null;
            Block block = this.level.getBlockState(BlockPos.containing(position)).getBlock();
            float speedFactorHere = block.getSpeedFactor();
            if (block != Blocks.WATER && block != Blocks.BUBBLE_COLUMN) {
                return (double) speedFactorHere == 1.0D ? this.level
                    .getBlockState(BlockPos.containing(this.position.x, this.boundingBox.minY - 0.5000001D, this.position.z)).getBlock().getSpeedFactor() : speedFactorHere;
            } else {
                return speedFactorHere;
            }
        }

        private boolean calculateOpenWater(BlockPos blockPos) {
            OpenWaterType previousLayer = OpenWaterType.INVALID;

            boolean valid = true;

            for (int y = -1; y <= 2; ++y) {
                OpenWaterType layer = this.getOpenWaterTypeForArea(blockPos.offset(-2, y, -2), blockPos.offset(2, y, 2));
                switch (layer) {
                    case INVALID:
                        valid = false;
                        break;
                    case ABOVE_WATER:
                        if (previousLayer == OpenWaterType.INVALID) {
                            valid = false;
                        }
                        break;
                    case INSIDE_WATER:
                        if (previousLayer == OpenWaterType.ABOVE_WATER) {
                            valid = false;
                        }
                        break;
                }

                if (!valid) {
                    List<BlockPos> aboveWaterBlocks = new ArrayList<>(0);
                    boolean foundWater = false;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos pos = blockPos.offset(dx, y, dz);
                            OpenWaterType type = getOpenWaterTypeForBlock(pos);
                            if (type == OpenWaterType.INVALID) {
                                openWaterViolations.add(pos);
                            } else if (type == OpenWaterType.ABOVE_WATER) {
                                aboveWaterBlocks.add(pos);
                            } else if (type == OpenWaterType.INSIDE_WATER) {
                                foundWater = true;
                            }
                        }
                    }
                    if (foundWater) {
                        openWaterViolations.addAll(aboveWaterBlocks);
                    }
                }

                previousLayer = layer;
            }

            return valid;
        }

        private OpenWaterType getOpenWaterTypeForArea(BlockPos from, BlockPos to) {
            return BlockPos.betweenClosedStream(from, to).map(this::getOpenWaterTypeForBlock).reduce((a, b) -> a == b ? a : OpenWaterType.INVALID).orElse(OpenWaterType.INVALID);
        }

        private OpenWaterType getOpenWaterTypeForBlock(BlockPos pos) {
            assert level != null;
            BlockState state = this.level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.LILY_PAD)) {
                FluidState fluidState = state.getFluidState();
                return fluidState.is(FluidTags.WATER) && fluidState.isSource() && state.getCollisionShape(this.level, pos).isEmpty() ? OpenWaterType.INSIDE_WATER : OpenWaterType.INVALID;
            } else {
                return OpenWaterType.ABOVE_WATER;
            }
        }

        private void recordFishingConditions(BlockPos above) {
            if (!DEBUG_LOGGING) {
                return;
            }
            catchingFishTicks++;
            if (this.level.isRainingAt(above)) {
                rainingTicks++;
            }
            if (!this.level.canSeeSky(above)) {
                cantSeeSkyTicks++;
            }
            skyLightAbove.putIfAbsent(above.immutable(), this.level.getBrightness(LightLayer.SKY, above));
        }

        private String describeFishingConditions() {
            return "catchingFishTicks=" + catchingFishTicks + ", outOfWaterTicks=" + outOfWaterTicks + ", rainingTicks=" + rainingTicks
                + ", cantSeeSkyTicks=" + cantSeeSkyTicks + ", skyLightAbove=" + skyLightAbove;
        }

        private void catchingFish(BlockPos blockPos) {
            recordFishingConditions(blockPos.above());
            assert level != null;
            int fishingSpeed = 1;
            BlockPos above = blockPos.above();
            if (this.random.nextFloat() < 0.25F && this.level.isRainingAt(above)) {
                ++fishingSpeed;
            }

            if (this.random.nextFloat() < 0.5F && !this.level.canSeeSky(above)) {
                --fishingSpeed;
            }

            if (this.nibble > 0) {
                --this.nibble;
                if (this.nibble <= 0) {
                    this.timeUntilLured = 0;
                    this.timeUntilHooked = 0;
                    this.biting = false;
                } else if (protocolVersion < MultiVersionCompat.V1_16) {
                    // the bobber was pulled down using the main random before 1.16
                    this.deltaMovement = this.deltaMovement.add(0.0D, -0.2D * this.random.nextFloat() * this.random.nextFloat(), 0.0D);
                }
            } else if (this.timeUntilHooked > 0) {
                this.timeUntilHooked -= fishingSpeed;
                if (this.timeUntilHooked > 0) {
                    if (protocolVersion >= MultiVersionCompat.V1_19) {
                        this.fishAngle += (float) random.triangle(0, 9.188);
                    } else {
                        this.fishAngle += (float) (random.nextGaussian() * 4.0);
                    }
                    float angle = this.fishAngle * 0.017453292F;
                    float angleSin = Mth.sin(angle);
                    float angleCos = Mth.cos(angle);
                    double fishX = this.position.x + (double) (angleSin * (float) this.timeUntilHooked * 0.1F);
                    double fishY = (float) Mth.floor(this.position.y) + 1.0F;
                    double fishZ = this.position.z + (double) (angleCos * (float) this.timeUntilHooked * 0.1F);
                    BlockState splashBlockState = level.getBlockState(BlockPos.containing(fishX, fishY - 1.0D, fishZ));
                    if (splashBlockState.is(Blocks.WATER)) {
                        if (this.random.nextFloat() < 0.15F) {
                            //serverLevel.sendParticles(ParticleTypes.BUBBLE, fishX, fishY - 0.10000000149011612D, fishZ, 1, angleSin, 0.1D, angleCos, 0.0D);
                        }

                        float particleXMovement = angleSin * 0.04F;
                        float particleZMovement = angleCos * 0.04F;
                        //serverLevel.sendParticles(ParticleTypes.FISHING, fishX, fishY, fishZ, 0, particleZMovement, 0.01D, -particleXMovement, 1.0D);
                        //serverLevel.sendParticles(ParticleTypes.FISHING, fishX, fishY, fishZ, 0, -particleZMovement, 0.01D, particleXMovement, 1.0D);
                    }
                } else {
                    if (protocolVersion < MultiVersionCompat.V1_16) {
                        // the bobber was pulled down using the main random before 1.16
                        this.deltaMovement = new Vec3(this.deltaMovement.x, -0.4F * Mth.nextFloat(this.random, 0.6F, 1.0F), this.deltaMovement.z);
                    }
                    random.nextFloat();
                    random.nextFloat();
                    //this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                    double y = this.position.y + 0.5D;
                    //serverLevel.sendParticles(ParticleTypes.BUBBLE, this.getX(), y, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0D, this.getBbWidth(), 0.20000000298023224D);
                    //serverLevel.sendParticles(ParticleTypes.FISHING, this.getX(), y, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0D, this.getBbWidth(), 0.20000000298023224D);
                    this.nibble = Mth.nextInt(this.random, 20, 40);
                    this.biting = true;
                }
            } else if (this.timeUntilLured > 0) {
                this.timeUntilLured -= fishingSpeed;
                float teaseChance = 0.15F;
                if (this.timeUntilLured < 20) {
                    teaseChance = (float) ((double) teaseChance + (double) (20 - this.timeUntilLured) * 0.05D);
                } else if (this.timeUntilLured < 40) {
                    teaseChance = (float) ((double) teaseChance + (double) (40 - this.timeUntilLured) * 0.02D);
                } else if (this.timeUntilLured < 60) {
                    teaseChance = (float) ((double) teaseChance + (double) (60 - this.timeUntilLured) * 0.01D);
                }

                if (this.random.nextFloat() < teaseChance) {
                    float angle = Mth.nextFloat(this.random, 0.0F, 360.0F) * 0.017453292F;
                    float dist = Mth.nextFloat(this.random, 25.0F, 60.0F);
                    double fishX = this.position.x + (double) (Mth.sin(angle) * dist * 0.1F);
                    double fishY = (float) Mth.floor(this.position.y) + 1.0F;
                    double fishZ = this.position.z + (double) (Mth.cos(angle) * dist * 0.1F);
                    // the position was truncated rather than floored before 1.14.1
                    BlockPos splashPos = protocolVersion >= MultiVersionCompat.V1_14_1 ? BlockPos.containing(fishX, fishY - 1.0D, fishZ) : new BlockPos((int) fishX, (int) fishY - 1, (int) fishZ);
                    BlockState splashBlockState = level.getBlockState(splashPos);
                    if (splashBlockState.is(Blocks.WATER)) {
                        random.nextInt(2);
                        //serverLevel.sendParticles(ParticleTypes.SPLASH, fishX, fishY, fishZ, 2 + this.random.nextInt(2), 0.10000000149011612D, 0.0D, 0.10000000149011612D, 0.0D);
                    }
                }

                if (this.timeUntilLured <= 0) {
                    this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                    this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
                }
            } else {
                this.timeUntilLured = Mth.nextInt(this.random, 100, 600);
                this.timeUntilLured -= this.lureLevel * 20 * 5;
            }
        }

        // the fishing bobber before 1.11, which floated depending on how much of it was in water, and had no states
        private void tickLegacy() {
            baseTick();

            // The bobber gets stuck in any block it hits, but the block it's stuck in isn't stored, so it comes unstuck
            // on the next tick, slowing down randomly
            if (this.inGroundLegacy) {
                this.inGroundLegacy = false;
                double xa = this.deltaMovement.x * (this.random.nextFloat() * 0.2F);
                double ya = this.deltaMovement.y * (this.random.nextFloat() * 0.2F);
                double za = this.deltaMovement.z * (this.random.nextFloat() * 0.2F);
                this.deltaMovement = new Vec3(xa, ya, za);
            }

            HitResult hitResult = this.getHitResultOnMoveVector();
            if (hitResult.getType() == HitResult.Type.ENTITY) {
                failedReason = "collision";
                return;
            }
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                this.inGroundLegacy = true;
                return;
            }

            Vec3 oldPosition = this.position;
            this.move(this.deltaMovement);
            this.updateStepsLegacy(oldPosition);

            double submerged = 0;
            for (int i = 0; i < 5; i++) {
                double minY = this.boundingBox.minY + (this.boundingBox.maxY - this.boundingBox.minY) * i / 5;
                double maxY = this.boundingBox.minY + (this.boundingBox.maxY - this.boundingBox.minY) * (i + 1) / 5;
                if (isInWaterLegacy(new AABB(this.boundingBox.minX, minY, this.boundingBox.minZ, this.boundingBox.maxX, maxY, this.boundingBox.maxZ))) {
                    submerged += 1.0 / 5;
                }
            }

            if (submerged > 0) {
                this.catchingFishLegacy(BlockPos.containing(this.position));
            }

            float inertia = this.onGround || this.horizontalCollision ? 0.5F : 0.92F;
            double ya = this.deltaMovement.y + 0.04F * (submerged * 2 - 1);
            if (submerged > 0) {
                inertia = (float) (inertia * 0.9);
                ya *= 0.8;
            }
            this.deltaMovement = new Vec3(this.deltaMovement.x * inertia, ya * inertia, this.deltaMovement.z * inertia);

            this.boundingBox = FISHING_BOBBER_DIMENSIONS.makeBoundingBox(this.position.x, this.position.y, this.position.z);
        }

        // Before 1.11, the bobber made step sounds like a walking entity, and swimming sounds in water, which use its random
        private void updateStepsLegacy(Vec3 oldPosition) {
            double dx = this.position.x - oldPosition.x;
            double dz = this.position.z - oldPosition.z;
            this.moveDistLegacy = (float) (this.moveDistLegacy + (float) Math.sqrt(dx * dx + dz * dz) * 0.6);
            BlockPos posBelow = BlockPos.containing(this.position.x, this.position.y - 0.2F, this.position.z);
            BlockState stateBelow = this.level.getBlockState(posBelow);
            if (stateBelow.isAir()) {
                BlockState stateBelow2 = this.level.getBlockState(posBelow.below());
                if (stateBelow2.is(BlockTags.FENCES) || stateBelow2.is(BlockTags.WALLS) || stateBelow2.is(BlockTags.FENCE_GATES)) {
                    stateBelow = stateBelow2;
                }
            }
            if (this.moveDistLegacy > this.nextStepLegacy && !stateBelow.isAir()) {
                this.nextStepLegacy = (int) this.moveDistLegacy + 1;
                if (this.wasTouchingWater) {
                    // swimming sound pitch
                    this.random.nextFloat();
                    this.random.nextFloat();
                }
            }
        }

        // whether any water in the box is above its bottom, where water source blocks count as full blocks
        private boolean isInWaterLegacy(AABB box) {
            for (BlockPos blockPos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.ceil(box.maxX) - 1, Mth.ceil(box.maxY) - 1, Mth.ceil(box.maxZ) - 1)) {
                FluidState fluidState = this.level.getFluidState(blockPos);
                if (fluidState.is(FluidTags.WATER)) {
                    double fluidTop = blockPos.getY() + 1 - (fluidState.isSource() ? 0 : (8 - fluidState.getAmount()) / 8.0);
                    if (fluidTop >= box.minY) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void catchingFishLegacy(BlockPos blockPos) {
            recordFishingConditions(blockPos.above());
            int fishingSpeed = 1;
            BlockPos above = blockPos.above();
            if (this.random.nextFloat() < 0.25F && this.level.isRainingAt(above)) {
                fishingSpeed = 2;
            }

            if (this.random.nextFloat() < 0.5F && !this.level.canSeeSky(above)) {
                --fishingSpeed;
            }

            if (this.nibble > 0) {
                --this.nibble;
                if (this.nibble <= 0) {
                    this.timeUntilLured = 0;
                    this.timeUntilHooked = 0;
                }
            } else if (this.timeUntilHooked > 0) {
                this.timeUntilHooked -= fishingSpeed;
                if (this.timeUntilHooked <= 0) {
                    this.deltaMovement = this.deltaMovement.add(0.0D, -0.2F, 0.0D);
                    random.nextFloat();
                    random.nextFloat();
                    //this.playSound(SoundEvents.ENTITY_BOBBER_SPLASH, 0.25F, 1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
                    this.nibble = Mth.nextInt(this.random, 10, 30);
                } else {
                    this.fishAngle += (float) (this.random.nextGaussian() * 4.0D);
                    float angle = this.fishAngle * 0.017453292F;
                    double fishX = this.position.x + (double) (Mth.sin(angle) * (float) this.timeUntilHooked * 0.1F);
                    double fishY = (float) Mth.floor(this.boundingBox.minY) + 1.0F;
                    double fishZ = this.position.z + (double) (Mth.cos(angle) * (float) this.timeUntilHooked * 0.1F);
                    if (this.level.getBlockState(new BlockPos((int) fishX, (int) fishY - 1, (int) fishZ)).is(Blocks.WATER)) {
                        this.random.nextFloat();
                        //worldserver.spawnParticle(EnumParticleTypes.WATER_BUBBLE, ...);
                    }
                }
            } else if (this.timeUntilLured > 0) {
                this.timeUntilLured -= fishingSpeed;
                float teaseChance = 0.15F;
                if (this.timeUntilLured < 20) {
                    teaseChance = (float) ((double) teaseChance + (double) (20 - this.timeUntilLured) * 0.05D);
                } else if (this.timeUntilLured < 40) {
                    teaseChance = (float) ((double) teaseChance + (double) (40 - this.timeUntilLured) * 0.02D);
                } else if (this.timeUntilLured < 60) {
                    teaseChance = (float) ((double) teaseChance + (double) (60 - this.timeUntilLured) * 0.01D);
                }

                if (this.random.nextFloat() < teaseChance) {
                    float angle = Mth.nextFloat(this.random, 0.0F, 360.0F) * 0.017453292F;
                    float dist = Mth.nextFloat(this.random, 25.0F, 60.0F);
                    double fishX = this.position.x + (double) (Mth.sin(angle) * dist * 0.1F);
                    double fishY = (float) Mth.floor(this.boundingBox.minY) + 1.0F;
                    double fishZ = this.position.z + (double) (Mth.cos(angle) * dist * 0.1F);
                    if (this.level.getBlockState(new BlockPos((int) fishX, (int) fishY - 1, (int) fishZ)).is(Blocks.WATER)) {
                        this.random.nextInt(2);
                        //worldserver.spawnParticle(EnumParticleTypes.WATER_SPLASH, ...);
                    }
                }

                if (this.timeUntilLured <= 0) {
                    this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                    this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
                }
            } else {
                this.timeUntilLured = Mth.nextInt(this.random, 100, 900);
                this.timeUntilLured -= this.lureLevel * 20 * 5;
            }

            if (this.nibble > 0) {
                this.deltaMovement = this.deltaMovement.add(0.0D, -(double) (this.random.nextFloat() * this.random.nextFloat() * this.random.nextFloat()) * 0.2D, 0.0D);
            }
        }

        private enum OpenWaterType {
            ABOVE_WATER,
            INSIDE_WATER,
            INVALID;
        }

        private enum FishHookState {
            FLYING,
            BOBBING;
        }
    }

    // endregion

}
