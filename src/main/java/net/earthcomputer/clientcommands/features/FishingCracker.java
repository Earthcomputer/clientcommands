package net.earthcomputer.clientcommands.features;

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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
    // goals
    public static final List<WithStringArgument.Result<ClientItemPredicateArgument.ClientItemPredicate>> goals = new ArrayList<>();
    private static boolean hasWarnedMultipleEnchants = false;

    // loot state
    private static ItemStack actualLoot;
    private static final Catch[] expectedCatches = new Catch[21]; // must be an odd number

    // bobber state
    private static ItemStack tool;
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
    private static volatile int magicMillisecondsCorrection = -100;
    private static final ScheduledExecutorService DELAY_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    // state
    public static volatile State state = State.NOT_MANIPULATING;
    private static final Object STATE_LOCK = new Object();

    // fishing rod uses
    private static int expectedFishingRodUses = 0;

    // region LOOT SIMULATION

    private static boolean isMatchingLoot(ItemStack loot, ClientItemPredicateArgument.ClientItemPredicate goal) {
        return goal.getPossibleItems().contains(loot.getItem());
    }

    private static List<com.seedfinding.mcfeature.loot.item.ItemStack> generateAllMatchingLoot(LootTable table, @Nullable LootContext context, ClientItemPredicateArgument.ClientItemPredicate goal, Consumer<LootCondition> failedConditions) {
        var result = new ArrayList<com.seedfinding.mcfeature.loot.item.ItemStack>();
        for (LootPool lootPool : table.lootPools) {
            result.addAll(generateAllMatchingLootForPool(lootPool, context, goal, failedConditions));
        }

        if (!result.isEmpty() && !checkConditions(table, context, failedConditions)) {
            return Collections.emptyList();
        }

        return result;
    }

    private static List<com.seedfinding.mcfeature.loot.item.ItemStack> generateAllMatchingLootForPool(LootPool pool, @Nullable LootContext context, ClientItemPredicateArgument.ClientItemPredicate goal, Consumer<LootCondition> failedConditions) {
        var result = new ArrayList<com.seedfinding.mcfeature.loot.item.ItemStack>();
        for (LootEntry lootEntry : pool.lootEntries) {
            result.addAll(generateAllMatchingLootForEntry(lootEntry, context, goal, failedConditions));
        }

        if (!result.isEmpty() && !checkConditions(pool, context, failedConditions)) {
            return Collections.emptyList();
        }

        return result;
    }

    private static List<com.seedfinding.mcfeature.loot.item.ItemStack> generateAllMatchingLootForEntry(LootEntry entry, @Nullable LootContext context, ClientItemPredicateArgument.ClientItemPredicate goal, Consumer<LootCondition> failedConditions) {
        List<com.seedfinding.mcfeature.loot.item.ItemStack> result = Collections.emptyList();

        if (entry instanceof ItemEntry itemEntry) {
            if (isMatchingLoot(SeedfindingUtil.fromSeedfindingItem(itemEntry.item), goal)) {
                result = Collections.singletonList(new com.seedfinding.mcfeature.loot.item.ItemStack(itemEntry.item));
            }
        } else if (entry instanceof TableEntry tableEntry) {
            result = generateAllMatchingLoot(tableEntry.table.get().apply(SeedfindingUtil.getMCVersion()), context, goal, failedConditions);
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

            if ((seed >>> 16 << 32) + (int)(((seed * 0x5deece66dL + 0xbL) & ((1L << 48) - 1)) >>> 16) == nextLongOutput) {
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
        return Configs.getFishingManipulation().isEnabled() && !goals.isEmpty();
    }

    private static void handleFishingRodThrow(ItemStack stack) {
        long time = System.nanoTime();
        synchronized (STATE_LOCK) {
            throwTime = time;
            estimatedTicksElapsed = 0;
            state = State.WAITING_FOR_BOBBER;
        }
        tool = stack;
    }

    public static void reset() {
        synchronized (STATE_LOCK) {
            state = State.NOT_MANIPULATING;

            if (canManipulateFishing() && Configs.getFishingManipulation() == Configs.FishingManipulation.AFK) {
                state = State.WAITING_FOR_RETRHOW;
                TaskManager.addTask("cfishRethrow", new LongTask() {
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
                if (packet.getData() == player.getId() && packet.getType() == EntityType.FISHING_BOBBER) {
                    processBobberSpawn(packet.getUUID(), new Vec3(packet.getX(), packet.getY(), packet.getZ()), new Vec3(packet.getXa(), packet.getYa(), packet.getZa()));
                }
            }
        });
        MoreClientEntityEvents.PRE_ADD_MAYBE_ON_NETWORK_THREAD.register(packet -> {
            // Called on network thread first, FishingCracker.waitingForFishingRod
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }

            if (!canManipulateFishing() || packet.getData() != player.getId() || packet.getType() != EntityType.FISHING_BOBBER) {
                return;
            }

            onFishingBobberEntity();
        });
        MoreClientEntityEvents.POST_ADD_XP_ORB.register(packet -> {
            if (canManipulateFishing()) {
                processExperienceOrbSpawn(packet.getX(), packet.getY(), packet.getZ(), packet.getValue());
            }
        });
        MoreClientEvents.TIME_SYNC_ON_NETWORK_THREAD.register(packet -> {
            if (Configs.getFishingManipulation().isEnabled()) {
                onTimeSync();
            }
        });
    }

    private static void processBobberSpawn(UUID fishingBobberUUID, Vec3 pos, Vec3 velocity) {
        synchronized (STATE_LOCK) {
            if (state != State.WAITING_FOR_FIRST_BOBBER_TICK) {
                return;
            }
            state = State.WAITING_FOR_FISH;
        }

        OptionalLong optionalSeed = getSeed(fishingBobberUUID);
        if (optionalSeed.isEmpty()) {
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
        SimulatedFishingBobber fishingBobber = new SimulatedFishingBobber(seed, tool, pos, velocity);

        boolean wasCatchingFish = false;
        int ticksUntilOurItem = -1;
        List<Catch> possibleExpectedCatches = new ArrayList<>();
        int ourExpectedCatchIndex = -1;

        List<Vec3> bobberPositions = new ArrayList<>();
        bobberPositions.add(pos);
        // TODO: get a smarter number of max ticks based on the rarity of the item
        for (int ticks = 0; ticks < 10000; ticks++) {
            fishingBobber.tick();
            bobberPositions.add(fishingBobber.pos);
            if (fishingBobber.failedReason != null) {
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
                    bobberDestPos = fishingBobber.pos;
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
            // diagnose issue
            Set<LootCondition> failedConditions = new HashSet<>();
            boolean impossible = true;
            LootTable fishingLootTable = MCLootTables.FISHING.get().apply(SeedfindingUtil.getMCVersion());
            for (var goal : goals) {
                if (goal.value() instanceof ClientItemPredicateArgument.EnchantedItemPredicate predicate) {
                    if (predicate.isEnchantedBook() && predicate.predicate.numEnchantments() >= 2) {
                        if (!hasWarnedMultipleEnchants) {
                            ClientCommandHelper.sendHelp(Component.translatable("commands.cfish.help.tooManyEnchants"));
                            hasWarnedMultipleEnchants = true;
                        }
                    }
                }
                impossible &= generateAllMatchingLoot(fishingLootTable, fishingBobber.getLootContext(), goal.value(), failedConditions::add).isEmpty();
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
                    if (!fishingBobber.level.getBlockState(BlockPos.containing(fishingBobber.pos).above()).is(Blocks.LILY_PAD)) {
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

            if (retractFishingBobber()) {
                if (!throwFishingBobber()) {
                    reset();
                }
            } else {
                reset();
            }
        } else {
            totalTicksToWait = ticksUntilOurItem;
            Arrays.fill(expectedCatches, null);
            for (int i = Math.max(0, ourExpectedCatchIndex - expectedCatches.length / 2),
                     e = Math.min(possibleExpectedCatches.size(), ourExpectedCatchIndex + 1 + expectedCatches.length / 2);
                 i < e; i++) {
                expectedCatches[i - ourExpectedCatchIndex + expectedCatches.length / 2] = possibleExpectedCatches.get(i);
            }
        }
    }

    public static void processItemSpawn(Vec3 pos, ItemStack stack) {
        synchronized (STATE_LOCK) {
            if (state != State.WAITING_FOR_ITEM) {
                return;
            }
            if (Math.abs(pos.x - bobberDestPos.x) >= 1 || Math.abs(pos.z - bobberDestPos.z) >= 1 || Math.abs(pos.y - bobberDestPos.y) >= 5) {
                return;
            }
            state = State.WAITING_FOR_XP;
        }

        actualLoot = stack;
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

        Catch actualCatch = new Catch(actualLoot, experienceAmount);
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
            magicMillisecondsCorrection = (int) Math.round(CombinedMedianEM.mu);
        }
    }

    private static void onTimeSync() {
        long time = System.nanoTime();
        //noinspection SuspiciousSystemArraycopy
        System.arraycopy(timeSyncTimes, 1, timeSyncTimes, 0, timeSyncTimes.length - 1);
        timeSyncTimes[timeSyncTimes.length - 1] = time;
        if (timeSyncTimes[0] != 0) {
            serverMspt = (int) ((time - timeSyncTimes[0]) / ((timeSyncTimes.length - 1) * 20 * 1000000));
        }

        if (state == State.WAITING_FOR_FISH) {
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
                DELAY_EXECUTOR.schedule(() -> {
                    if (!Configs.getFishingManipulation().isEnabled() || state != State.ASYNC_WAITING_FOR_FISH) {
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
                        packetListener.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0));
                        synchronized (STATE_LOCK) {
                            state = State.WAITING_FOR_ITEM;
                        }
                        Minecraft.getInstance().tell(() -> {
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player != null) {
                                ItemStack oldStack = player.getMainHandItem();

                                // If the player interaction packet gets handled before the next tick,
                                // then the fish hook would be null and the client would act as if the fishing rod is extending.
                                // Temporarily set to the previous fishing hook to fix this.
                                expectedFishingRodUses++;
                                FishingHook prevFishingHook = player.fishing;
                                player.fishing = oldFishingHook;
                                InteractionResultHolder<ItemStack> result = oldStack.use(player.level(), player, InteractionHand.MAIN_HAND);
                                player.fishing = prevFishingHook;

                                if (oldStack != result.getObject()) {
                                    player.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
                                }
                                if (result.getResult().consumesAction() && result.getResult().shouldSwing()) {
                                    player.swing(InteractionHand.MAIN_HAND);
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
            expectedFishingRodUses--;
            return;
        }

        handleFishingRodThrow(stack);
    }

    public static void onRetractedFishingRod() {
        if (expectedFishingRodUses > 0) {
            expectedFishingRodUses--;
            return;
        }

        reset();
    }

    private static void onFishingBobberEntity() {
        bobberStartTime = System.nanoTime();

        synchronized (STATE_LOCK) {
            if (state != State.WAITING_FOR_BOBBER) {
                return;
            }
            state = State.WAITING_FOR_FIRST_BOBBER_TICK;

            int thrownItemDeltaMillis = (int) ((bobberStartTime - throwTime) / 1000000);
            int localPingMillis = PingCommand.getLocalPing();

            //The 1000 divided by 20 is the number milliseconds per tick there are
            int timeFromEndOfTick = thrownItemDeltaMillis - localPingMillis;

            averageTimeToEndOfTick = (averageTimeToEndOfTick * 3 + timeFromEndOfTick) / 4;
        }
    }

    public static void onBobOutOfWater() {
        Component message = Component.translatable("commands.cfish.error.outOfWater").withStyle(style -> style.withColor(ChatFormatting.RED));
        ClientCommandHelper.addOverlayMessage(message, 100);
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
            this.experience = experience;
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
        private static final EntityDimensions FISHING_BOBBER_DIMENSIONS = EntityType.FISHING_BOBBER.getDimensions();

        private final Level level = Objects.requireNonNull(Minecraft.getInstance().level);

        private final FishingHook fakeEntity = new FishingHook(Objects.requireNonNull(Minecraft.getInstance().player), level, 0, 0);

        // state variables
        private Vec3 pos;
        private AABB boundingBox;
        private Vec3 velocity;
        private boolean onGround;
        private State state = State.FLYING;
        private int hookCountdown;
        private int fishTravelCountdown;
        private boolean inOpenWater = true;
        private final Set<BlockPos> openWaterViolations = new LinkedHashSet<>(0);
        private int outOfOpenWaterTicks;
        private boolean caughtFish;
        private boolean horizontalCollision;
        private boolean verticalCollision;
        private int waitCountdown;
        private boolean touchingWater;
        private boolean firstUpdate;

        private float fishAngle;

        private final RandomSource random;
        private final ItemStack tool;
        private final int lureLevel;
        private final int luckLevel;

        // output variables
        @Nullable
        private String failedReason;

        public SimulatedFishingBobber(long seed, ItemStack tool, Vec3 pos, Vec3 velocity) {
            this.random = RandomSource.create(seed ^ 0x5deece66dL);
            // entity UUID
            Mth.createInsecureUUID(random);

            // entity yaw and pitch (ProjectileEntity.setVelocity)
            random.triangle(0, 1);
            random.triangle(0, 1);
            random.triangle(0, 1);

            this.tool = tool;
            this.lureLevel = EnchantmentHelper.getFishingSpeedBonus(tool);
            this.luckLevel = EnchantmentHelper.getFishingLuckBonus(tool);
            this.pos = pos;
            this.velocity = velocity;
            this.boundingBox = FISHING_BOBBER_DIMENSIONS.makeBoundingBox(pos.x, pos.y, pos.z);
        }

        public boolean canCatchFish() {
            return hookCountdown > 0;
        }

        public List<Catch> generateLoot() {
            fakeEntity.absMoveTo(pos.x, pos.y, pos.z);
            fakeEntity.setDeltaMovement(velocity);

            LootContext lootContext = getLootContext();

            List<Catch> catches = new ArrayList<>();
            for (var loot : MCLootTables.FISHING.get().generate(lootContext)) {
                catches.add(new Catch(SeedfindingUtil.fromSeedfindingItem(loot), 1 + lootContext.nextInt(6)));
            }

            return catches;
        }

        private LootContext getLootContext() {
            return new LootContext(((LegacyRandomSource) random).seed.get() ^ 0x5deece66dL, SeedfindingUtil.getMCVersion())
                .withBiome(SeedfindingUtil.toSeedfindingBiome(level, level.getBiome(BlockPos.containing(pos))))
                .withOpenWater(inOpenWater)
                .withLuck(luckLevel);
        }

        public void tick() {
            onBaseTick();

            if (this.onGround) {
                failedReason = "onGround";
            }

            float f = 0.0F;
            BlockPos blockPos = BlockPos.containing(this.pos);
            FluidState fluidState = this.level.getFluidState(blockPos);
            if (fluidState.is(FluidTags.WATER)) {
                f = fluidState.getHeight(this.level, blockPos);
            }

            boolean bl = f > 0.0F;
            if (this.state == State.FLYING) {
                if (bl) {
                    this.velocity = this.velocity.multiply(0.3D, 0.2D, 0.3D);
                    this.state = State.BOBBING;
                    return;
                }

                this.checkForCollision();
            } else {
                if (this.state == State.BOBBING) {
                    Vec3 vec3 = this.velocity;
                    double d = this.pos.y + vec3.y - (double)blockPos.getY() - (double)f;
                    if (Math.abs(d) < 0.01D) {
                        d += Math.signum(d) * 0.1D;
                    }

                    this.velocity = new Vec3(vec3.x * 0.9D, vec3.y - d * (double)this.random.nextFloat() * 0.2D, vec3.z * 0.9D);
                    if (this.hookCountdown <= 0 && this.fishTravelCountdown <= 0) {
                        this.inOpenWater = true;
                    } else {
                        this.inOpenWater &= this.outOfOpenWaterTicks < 10 & this.isOpenOrWaterAround(blockPos);
                    }

                    if (bl) {
                        this.outOfOpenWaterTicks = Math.max(0, this.outOfOpenWaterTicks - 1);
                        if (this.caughtFish) {
                            // this will just drag the bobber down which we don't care about
                            //this.velocity = (this.velocity.add(0.0D, -0.1D * (double)this.velocityRandom.nextFloat() * (double)this.velocityRandom.nextFloat(), 0.0D));
                        }

                        this.tickFishingLogic(blockPos);
                    } else {
                        this.outOfOpenWaterTicks = Math.min(10, this.outOfOpenWaterTicks + 1);
                    }
                }
            }

            if (!fluidState.is(FluidTags.WATER)) {
                this.velocity = this.velocity.add(0.0D, -0.03D, 0.0D);
            }

            this.move(this.velocity);
            if (this.state == State.FLYING && (this.onGround || this.horizontalCollision)) {
                this.velocity = Vec3.ZERO;
            }

            double e = 0.92D;
            this.velocity = this.velocity.scale(e);

            boundingBox = FISHING_BOBBER_DIMENSIONS.makeBoundingBox(pos.x, pos.y, pos.z);
        }

        private void onBaseTick() {
            this.updateWaterState();

            this.firstUpdate = false;
        }

        private void updateWaterState() {
            this.checkWaterState();
        }

        private void checkWaterState() {
            if (this.updateMovementInFluid(FluidTags.WATER, 0.014D)) {
                if (!this.touchingWater && !this.firstUpdate) {
                    this.onSwimmingStart();
                }

                this.touchingWater = true;
            } else {
                if (this.touchingWater) {
                    failedReason = "outOfWater";
                }
                this.touchingWater = false;
            }
        }

        private void onSwimmingStart() {
            float f = 0.2F;
            Vec3 vec3 = velocity;
            float g = (float) Math.sqrt(vec3.x * vec3.x * 0.20000000298023224D + vec3.y * vec3.y + vec3.z * vec3.z * 0.20000000298023224D) * f;
            if (g > 1.0F) {
                g = 1.0F;
            }

            if ((double)g < 0.25D) {
                random.nextFloat();
                random.nextFloat();
                //this.playSound(this.getSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            } else {
                random.nextFloat();
                random.nextFloat();
                //this.playSound(this.getHighSpeedSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            }

            float h = (float)Mth.floor(this.pos.y);

            int j;
            double k;
            double l;
            for(j = 0; (float)j < 1.0F + FISHING_BOBBER_DIMENSIONS.width() * 20.0F; ++j) {
                k = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width();
                l = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width();
                random.nextDouble();
                //this.world.addParticle(ParticleTypes.BUBBLE, this.pos.x + k, (double)(h + 1.0F), this.pos.z + l, vec3d.x, vec3d.y - this.random.nextDouble() * 0.20000000298023224D, vec3d.z);
            }

            for(j = 0; (float)j < 1.0F + FISHING_BOBBER_DIMENSIONS.width() * 20.0F; ++j) {
                k = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width();
                l = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width();
                //this.world.addParticle(ParticleTypes.SPLASH, this.getX() + k, (double)(h + 1.0F), this.getZ() + l, vec3d.x, vec3d.y, vec3d.z);
            }
        }

        private boolean updateMovementInFluid(TagKey<Fluid> tag, double d) {
            AABB aabb = this.boundingBox.deflate(0.001D);
            int i = Mth.floor(aabb.minX);
            int j = Mth.ceil(aabb.maxX);
            int k = Mth.floor(aabb.minY);
            int l = Mth.ceil(aabb.maxY);
            int m = Mth.floor(aabb.minZ);
            int n = Mth.ceil(aabb.maxZ);
            if (!this.level.hasChunksAt(i, k, m, j, l, n)) {
                return false;
            } else {
                double e = 0.0D;
                boolean bl2 = false;
                Vec3 vec3 = Vec3.ZERO;
                int o = 0;
                BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

                for (int p = i; p < j; ++p) {
                    for (int q = k; q < l; ++q) {
                        for (int r = m; r < n; ++r) {
                            mutable.set(p, q, r);
                            FluidState fluidState = this.level.getFluidState(mutable);
                            if (fluidState.is(tag)) {
                                double f = (float)q + fluidState.getHeight(this.level, mutable);
                                if (f >= aabb.minY) {
                                    bl2 = true;
                                    e = Math.max(f - aabb.minY, e);
                                }
                            }
                        }
                    }
                }

                if (vec3.length() > 0.0D) {
                    if (o > 0) {
                        vec3 = vec3.scale(1.0D / (double)o);
                    }

                    vec3 = vec3.normalize();

                    Vec3 vec33 = this.velocity;
                    vec3 = vec3.scale(d);
                    double g = 0.003D;
                    if (Math.abs(vec33.x) < 0.003D && Math.abs(vec33.z) < 0.003D && vec3.length() < 0.0045000000000000005D) {
                        vec3 = vec3.normalize().scale(0.0045000000000000005D);
                    }

                    this.velocity = this.velocity.add(vec3);
                }

                return bl2;
            }
        }

        private void checkForCollision() {
            fakeEntity.absMoveTo(pos.x, pos.y, pos.z);
            fakeEntity.setDeltaMovement(velocity);
            HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(fakeEntity, fakeEntity::canHitEntity);
            if (hitResult.getType() != HitResult.Type.MISS) {
                failedReason = "collision";
            }
        }

        private void move(Vec3 movement) {
            assert level != null;

            Vec3 vec3 = this.adjustMovementForCollisions(movement);
            if (vec3.lengthSqr() > 1.0E-7D) {
                this.boundingBox = this.boundingBox.move(vec3);
                this.pos = new Vec3((boundingBox.minX + boundingBox.maxX) / 2.0D, boundingBox.minY, (boundingBox.minZ + boundingBox.maxZ) / 2.0D);
            }

            this.horizontalCollision = !Mth.equal(movement.x, vec3.x) || !Mth.equal(movement.z, vec3.z);
            this.verticalCollision = movement.y != vec3.y;
            this.onGround = this.verticalCollision && movement.y < 0.0D;
            //this.fall(vec3d.y, this.onGround, blockState, blockPos);
            Vec3 vec32 = this.velocity;
            if (movement.x != vec3.x) {
                this.velocity = new Vec3(0.0D, vec32.y, vec32.z);
            }

            if (movement.z != vec3.z) {
                this.velocity = new Vec3(vec32.x, vec32.y, 0.0D);
            }

            if (movement.y != vec3.y) {
                // block.onLanded
                velocity = velocity.multiply(1.0D, 0.0D, 1.0D);
            }

            if (this.onGround) {
                // block.onSteppedOn
            }

            //this.checkBlockCollision(); // no interesting blocks

            float i = this.getVelocityMultiplier();
            this.velocity = this.velocity.multiply((double)i, 1.0D, (double)i);
            if (this.level.getBlockStatesIfLoaded(this.boundingBox.deflate(0.001D)).anyMatch((blockStatex) -> blockStatex.is(BlockTags.FIRE) || blockStatex.is(Blocks.LAVA))) {
                failedReason = "fire";
            }
        }

        private Vec3 adjustMovementForCollisions(Vec3 movement) {
            AABB aabb = this.boundingBox;
            fakeEntity.absMoveTo(pos.x, pos.y, pos.z);
            fakeEntity.setDeltaMovement(velocity);
            assert level != null;

            VoxelShape voxelShape = this.level.getWorldBorder().getCollisionShape();
            List<VoxelShape> voxelShapes = new ArrayList<>();
            if (!Shapes.joinIsNotEmpty(voxelShape, Shapes.create(aabb.deflate(1.0E-7D)), BooleanOp.AND)) {
                voxelShapes.add(voxelShape);
            }
            voxelShapes.addAll(this.level.getEntityCollisions(fakeEntity, aabb.expandTowards(movement)));

            return movement.lengthSqr() == 0.0D ? movement : Entity.collideBoundingBox(fakeEntity, movement, aabb, this.level, voxelShapes);
        }

        private float getVelocityMultiplier() {
            assert level != null;
            Block block = this.level.getBlockState(BlockPos.containing(pos)).getBlock();
            float f = block.getSpeedFactor();
            if (block != Blocks.WATER && block != Blocks.BUBBLE_COLUMN) {
                return (double)f == 1.0D ? this.level
                    .getBlockState(BlockPos.containing(this.pos.x, this.boundingBox.minY - 0.5000001D, this.pos.z)).getBlock().getSpeedFactor() : f;
            } else {
                return f;
            }
        }

        private boolean isOpenOrWaterAround(BlockPos pos) {
            PositionType positionType = PositionType.INVALID;

            boolean valid = true;

            for (int i = -1; i <= 2; ++i) {
                PositionType positionType2 = this.getPositionType(pos.offset(-2, i, -2), pos.offset(2, i, 2));
                switch (positionType2) {
                    case INVALID:
                        valid = false;
                        break;
                    case ABOVE_WATER:
                        if (positionType == PositionType.INVALID) {
                            valid = false;
                        }
                        break;
                    case INSIDE_WATER:
                        if (positionType == PositionType.ABOVE_WATER) {
                            valid = false;
                        }
                        break;
                }

                if (!valid) {
                    List<BlockPos> aboveWaterBlocks = new ArrayList<>(0);
                    boolean foundWater = false;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos pos2 = pos.offset(dx, i, dz);
                            PositionType positionType3 = getPositionType(pos2);
                            if (positionType3 == PositionType.INVALID) {
                                openWaterViolations.add(pos2);
                            } else if (positionType3 == PositionType.ABOVE_WATER) {
                                aboveWaterBlocks.add(pos2);
                            } else if (positionType3 == PositionType.INSIDE_WATER) {
                                foundWater = true;
                            }
                        }
                    }
                    if (foundWater) {
                        openWaterViolations.addAll(aboveWaterBlocks);
                    }
                }

                positionType = positionType2;
            }

            return valid;
        }

        private PositionType getPositionType(BlockPos start, BlockPos end) {
            return BlockPos.betweenClosedStream(start, end).map(this::getPositionType).reduce((positionType, positionType2) -> positionType == positionType2 ? positionType : PositionType.INVALID).orElse(PositionType.INVALID);
        }

        private PositionType getPositionType(BlockPos pos) {
            assert level != null;
            BlockState blockState = this.level.getBlockState(pos);
            if (!blockState.isAir() && !blockState.is(Blocks.LILY_PAD)) {
                FluidState fluidState = blockState.getFluidState();
                return fluidState.is(FluidTags.WATER) && fluidState.isSource() && blockState.getCollisionShape(this.level, pos).isEmpty() ? PositionType.INSIDE_WATER : PositionType.INVALID;
            } else {
                return PositionType.ABOVE_WATER;
            }
        }

        private void tickFishingLogic(BlockPos pos) {
            assert level != null;
            int i = 1;
            BlockPos blockPos = pos.above();
            if (this.random.nextFloat() < 0.25F && this.level.isRainingAt(blockPos)) {
                ++i;
            }

            if (this.random.nextFloat() < 0.5F && !this.level.canSeeSky(blockPos)) {
                --i;
            }

            if (this.hookCountdown > 0) {
                --this.hookCountdown;
                if (this.hookCountdown <= 0) {
                    this.waitCountdown = 0;
                    this.fishTravelCountdown = 0;
                    this.caughtFish = false;
                }
            } else {
                float n;
                float o;
                float p;
                double q;
                double r;
                double s;
                BlockState blockState2;
                if (this.fishTravelCountdown > 0) {
                    this.fishTravelCountdown -= i;
                    if (this.fishTravelCountdown > 0) {
                        this.fishAngle += random.triangle(0, 9.188);
                        n = this.fishAngle * 0.017453292F;
                        o = Mth.sin(n);
                        p = Mth.cos(n);
                        q = this.pos.x + (double)(o * (float)this.fishTravelCountdown * 0.1F);
                        r = (double)((float)Mth.floor(this.pos.y) + 1.0F);
                        s = this.pos.z + (double)(p * (float)this.fishTravelCountdown * 0.1F);
                        blockState2 = level.getBlockState(BlockPos.containing(q, r - 1.0D, s));
                        if (blockState2.is(Blocks.WATER)) {
                            if (this.random.nextFloat() < 0.15F) {
                                //serverWorld.spawnParticles(ParticleTypes.BUBBLE, q, r - 0.10000000149011612D, s, 1, (double)o, 0.1D, (double)p, 0.0D);
                            }

                            float k = o * 0.04F;
                            float l = p * 0.04F;
                            //serverWorld.spawnParticles(ParticleTypes.FISHING, q, r, s, 0, (double)l, 0.01D, (double)(-k), 1.0D);
                            //serverWorld.spawnParticles(ParticleTypes.FISHING, q, r, s, 0, (double)(-l), 0.01D, (double)k, 1.0D);
                        }
                    } else {
                        random.nextFloat();
                        random.nextFloat();
                        //this.playSound(SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                        double m = this.pos.y + 0.5D;
                        //serverWorld.spawnParticles(ParticleTypes.BUBBLE, this.getX(), m, this.getZ(), (int)(1.0F + this.getWidth() * 20.0F), (double)this.getWidth(), 0.0D, (double)this.getWidth(), 0.20000000298023224D);
                        //serverWorld.spawnParticles(ParticleTypes.FISHING, this.getX(), m, this.getZ(), (int)(1.0F + this.getWidth() * 20.0F), (double)this.getWidth(), 0.0D, (double)this.getWidth(), 0.20000000298023224D);
                        this.hookCountdown = Mth.nextInt(this.random, 20, 40);
                        this.caughtFish = true;
                        //this.velocity = new Vec3d(this.velocity.x, (double)(-0.4F * MathHelper.nextFloat(this.velocityRandom, 0.6F, 1.0F)), this.velocity.z);
                    }
                } else if (this.waitCountdown > 0) {
                    this.waitCountdown -= i;
                    n = 0.15F;
                    if (this.waitCountdown < 20) {
                        n = (float)((double)n + (double)(20 - this.waitCountdown) * 0.05D);
                    } else if (this.waitCountdown < 40) {
                        n = (float)((double)n + (double)(40 - this.waitCountdown) * 0.02D);
                    } else if (this.waitCountdown < 60) {
                        n = (float)((double)n + (double)(60 - this.waitCountdown) * 0.01D);
                    }

                    if (this.random.nextFloat() < n) {
                        o = Mth.nextFloat(this.random, 0.0F, 360.0F) * 0.017453292F;
                        p = Mth.nextFloat(this.random, 25.0F, 60.0F);
                        q = this.pos.x + (double)(Mth.sin(o) * p * 0.1F);
                        r = (float)Mth.floor(this.pos.y) + 1.0F;
                        s = this.pos.z + (double)(Mth.cos(o) * p * 0.1F);
                        blockState2 = level.getBlockState(BlockPos.containing(q, r - 1.0D, s));
                        if (blockState2.is(Blocks.WATER)) {
                            random.nextInt(2);
                            //serverWorld.spawnParticles(ParticleTypes.SPLASH, q, r, s, 2 + this.random.nextInt(2), 0.10000000149011612D, 0.0D, 0.10000000149011612D, 0.0D);
                        }
                    }

                    if (this.waitCountdown <= 0) {
                        this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
                        this.fishTravelCountdown = Mth.nextInt(this.random, 20, 80);
                    }
                } else {
                    this.waitCountdown = Mth.nextInt(this.random, 100, 600);
                    this.waitCountdown -= this.lureLevel * 20 * 5;
                }
            }
        }

        private enum PositionType {
            ABOVE_WATER,
            INSIDE_WATER,
            INVALID;
        }

        private enum State {
            FLYING,
            BOBBING;
        }
    }

    // endregion

}
