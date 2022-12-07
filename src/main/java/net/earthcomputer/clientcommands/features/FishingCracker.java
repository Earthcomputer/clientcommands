package net.earthcomputer.clientcommands.features;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.PingCommand;
import net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgumentType;
import net.earthcomputer.clientcommands.mixin.AlternativeLootConditionAccessor;
import net.earthcomputer.clientcommands.mixin.AndConditionAccessor;
import net.earthcomputer.clientcommands.mixin.CheckedRandomAccessor;
import net.earthcomputer.clientcommands.mixin.CombinedEntryAccessor;
import net.earthcomputer.clientcommands.mixin.EntityPredicateAccessor;
import net.earthcomputer.clientcommands.mixin.EntityPropertiesLootConditionAccessor;
import net.earthcomputer.clientcommands.mixin.FishingHookPredicateAccessor;
import net.earthcomputer.clientcommands.mixin.InvertedLootConditionAccessor;
import net.earthcomputer.clientcommands.mixin.ItemEntryAccessor;
import net.earthcomputer.clientcommands.mixin.LocationCheckLootConditionAccessor;
import net.earthcomputer.clientcommands.mixin.LocationPredicateAccessor;
import net.earthcomputer.clientcommands.mixin.LootContextAccessor;
import net.earthcomputer.clientcommands.mixin.LootPoolAccessor;
import net.earthcomputer.clientcommands.mixin.LootPoolEntryAccessor;
import net.earthcomputer.clientcommands.mixin.LootTableAccessor;
import net.earthcomputer.clientcommands.mixin.LootTableEntryAccessor;
import net.earthcomputer.clientcommands.mixin.ProjectileEntityAccessor;
import net.earthcomputer.clientcommands.mixin.TagEntryAccessor;
import net.earthcomputer.clientcommands.mixin.WeatherCheckLootConditionAccessor;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.data.server.loottable.FishingLootTableGenerator;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.AlternativeLootCondition;
import net.minecraft.loot.condition.EntityPropertiesLootCondition;
import net.minecraft.loot.condition.InvertedLootCondition;
import net.minecraft.loot.condition.LocationCheckLootCondition;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionTypes;
import net.minecraft.loot.condition.WeatherCheckLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.entry.CombinedEntry;
import net.minecraft.loot.entry.EmptyEntry;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.loot.entry.LootTableEntry;
import net.minecraft.loot.entry.TagEntry;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.predicate.FluidPredicate;
import net.minecraft.predicate.LightPredicate;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.FishingHookPredicate;
import net.minecraft.predicate.entity.TypeSpecificPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FishingCracker {
    private static final Logger LOGGER = LogUtils.getLogger();

    // goals
    public static final List<ClientItemPredicateArgumentType.ClientItemPredicate> goals = new ArrayList<>();
    private static boolean hasWarnedMultipleEnchants = false;

    // loot state
    private static ItemStack actualLoot;
    private static final Catch[] expectedCatches = new Catch[21]; // must be an odd number

    // bobber state
    private static ItemStack tool;
    private static Vec3d bobberDestPos;
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

    private static final Map<Identifier, LootTable> FISHING_LOOT_TABLES;
    private static final LootTable FISHING_LOOT_TABLE;
    private static final Map<Item, LootConditionType> LOOT_CONDITIONS = new IdentityHashMap<>();
    private static final LootContextParameter<Boolean> IN_OPEN_WATER_PARAMETER = new LootContextParameter<>(new Identifier("clientcommands", "in_open_water"));
    static {
        // fix for class load order issue
        try {
            Class.forName(EntityPredicate.class.getName(), true, FishingCracker.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }

        var fishingLootTables = ImmutableMap.<Identifier, LootTable>builder();
        new FishingLootTableGenerator().accept((id, builder) -> fishingLootTables.put(id, builder.build()));
        FISHING_LOOT_TABLES = fishingLootTables.build();

        {
            LootTable lootTable = FISHING_LOOT_TABLES.get(LootTables.FISHING_GAMEPLAY);
            walkLootTable(lootTable, new LootCondition[0]);
            FISHING_LOOT_TABLE = lootTable;
            assert lootTable != null;
            LootPool[] pools = ((LootTableAccessor) lootTable).getPools();
            LootPoolEntry[] entries = ((LootPoolAccessor) pools[0]).getEntries();
            for (LootPoolEntry entry : entries) {
                LootCondition[] conditions = ((LootPoolEntryAccessor) entry).getConditions();
                for (int i = 0; i < conditions.length; i++) {
                    LootCondition condition = conditions[i];
                    if (condition instanceof EntityPropertiesLootCondition) {
                        conditions[i] = new EntityPropertiesLootCondition(null, null) {
                            @Override
                            public Set<LootContextParameter<?>> getRequiredParameters() {
                                return ImmutableSet.<LootContextParameter<?>>builder().addAll(super.getRequiredParameters()).add(IN_OPEN_WATER_PARAMETER).build();
                            }

                            @Override
                            public boolean test(LootContext lootContext) {
                                Boolean ret = lootContext.get(IN_OPEN_WATER_PARAMETER);
                                assert ret != null;
                                return ret;
                            }
                        };
                        ((LootPoolEntryAccessor) entry).setConditionPredicate(LootConditionTypes.joinAnd(conditions));
                    }
                }
            }
        }

        {
            LootTable lootTable = FISHING_LOOT_TABLES.get(LootTables.FISHING_JUNK_GAMEPLAY);
            assert lootTable != null;
            LootPool[] pools = ((LootTableAccessor) lootTable).getPools();
            for (LootPool pool : pools) {
                LootPoolEntry[] entries = ((LootPoolAccessor) pool).getEntries();
                for (LootPoolEntry entry : entries) {
                    LootCondition[] conditions = ((LootPoolEntryAccessor) entry).getConditions();
                    for (LootCondition condition : conditions) {
                        if (condition instanceof AlternativeLootCondition) {
                            LootCondition[] terms = ((AlternativeLootConditionAccessor) condition).getTerms();
                            for (int i = 0; i < terms.length; i++) {
                                LootCondition term = terms[i];
                                if (term instanceof LocationCheckLootCondition) {
                                    var accessor = (LocationCheckLootConditionAccessor) term;
                                    var predicateAccessor = (LocationPredicateAccessor) accessor.getPredicate();
                                    terms[i] = new LocationCheckLootCondition(accessor.getPredicate(), accessor.getOffset()) {
                                        @Override
                                        public boolean test(LootContext lootContext) {
                                            ClientWorld world = MinecraftClient.getInstance().world;
                                            assert world != null;
                                            Vec3d origin = lootContext.get(LootContextParameters.ORIGIN);
                                            if (origin == null) {
                                                return false;
                                            }
                                            origin = origin.add(accessor.getOffset().getX(), accessor.getOffset().getY(), accessor.getOffset().getZ());
                                            if (!predicateAccessor.getX().test((float)origin.getX()) || !predicateAccessor.getY().test((float)origin.getY()) || !predicateAccessor.getZ().test((float)origin.getZ())) {
                                                return false;
                                            }
                                            var biome = world.getBiome(new BlockPos(origin));
                                            return biome.getKey().isPresent() && biome.getKey().get() == predicateAccessor.getBiome();
                                        }
                                    };
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void walkLootTable(LootTable lootTable, LootCondition[] parentConditions) {
        for (LootPool pool : ((LootTableAccessor) lootTable).getPools()) {
            for (LootPoolEntry entry : ((LootPoolAccessor) pool).getEntries()) {
                walkLootEntry(entry, parentConditions);
            }
        }
    }

    private static void walkLootEntry(LootPoolEntry entry, LootCondition[] parentConditions) {
        LootCondition[] conditions = ArrayUtils.addAll(parentConditions, ((LootPoolEntryAccessor) entry).getConditions());
        if (entry instanceof CombinedEntry) {
            for (LootPoolEntry child : ((CombinedEntryAccessor) entry).getChildren()) {
                walkLootEntry(child, conditions);
            }
        } else if (entry instanceof ItemEntry) {
            addCondition(((ItemEntryAccessor) entry).getItem(), conditions);
        } else if (entry instanceof TagEntry) {
            TagKey<Item> tag = ((TagEntryAccessor) entry).getName();
            Registries.ITEM.getEntryList(tag).ifPresent(list -> {
                for (RegistryEntry<Item> item : list) {
                    addCondition(item.value(), conditions);
                }
            });
        } else if (entry instanceof LootTableEntry) {
            walkLootTable(FISHING_LOOT_TABLES.get(((LootTableEntryAccessor) entry).getId()), conditions);
        } else if (!(entry instanceof EmptyEntry)) {
            throw new IllegalArgumentException("Cannot convert loot entry");
        }
    }

    private static abstract sealed class LootConditionType {
        @Nullable
        abstract LootConditionType getFailedCondition(LootContext context, boolean inverted);

        int getPriority() {
            return 0;
        }
    }
    private static final class AlwaysTrueCondition extends LootConditionType {
        public static final AlwaysTrueCondition INSTANCE = new AlwaysTrueCondition();

        @Override
        @Nullable LootConditionType getFailedCondition(LootContext context, boolean inverted) {
            return inverted ? this : null;
        }
    }
    private static final class AndCondition extends LootConditionType {
        private final LootConditionType[] children;

        private AndCondition(LootConditionType[] children) {
            this.children = children;
        }

        @Override
        @Nullable LootConditionType getFailedCondition(LootContext context, boolean inverted) {
            LootConditionType result = null;
            for (LootConditionType child : children) {
                LootConditionType failure = child.getFailedCondition(context, inverted);
                if (failure == null) {
                    if (inverted) {
                        return null;
                    }
                } else {
                    if (result == null) {
                        result = failure;
                    } else if ((result.getPriority() < failure.getPriority()) != inverted) {
                        result = failure;
                    }
                }
            }
            return result;
        }
    }
    private static final class OrCondition extends LootConditionType {
        private final LootConditionType[] children;

        private OrCondition(LootConditionType[] children) {
            this.children = children;
        }

        @Override
        @Nullable LootConditionType getFailedCondition(LootContext context, boolean inverted) {
            LootConditionType result = null;
            for (LootConditionType child : children) {
                LootConditionType failure = child.getFailedCondition(context, inverted);
                if (failure == null) {
                    if (!inverted) {
                        return null;
                    }
                } else {
                    if (result == null) {
                        result = failure;
                    } else if ((result.getPriority() < failure.getPriority()) == inverted) {
                        result = failure;
                    }
                }
            }
            return result;
        }
    }
    private static final class NotCondition extends LootConditionType {
        private final LootConditionType operand;

        private NotCondition(LootConditionType operand) {
            this.operand = operand;
        }

        @Override
        @Nullable LootConditionType getFailedCondition(LootContext context, boolean inverted) {
            return operand.getFailedCondition(context, !inverted);
        }
    }
    private static final class OpenWaterCondition extends LootConditionType {
        public static final OpenWaterCondition INSTANCE = new OpenWaterCondition();

        @Override
        @Nullable LootConditionType getFailedCondition(LootContext context, boolean inverted) {
            Boolean inOpenWater = context.get(IN_OPEN_WATER_PARAMETER);
            assert inOpenWater != null;
            return inOpenWater == inverted ? this : null;
        }

        @Override
        int getPriority() {
            return 2;
        }
    }
    private static final class BiomeCondition extends LootConditionType {
        private final BlockPos offset;
        private final NumberRange.FloatRange xRange;
        private final NumberRange.FloatRange yRange;
        private final NumberRange.FloatRange zRange;
        private final RegistryKey<Biome> biome;

        private BiomeCondition(BlockPos offset, NumberRange.FloatRange xRange, NumberRange.FloatRange yRange, NumberRange.FloatRange zRange, RegistryKey<Biome> biome) {
            this.offset = offset;
            this.xRange = xRange;
            this.yRange = yRange;
            this.zRange = zRange;
            this.biome = biome;
        }

        @Override
        @Nullable LootConditionType getFailedCondition(LootContext context, boolean inverted) {
            ClientWorld world = MinecraftClient.getInstance().world;
            assert world != null;
            Vec3d origin = context.get(LootContextParameters.ORIGIN);
            if (origin == null) {
                return this;
            }
            origin = origin.add(offset.getX(), offset.getY(), offset.getZ());
            if (!xRange.test((float)origin.getX()) || !yRange.test((float)origin.getY()) || !zRange.test((float)origin.getZ())) {
                return inverted ? null : this;
            }
            var biome = world.getBiome(new BlockPos(origin));
            return (biome.getKey().isPresent() && biome.getKey().get() == this.biome) == inverted ? this : null;
        }

        @Override
        int getPriority() {
            return 1;
        }
    }
    private static final class WeatherCheckCondition extends LootConditionType {
        @Nullable final Boolean raining;
        @Nullable final Boolean thundering;

        private WeatherCheckCondition(@Nullable Boolean raining, @Nullable Boolean thundering) {
            this.raining = raining;
            this.thundering = thundering;
        }

        @Override
        @Nullable LootConditionType getFailedCondition(LootContext context, boolean inverted) {
            ClientWorld world = MinecraftClient.getInstance().world;
            assert world != null;
            if (this.raining != null && world.isRaining() != this.raining) {
                return inverted ? null : this;
            }
            if (this.thundering != null && world.isThundering() != this.thundering) {
                return inverted ? null : this;
            }
            return inverted ? this : null;
        }
    }

    private static void addCondition(Item item, LootCondition[] conditions) {
        LootConditionType condition = convertAndCondition(conditions);
        LootConditionType existingCondition = LOOT_CONDITIONS.get(item);
        if (existingCondition == null) {
            LOOT_CONDITIONS.put(item, condition);
            return;
        }
        if (existingCondition instanceof AlwaysTrueCondition) {
            return;
        }
        if (condition instanceof AlwaysTrueCondition) {
            LOOT_CONDITIONS.put(item, condition);
            return;
        }
        if (existingCondition instanceof OrCondition existingOr) {
            if (condition instanceof OrCondition or) {
                LOOT_CONDITIONS.put(item, new OrCondition(ArrayUtils.addAll(existingOr.children, or.children)));
            } else {
                LOOT_CONDITIONS.put(item, new OrCondition(ArrayUtils.add(existingOr.children, condition)));
            }
        } else if (condition instanceof OrCondition or) {
            LOOT_CONDITIONS.put(item, new OrCondition(ArrayUtils.add(or.children, existingCondition)));
        } else {
            LOOT_CONDITIONS.put(item, new OrCondition(new LootConditionType[]{existingCondition, condition}));
        }
    }

    private static LootConditionType convertAndCondition(LootCondition[] conditions) {
        if (conditions.length == 0) {
            return AlwaysTrueCondition.INSTANCE;
        } else if (conditions.length == 1) {
            return convertCondition(conditions[0]);
        } else {
            return new AndCondition(convertConditions(conditions));
        }
    }

    private static LootConditionType[] convertConditions(LootCondition[] conditions) {
        LootConditionType[] result = new LootConditionType[conditions.length];
        for (int i = 0; i < conditions.length; i++) {
            result[i] = convertCondition(conditions[i]);
        }
        return result;
    }

    private static LootConditionType convertCondition(LootCondition condition) {
        if (condition instanceof AlternativeLootCondition) {
            return new OrCondition(convertConditions(((AlternativeLootConditionAccessor) condition).getTerms()));
        } else if (condition instanceof AndConditionAccessor andCondition) {
            return new AndCondition(convertConditions(andCondition.getTerms()));
        } else if (condition instanceof InvertedLootCondition) {
            return new NotCondition(convertCondition(((InvertedLootConditionAccessor) condition).getTerm()));
        } else if (condition instanceof LocationCheckLootCondition) {
            var accessor = (LocationCheckLootConditionAccessor) condition;
            var predicate = (LocationPredicateAccessor) accessor.getPredicate();
            if (predicate.getSmokey() != null
                    || predicate.getDimension() != null
                    || predicate.getFeature() != null
                    || predicate.getFluid() != FluidPredicate.ANY
                    || predicate.getBlock() != BlockPredicate.ANY
                    || predicate.getLight() != LightPredicate.ANY
                    || predicate.getBiome() == null) {
                throw new IllegalArgumentException("Cannot convert condition");
            }
            return new BiomeCondition(accessor.getOffset(), predicate.getX(), predicate.getY(), predicate.getZ(), predicate.getBiome());
        } else if (condition instanceof EntityPropertiesLootCondition) {
            var accessor = (EntityPropertiesLootConditionAccessor) condition;
            if (accessor.getEntity() != LootContext.EntityTarget.THIS) {
                throw new IllegalArgumentException("Cannot convert condition");
            }
            EntityPredicate predicate = accessor.getPredicate();
            for (Field field : EntityPredicate.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.getType() == TypeSpecificPredicate.class) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    if (field.get(predicate) != field.get(EntityPredicate.ANY)) {
                        throw new IllegalArgumentException("Cannot convert condition " + field.getName() + ", " + field.get(predicate) + " != " + field.get(EntityPredicate.ANY));
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
            TypeSpecificPredicate fishingHook = ((EntityPredicateAccessor) predicate).getTypeSpecific();
            if (fishingHook == FishingHookPredicate.ANY || !(fishingHook instanceof FishingHookPredicate)) {
                return AlwaysTrueCondition.INSTANCE;
            } else if (((FishingHookPredicateAccessor) fishingHook).isInOpenWater()) {
                return OpenWaterCondition.INSTANCE;
            } else {
                return new NotCondition(OpenWaterCondition.INSTANCE);
            }
        } else if (condition instanceof WeatherCheckLootCondition) {
            var accessor = (WeatherCheckLootConditionAccessor) condition;
            return new WeatherCheckCondition(accessor.getRaining(), accessor.getThundering());
        } else {
            throw new IllegalArgumentException("Cannot convert condition");
        }
    }

    private static LootContext createLootContext(Random rand, float luck, Map<LootContextParameter<?>, Object> parameters) {
        return LootContextAccessor.createLootContext(rand, luck, null, id -> {
            LootTable lootTable = FISHING_LOOT_TABLES.get(id);
            if (lootTable == null) {
                LOGGER.warn("Unknown loot table: {}", id);
            }
            return lootTable;
        }, id -> null, parameters, Collections.emptyMap());
    }

    // endregion

    // region SEED CRACKING

    /**
     * Returns the internal seed of the Random the instant before it generates the UUID via {@link MathHelper#randomUuid(Random)}
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
                Random rand = Random.create(seed ^ 0x5deece66dL);
                if (MathHelper.randomUuid(rand).equals(uuid)) {
                    return OptionalLong.of(seed);
                }
            }
            
            hi += 1L << 62;
        } while (hi != 0);

        return OptionalLong.empty();
    }

    // endregion

    // region UTILITY

    private static boolean internalInteractFishingBobber(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        ClientPlayerInteractionManager interactionManager = MinecraftClient.getInstance().interactionManager;
        if (player != null && interactionManager != null) {
            ItemStack stack = player.getMainHandStack();
            if (stack.getItem() == Items.FISHING_ROD) {
                expectedFishingRodUses++;
                interactionManager.interactItem(player, Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    public static boolean retractFishingBobber(){
        return internalInteractFishingBobber();
    }

    public static boolean throwFishingBobber() {
        if (internalInteractFishingBobber()){
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            assert player != null;
            ItemStack stack = player.getMainHandStack();
            handleFishingRodThrow(stack);
            return true;
        }
        return false;
    }

    public static boolean canManipulateFishing() {
        return TempRules.getFishingManipulation().isEnabled() && !goals.isEmpty();
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

            if(canManipulateFishing() && TempRules.getFishingManipulation() == TempRules.FishingManipulation.AFK){
                state = State.WAITING_FOR_RETRHOW;
                TaskManager.addTask("cfishRethrow", new LongTask() {
                    private int counter;
                    @Override
                    public void initialize() {
                        counter = RETHROW_COOLDOWN;
                    }

                    @Override
                    public boolean condition() {
                        synchronized (STATE_LOCK){
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
                        synchronized (STATE_LOCK){
                            if(throwFishingBobber()){
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

    public static void processBobberSpawn(UUID fishingBobberUUID, Vec3d pos, Vec3d velocity) {
        synchronized (STATE_LOCK) {
            if (state != State.WAITING_FOR_FIRST_BOBBER_TICK) {
                return;
            }
            state = State.WAITING_FOR_FISH;
        }

        OptionalLong optionalSeed = getSeed(fishingBobberUUID);
        if (optionalSeed.isEmpty()) {
            Text error = Text.translatable("commands.cfish.error.crackFailed").styled(style -> style.withColor(Formatting.RED));
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

        List<Vec3d> bobberPositions = new ArrayList<>();
        bobberPositions.add(pos);
        // TODO: get a smarter number of max ticks based on the rarity of the item
        for (int ticks = 0; ticks < 10000; ticks++) {
            fishingBobber.tick();
            bobberPositions.add(fishingBobber.pos);
            if (fishingBobber.failedReason != null) {
                bobberNumTicks = ticks;
                for (int i = 0; i < bobberPositions.size(); i++) {
                    int color = i == bobberPositions.size() - 1 ? 0xff0000 : 0x00ff00;
                    RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, new ErrorEntry(i, true), SimulatedFishingBobber.FISHING_BOBBER_DIMENSIONS.getBoxAt(bobberPositions.get(i)), color, 100);
                    if (i != 0) {
                        RenderQueue.addLine(RenderQueue.Layer.ON_TOP, new ErrorEntry(i, false), bobberPositions.get(i - 1), bobberPositions.get(i), color, 100);
                    }
                }
                Text error = Text.translatable("commands.cfish.error." + fishingBobber.failedReason).styled(style -> style.withColor(Formatting.RED));
                ClientCommandHelper.addOverlayMessage(error, 100);
                reset();
                return;
            }

            if (fishingBobber.canCatchFish()) {
                List<Catch> catches = fishingBobber.generateLoot();

                if (ourExpectedCatchIndex == -1 && goals.stream().anyMatch(goal -> catches.stream().anyMatch(c -> goal.test(c.loot)))) {
                    bobberDestPos = fishingBobber.pos;
                    ticksUntilOurItem = ticks;
                    ourExpectedCatchIndex = possibleExpectedCatches.size();
                }
                possibleExpectedCatches.add(catches.get(0));
                wasCatchingFish = true;
            } else if (wasCatchingFish) {
                bobberNumTicks = ticks;
                break;
            }
        }

        if (ticksUntilOurItem == -1) {
            // diagnose issue
            LootConditionType failedCondition = null;
            boolean impossible = true;
            for (var goal : goals) {
                if (goal instanceof ClientItemPredicateArgumentType.EnchantedItemPredicate predicate) {
                    if (goal.getPossibleItems().contains(Items.ENCHANTED_BOOK)
                            && predicate.predicate().numEnchantments() >= 2) {
                        if (!hasWarnedMultipleEnchants) {
                            Text help = Text.translatable("commands.cfish.help.tooManyEnchants").styled(style -> style.withColor(Formatting.AQUA));
                            ClientCommandHelper.sendFeedback(help);
                            hasWarnedMultipleEnchants = true;
                        }
                    }
                }
                for (Item item : goal.getPossibleItems()) {
                    LootConditionType conditionType = LOOT_CONDITIONS.get(item);
                    if (conditionType != null) {
                        impossible = false;
                        LootConditionType failed = conditionType.getFailedCondition(fishingBobber.getLootContext(Random.create(((CheckedRandomAccessor) fishingBobber.random).getSeed().get() ^ 0x5deece66dL)), false);
                        if (failed != null) {
                            if (failedCondition == null || failed.getPriority() > failedCondition.getPriority()) {
                                failedCondition = failed;
                            }
                        }
                    }
                }
            }

            if (impossible) {
                Text error = Text.translatable("commands.cfish.error.impossibleLoot").styled(style -> style.withColor(Formatting.RED));
                ClientCommandHelper.addOverlayMessage(error, 100);
                reset();
                return;
            }
            if (failedCondition != null) {
                if (failedCondition instanceof OpenWaterCondition) {
                    Text error = Text.translatable("commands.cfish.error.openWater").styled(style -> style.withColor(Formatting.RED));
                    ClientCommandHelper.addOverlayMessage(error, 100);
                    if (!fishingBobber.world.getBlockState(new BlockPos(fishingBobber.pos).up()).isOf(Blocks.LILY_PAD)) {
                        Text help = Text.translatable("commands.cfish.error.openWater.lilyPad").styled(style -> style.withColor(Formatting.AQUA));
                        ClientCommandHelper.sendFeedback(help);
                    }
                    boolean foundFlowingWater = false;
                    for (BlockPos openWaterViolation : fishingBobber.openWaterViolations) {
                        if (!foundFlowingWater
                            && fishingBobber.world.getBlockState(openWaterViolation).isOf(Blocks.WATER)
                            && !fishingBobber.world.getFluidState(openWaterViolation).isStill()
                        ) {
                            foundFlowingWater = true;
                        }
                        RenderQueue.addCuboid(
                                RenderQueue.Layer.ON_TOP,
                                UUID.randomUUID(),
                                Vec3d.of(openWaterViolation),
                                Vec3d.of(openWaterViolation.add(1, 1, 1)),
                                0xff0000,
                                100
                        );
                    }
                    if (foundFlowingWater) {
                        Text help = Text.translatable("commands.cfish.error.openWater.flowingWater").styled(style -> style.withColor(Formatting.AQUA));
                        ClientCommandHelper.sendFeedback(help);
                    }
                    reset();
                    return;
                }
                if (failedCondition instanceof BiomeCondition biomeCondition) {
                    Text error = Text.translatable(
                            "commands.cfish.error.biome",
                            Text.translatable("biome." + biomeCondition.biome.getValue().getNamespace() + "." + biomeCondition.biome.getValue().getPath())
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

    public static void processItemSpawn(Vec3d pos, ItemStack stack) {
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

    public static void processExperienceOrbSpawn(double x, double y, double z, int experienceAmount) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

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
            ClientCommandHelper.addOverlayMessage(Text.translatable("commands.cfish.correctLoot", magicMillisecondsCorrection)
                    .styled(style -> style.withColor(Formatting.GREEN)), 100);
        } else {
            ClientCommandHelper.addOverlayMessage(Text.translatable("commands.cfish.wrongLoot", magicMillisecondsCorrection, indices)
                    .styled(style -> style.withColor(Formatting.RED)), 100);
        }

        if (!indices.isEmpty()) {
            if (CombinedMedianEM.data.size() >= 10) {
                CombinedMedianEM.data.remove(0);
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

    public static void onTimeSync() {
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
                    if (!TempRules.getFishingManipulation().isEnabled() || state != State.ASYNC_WAITING_FOR_FISH) {
                        return;
                    }
                    ClientPlayerEntity oldPlayer = MinecraftClient.getInstance().player;
                    if (oldPlayer != null) {
                        ClientPlayNetworkHandler networkHandler = oldPlayer.networkHandler;
                        while (System.nanoTime() < targetTime) {
                            if (state != State.ASYNC_WAITING_FOR_FISH){
                                return;
                            }
                        }
                        FishingBobberEntity oldFishingBobberEntity = oldPlayer.fishHook;
                        networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0));
                        synchronized (STATE_LOCK) {
                            state = State.WAITING_FOR_ITEM;
                        }
                        MinecraftClient.getInstance().send(() -> {
                            ClientPlayerEntity player = MinecraftClient.getInstance().player;
                            if (player != null) {
                                ItemStack oldStack = player.getMainHandStack();

                                // If the player interaction packet gets handled before the next tick,
                                // then the fish hook would be null and the client would act as if the fishing rod is extending.
                                // Temporarily set to the previous fishing hook to fix this.
                                expectedFishingRodUses++;
                                FishingBobberEntity prevFishingBobberEntity = player.fishHook;
                                player.fishHook = oldFishingBobberEntity;
                                TypedActionResult<ItemStack> result = oldStack.use(player.world, player, Hand.MAIN_HAND);
                                player.fishHook = prevFishingBobberEntity;

                                if (oldStack != result.getValue()) {
                                    player.setStackInHand(Hand.MAIN_HAND, result.getValue());
                                }
                                if (result.getResult().isAccepted() && result.getResult().shouldSwingHand()) {
                                    player.swingHand(Hand.MAIN_HAND);
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

    public static void onFishingBobberEntity() {
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
        Text message = Text.translatable("commands.cfish.error.outOfWater").styled(style -> style.withColor(Formatting.RED));
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

    public record Catch(ItemStack loot, int experience) {
        @Override
        public int hashCode() {
            return 7 * (31 * Objects.hash(loot.getItem(), loot.getNbt()) + loot.getCount()) + experience;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) return true;
            if (!(other instanceof Catch that)) return false;
            return ItemStack.areEqual(loot, that.loot) && experience == that.experience;
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

        private final World world = Objects.requireNonNull(MinecraftClient.getInstance().world);

        private final FishingBobberEntity fakeEntity = new FishingBobberEntity(Objects.requireNonNull(MinecraftClient.getInstance().player), world, 0, 0);

        // state variables
        private Vec3d pos;
        private Box boundingBox;
        private Vec3d velocity;
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

        private final Random random;
        private final ItemStack tool;
        private final int lureLevel;
        private final int luckLevel;

        // output variables
        @Nullable
        private String failedReason;

        public SimulatedFishingBobber(long seed, ItemStack tool, Vec3d pos, Vec3d velocity) {
            this.random = Random.create(seed ^ 0x5deece66dL);
            // entity UUID
            MathHelper.randomUuid(random);

            // entity yaw and pitch (ProjectileEntity.setVelocity)
            random.nextTriangular(0, 1);
            random.nextTriangular(0, 1);
            random.nextTriangular(0, 1);

            this.tool = tool;
            this.lureLevel = EnchantmentHelper.getLure(tool);
            this.luckLevel = EnchantmentHelper.getLuckOfTheSea(tool);
            this.pos = pos;
            this.velocity = velocity;
            this.boundingBox = FISHING_BOBBER_DIMENSIONS.getBoxAt(pos.x, pos.y, pos.z);
        }

        public boolean canCatchFish() {
            return hookCountdown > 0;
        }

        public List<Catch> generateLoot() {
            fakeEntity.updatePosition(pos.x, pos.y, pos.z);
            fakeEntity.setVelocity(velocity);

            Random randomCopy = Random.create(((CheckedRandomAccessor) random).getSeed().get() ^ 0x5deece66dL);
            LootContext lootContext = getLootContext(randomCopy);

            List<Catch> catches = new ArrayList<>();
            for (ItemStack loot : FISHING_LOOT_TABLE.generateLoot(lootContext)) {
                catches.add(new Catch(loot, 1 + randomCopy.nextInt(6)));
            }
            return catches;
        }

        private LootContext getLootContext(Random randomCopy) {
            var parameters = ImmutableMap.of(
                    LootContextParameters.ORIGIN, pos,
                    LootContextParameters.TOOL, tool,
                    LootContextParameters.THIS_ENTITY, fakeEntity,
                    IN_OPEN_WATER_PARAMETER, inOpenWater
            );
            return createLootContext(randomCopy, luckLevel, parameters);
        }

        public void tick() {
            onBaseTick();

            if (this.onGround) {
                failedReason = "onGround";
            }

            float f = 0.0F;
            BlockPos blockPos = new BlockPos(this.pos);
            FluidState fluidState = this.world.getFluidState(blockPos);
            if (fluidState.isIn(FluidTags.WATER)) {
                f = fluidState.getHeight(this.world, blockPos);
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
                    Vec3d vec3d = this.velocity;
                    double d = this.pos.y + vec3d.y - (double)blockPos.getY() - (double)f;
                    if (Math.abs(d) < 0.01D) {
                        d += Math.signum(d) * 0.1D;
                    }

                    this.velocity = new Vec3d(vec3d.x * 0.9D, vec3d.y - d * (double)this.random.nextFloat() * 0.2D, vec3d.z * 0.9D);
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

            if (!fluidState.isIn(FluidTags.WATER)) {
                this.velocity = this.velocity.add(0.0D, -0.03D, 0.0D);
            }

            this.move(this.velocity);
            if (this.state == State.FLYING && (this.onGround || this.horizontalCollision)) {
                this.velocity = Vec3d.ZERO;
            }

            double e = 0.92D;
            this.velocity = this.velocity.multiply(e);

            boundingBox = FISHING_BOBBER_DIMENSIONS.getBoxAt(pos.x, pos.y, pos.z);
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
            Vec3d vec3d = velocity;
            float g = (float) Math.sqrt(vec3d.x * vec3d.x * 0.20000000298023224D + vec3d.y * vec3d.y + vec3d.z * vec3d.z * 0.20000000298023224D) * f;
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

            float h = (float)MathHelper.floor(this.pos.y);

            int j;
            double k;
            double l;
            for(j = 0; (float)j < 1.0F + FISHING_BOBBER_DIMENSIONS.width * 20.0F; ++j) {
                k = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                l = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                random.nextDouble();
                //this.world.addParticle(ParticleTypes.BUBBLE, this.pos.x + k, (double)(h + 1.0F), this.pos.z + l, vec3d.x, vec3d.y - this.random.nextDouble() * 0.20000000298023224D, vec3d.z);
            }

            for(j = 0; (float)j < 1.0F + FISHING_BOBBER_DIMENSIONS.width * 20.0F; ++j) {
                k = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                l = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                //this.world.addParticle(ParticleTypes.SPLASH, this.getX() + k, (double)(h + 1.0F), this.getZ() + l, vec3d.x, vec3d.y, vec3d.z);
            }
        }

        private boolean updateMovementInFluid(TagKey<Fluid> tag, double d) {
            Box box = this.boundingBox.contract(0.001D);
            int i = MathHelper.floor(box.minX);
            int j = MathHelper.ceil(box.maxX);
            int k = MathHelper.floor(box.minY);
            int l = MathHelper.ceil(box.maxY);
            int m = MathHelper.floor(box.minZ);
            int n = MathHelper.ceil(box.maxZ);
            if (!this.world.isRegionLoaded(i, k, m, j, l, n)) {
                return false;
            } else {
                double e = 0.0D;
                boolean bl2 = false;
                Vec3d vec3d = Vec3d.ZERO;
                int o = 0;
                BlockPos.Mutable mutable = new BlockPos.Mutable();

                for(int p = i; p < j; ++p) {
                    for(int q = k; q < l; ++q) {
                        for(int r = m; r < n; ++r) {
                            mutable.set(p, q, r);
                            FluidState fluidState = this.world.getFluidState(mutable);
                            if (fluidState.isIn(tag)) {
                                double f = (double)((float)q + fluidState.getHeight(this.world, mutable));
                                if (f >= box.minY) {
                                    bl2 = true;
                                    e = Math.max(f - box.minY, e);
                                }
                            }
                        }
                    }
                }

                if (vec3d.length() > 0.0D) {
                    if (o > 0) {
                        vec3d = vec3d.multiply(1.0D / (double)o);
                    }

                    vec3d = vec3d.normalize();

                    Vec3d vec3d3 = this.velocity;
                    vec3d = vec3d.multiply(d);
                    double g = 0.003D;
                    if (Math.abs(vec3d3.x) < 0.003D && Math.abs(vec3d3.z) < 0.003D && vec3d.length() < 0.0045000000000000005D) {
                        vec3d = vec3d.normalize().multiply(0.0045000000000000005D);
                    }

                    this.velocity = this.velocity.add(vec3d);
                }

                return bl2;
            }
        }

        private void checkForCollision() {
            fakeEntity.updatePosition(pos.x, pos.y, pos.z);
            fakeEntity.setVelocity(velocity);
            HitResult hitResult = ProjectileUtil.getCollision(fakeEntity, ((ProjectileEntityAccessor) fakeEntity)::callCanHit);
            if (hitResult.getType() != HitResult.Type.MISS) {
                failedReason = "collision";
            }
        }

        private void move(Vec3d movement) {
            assert world != null;

            Vec3d vec3d = this.adjustMovementForCollisions(movement);
            if (vec3d.lengthSquared() > 1.0E-7D) {
                this.boundingBox = this.boundingBox.offset(vec3d);
                this.pos = new Vec3d((boundingBox.minX + boundingBox.maxX) / 2.0D, boundingBox.minY, (boundingBox.minZ + boundingBox.maxZ) / 2.0D);
            }

            this.horizontalCollision = !MathHelper.approximatelyEquals(movement.x, vec3d.x) || !MathHelper.approximatelyEquals(movement.z, vec3d.z);
            this.verticalCollision = movement.y != vec3d.y;
            this.onGround = this.verticalCollision && movement.y < 0.0D;
            //this.fall(vec3d.y, this.onGround, blockState, blockPos);
            Vec3d vec3d2 = this.velocity;
            if (movement.x != vec3d.x) {
                this.velocity = new Vec3d(0.0D, vec3d2.y, vec3d2.z);
            }

            if (movement.z != vec3d.z) {
                this.velocity = new Vec3d(vec3d2.x, vec3d2.y, 0.0D);
            }

            if (movement.y != vec3d.y) {
                // block.onLanded
                velocity = velocity.multiply(1.0D, 0.0D, 1.0D);
            }

            if (this.onGround) {
                // block.onSteppedOn
            }

            //this.checkBlockCollision(); // no interesting blocks

            float i = this.getVelocityMultiplier();
            this.velocity = this.velocity.multiply((double)i, 1.0D, (double)i);
            if (this.world.getStatesInBoxIfLoaded(this.boundingBox.contract(0.001D)).anyMatch((blockStatex) -> blockStatex.isIn(BlockTags.FIRE) || blockStatex.isOf(Blocks.LAVA))) {
                failedReason = "fire";
            }
        }

        private Vec3d adjustMovementForCollisions(Vec3d movement) {
            Box box = this.boundingBox;
            fakeEntity.updatePosition(pos.x, pos.y, pos.z);
            fakeEntity.setVelocity(velocity);
            assert world != null;

            VoxelShape voxelShape = this.world.getWorldBorder().asVoxelShape();
            List<VoxelShape> voxelShapes = new ArrayList<>();
            if (!VoxelShapes.matchesAnywhere(voxelShape, VoxelShapes.cuboid(box.contract(1.0E-7D)), BooleanBiFunction.AND)) {
                voxelShapes.add(voxelShape);
            }
            voxelShapes.addAll(this.world.getEntityCollisions(fakeEntity, box.stretch(movement)));

            return movement.lengthSquared() == 0.0D ? movement : Entity.adjustMovementForCollisions(fakeEntity, movement, box, this.world, voxelShapes);
        }

        private float getVelocityMultiplier() {
            assert world != null;
            Block block = this.world.getBlockState(new BlockPos(pos)).getBlock();
            float f = block.getVelocityMultiplier();
            if (block != Blocks.WATER && block != Blocks.BUBBLE_COLUMN) {
                return (double)f == 1.0D ? this.world.getBlockState(new BlockPos(this.pos.x, this.boundingBox.minY - 0.5000001D, this.pos.z)).getBlock().getVelocityMultiplier() : f;
            } else {
                return f;
            }
        }

        private boolean isOpenOrWaterAround(BlockPos pos) {
            PositionType positionType = PositionType.INVALID;

            boolean valid = true;

            for (int i = -1; i <= 2; ++i) {
                PositionType positionType2 = this.getPositionType(pos.add(-2, i, -2), pos.add(2, i, 2));
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
                            BlockPos pos2 = pos.add(dx, i, dz);
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
            return BlockPos.stream(start, end).map(this::getPositionType).reduce((positionType, positionType2) -> positionType == positionType2 ? positionType : PositionType.INVALID).orElse(PositionType.INVALID);
        }

        private PositionType getPositionType(BlockPos pos) {
            assert world != null;
            BlockState blockState = this.world.getBlockState(pos);
            if (!blockState.isAir() && !blockState.isOf(Blocks.LILY_PAD)) {
                FluidState fluidState = blockState.getFluidState();
                return fluidState.isIn(FluidTags.WATER) && fluidState.isStill() && blockState.getCollisionShape(this.world, pos).isEmpty() ? PositionType.INSIDE_WATER : PositionType.INVALID;
            } else {
                return PositionType.ABOVE_WATER;
            }
        }

        private void tickFishingLogic(BlockPos pos) {
            assert world != null;
            int i = 1;
            BlockPos blockPos = pos.up();
            if (this.random.nextFloat() < 0.25F && this.world.hasRain(blockPos)) {
                ++i;
            }

            if (this.random.nextFloat() < 0.5F && !this.world.isSkyVisible(blockPos)) {
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
                        this.fishAngle += random.nextTriangular(0, 9.188);
                        n = this.fishAngle * 0.017453292F;
                        o = MathHelper.sin(n);
                        p = MathHelper.cos(n);
                        q = this.pos.x + (double)(o * (float)this.fishTravelCountdown * 0.1F);
                        r = (double)((float)MathHelper.floor(this.pos.y) + 1.0F);
                        s = this.pos.z + (double)(p * (float)this.fishTravelCountdown * 0.1F);
                        blockState2 = world.getBlockState(new BlockPos(q, r - 1.0D, s));
                        if (blockState2.isOf(Blocks.WATER)) {
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
                        this.hookCountdown = MathHelper.nextInt(this.random, 20, 40);
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
                        o = MathHelper.nextFloat(this.random, 0.0F, 360.0F) * 0.017453292F;
                        p = MathHelper.nextFloat(this.random, 25.0F, 60.0F);
                        q = this.pos.x + (double)(MathHelper.sin(o) * p * 0.1F);
                        r = (double)((float)MathHelper.floor(this.pos.y) + 1.0F);
                        s = this.pos.z + (double)(MathHelper.cos(o) * p * 0.1F);
                        blockState2 = world.getBlockState(new BlockPos(q, r - 1.0D, s));
                        if (blockState2.isOf(Blocks.WATER)) {
                            random.nextInt(2);
                            //serverWorld.spawnParticles(ParticleTypes.SPLASH, q, r, s, 2 + this.random.nextInt(2), 0.10000000149011612D, 0.0D, 0.10000000149011612D, 0.0D);
                        }
                    }

                    if (this.waitCountdown <= 0) {
                        this.fishAngle = MathHelper.nextFloat(this.random, 0.0F, 360.0F);
                        this.fishTravelCountdown = MathHelper.nextInt(this.random, 20, 80);
                    }
                } else {
                    this.waitCountdown = MathHelper.nextInt(this.random, 100, 600);
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
