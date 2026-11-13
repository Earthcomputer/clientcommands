package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import org.intellij.lang.annotations.MagicConstant;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

public enum LegacyEnchantment {
    PROTECTION(Enchantments.PROTECTION, 0, 4, Weight.COMMON, MultiVersionCompat.V1_7_2, Category.ARMOR, l -> 1 + (l - 1) * 11, l -> 1 + l * 11, null),
    FIRE_PROTECTION(Enchantments.FIRE_PROTECTION, 1, 4, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, Category.ARMOR, l -> 10 + (l - 1) * 8, l -> 10 + l * 8, null),
    FEATHER_FALLING(Enchantments.FEATHER_FALLING, 2, 4, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, Category.ARMOR_FEET, l -> 5 + (l - 1) * 6, l -> 5 + l * 6, null),
    BLAST_PROTECTION(Enchantments.BLAST_PROTECTION, 3, 4, Weight.RARE, MultiVersionCompat.V1_7_2, Category.ARMOR, l -> 5 + (l - 1) * 8, l -> 5 + l * 8, null),
    PROJECTILE_PROTECTION(Enchantments.PROJECTILE_PROTECTION, 4, 4, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, Category.ARMOR, l -> 3 + (l - 1) * 6, l -> 3 + l * 6, null),
    RESPIRATION(Enchantments.RESPIRATION, 5, 3, Weight.RARE, MultiVersionCompat.V1_7_2, Category.ARMOR_HEAD, l -> l * 10, l -> 30 + l * 10, null),
    AQUA_AFFINITY(Enchantments.AQUA_AFFINITY, 6, 1, Weight.RARE, MultiVersionCompat.V1_7_2, Category.ARMOR_HEAD, l -> 1, l -> 41, null),
    THORNS(Enchantments.THORNS, 7, 3, Weight.VERY_RARE, MultiVersionCompat.V1_7_2, Category.ARMOR_CHEST, l -> 10 + (l - 1) * 20, l -> 40 + l * 20, Costs.SUPER_MIN_PLUS_50),
    DEPTH_STRIDER(Enchantments.DEPTH_STRIDER, 8, 3, Weight.RARE, MultiVersionCompat.V1_8, Category.ARMOR_FEET, l -> l * 10, l -> 15 + l * 10, null),
    FROST_WALKER(Enchantments.FROST_WALKER, 9, 2, Weight.RARE, MultiVersionCompat.V1_9, Category.ARMOR_FEET, l -> l * 10, l -> 15 + l * 10, null, Flags.TREASURE),
    BINDING_CURSE(Enchantments.BINDING_CURSE, 10, 1, Weight.VERY_RARE, MultiVersionCompat.V1_11, Category.WEARABLE, l -> 25, l -> 50, null, Flags.TREASURE),
    SOUL_SPEED(Enchantments.SOUL_SPEED, -1, 3, Weight.VERY_RARE, MultiVersionCompat.V1_16, Category.ARMOR_FEET, l -> l * 10, l -> 15 + l * 10, null, Flags.TREASURE, Flags.NON_DISCOVERABLE),
    SHARPNESS(Enchantments.SHARPNESS, 16, 5, Weight.COMMON, MultiVersionCompat.V1_7_2, Category.WEAPON, l -> 1 + (l - 1) * 11, l -> 21 + (l - 1) * 11, null),
    SMITE(Enchantments.SMITE, 17, 5, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, Category.WEAPON, l -> 5 + (l - 1) * 8, l -> 25 + (l - 1) * 8, null),
    BANE_OF_ARTHROPODS(Enchantments.BANE_OF_ARTHROPODS, 18, 5, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, Category.WEAPON, l -> 5 + (l - 1) * 8, l -> 25 + (l - 1) * 8, null),
    KNOCKBACK(Enchantments.KNOCKBACK, 19, 2, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, Category.WEAPON, l -> 5 + (l - 1) * 20, l -> 55 + (l - 1) * 20, Costs.SUPER_MIN_PLUS_50),
    FIRE_ASPECT(Enchantments.FIRE_ASPECT, 20, 2, Weight.RARE, MultiVersionCompat.V1_7_2, Category.WEAPON, l -> 10 + (l - 1) * 20, l -> 40 + l * 20, Costs.SUPER_MIN_PLUS_50),
    LOOTING(Enchantments.LOOTING, 21, 3, Weight.RARE, MultiVersionCompat.V1_7_2, Category.WEAPON, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9, Costs.SUPER_MIN_PLUS_50),
    SWEEPING_EDGE(Enchantments.SWEEPING_EDGE, 22, 3, Weight.RARE, MultiVersionCompat.V1_11_1, Category.WEAPON, l -> 5 + (l - 1) * 9, l -> 20 + (l - 1) * 9, null),
    EFFICIENCY(Enchantments.EFFICIENCY, 32, 5, Weight.COMMON, MultiVersionCompat.V1_7_2, Category.DIGGER, l -> 1 + (l - 1) * 10, l -> 51 + (l - 1) * 10, Costs.SUPER_MIN_PLUS_50),
    SILK_TOUCH(Enchantments.SILK_TOUCH, 33, 1, Weight.VERY_RARE, MultiVersionCompat.V1_7_2, Category.DIGGER, l -> 15, l -> 65, Costs.SUPER_MIN_PLUS_50),
    UNBREAKING(Enchantments.UNBREAKING, 34, 3, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, Category.BREAKABLE, l -> 5 + (l - 1) * 8, l -> 55 + (l - 1) * 8, Costs.SUPER_MIN_PLUS_50),
    FORTUNE(Enchantments.FORTUNE, 35, 3, Weight.RARE, MultiVersionCompat.V1_7_2, Category.DIGGER, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9, Costs.SUPER_MIN_PLUS_50),
    POWER(Enchantments.POWER, 48, 5, Weight.COMMON, MultiVersionCompat.V1_7_2, Category.BOW, l -> 1 + (l - 1) * 10, l -> 16 + (l - 1) * 10, null),
    PUNCH(Enchantments.PUNCH, 49, 2, Weight.RARE, MultiVersionCompat.V1_7_2, Category.BOW, l -> 12 + (l - 1) * 20, l -> 37 + (l - 1) * 20, null),
    FLAME(Enchantments.FLAME, 50, 1, Weight.RARE, MultiVersionCompat.V1_7_2, Category.BOW, l -> 20, l -> 50, null),
    INFINITY(Enchantments.INFINITY, 51, 1, Weight.VERY_RARE, MultiVersionCompat.V1_7_2, Category.BOW, l -> 20, l -> 50, null),
    LUCK_OF_THE_SEA(Enchantments.LUCK_OF_THE_SEA, 61, 3, Weight.RARE, MultiVersionCompat.V1_7_2, Category.FISHING_ROD, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9, Costs.SUPER_MIN_PLUS_50),
    LURE(Enchantments.LURE, 62, 3, Weight.RARE, MultiVersionCompat.V1_7_2, Category.FISHING_ROD, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9, Costs.SUPER_MIN_PLUS_50),
    LOYALTY(Enchantments.LOYALTY, -1, 3, Weight.UNCOMMON, MultiVersionCompat.V1_13, Category.TRIDENT, l -> 5 + l * 7, l -> 50, null),
    IMPALING(Enchantments.IMPALING, -1, 5, Weight.RARE, MultiVersionCompat.V1_13, Category.TRIDENT, l -> 1 + (l - 1) * 8, l -> 21 + (l - 1) * 8, null),
    RIPTIDE(Enchantments.RIPTIDE, -1, 3, Weight.RARE, MultiVersionCompat.V1_13, Category.TRIDENT, l -> 10 + l * 7, l -> 50, null),
    CHANNELING(Enchantments.CHANNELING, -1, 1, Weight.VERY_RARE, MultiVersionCompat.V1_13, Category.TRIDENT, l -> 25, l -> 50, null),
    MULTISHOT(Enchantments.MULTISHOT, -1, 1, Weight.RARE, MultiVersionCompat.V1_14, Category.CROSSBOW, l -> 20, l -> 50, null),
    QUICK_CHARGE(Enchantments.QUICK_CHARGE, -1, 3, Weight.UNCOMMON, MultiVersionCompat.V1_14, Category.CROSSBOW, l -> 12 + (l - 1) * 20, l -> 50, null),
    PIERCING(Enchantments.PIERCING, -1, 4, Weight.COMMON, MultiVersionCompat.V1_14, Category.CROSSBOW, l -> 1 + (l - 1) * 10, l -> 50, null),
    MENDING(Enchantments.MENDING, 70, 1, Weight.RARE, MultiVersionCompat.V1_9, Category.BREAKABLE, l -> 25, l -> 75, null, Flags.TREASURE),
    VANISHING_CURSE(Enchantments.VANISHING_CURSE, 71, 1, Weight.VERY_RARE, MultiVersionCompat.V1_11, Category.VANISHABLE, l -> 25, l -> 50, null, Flags.TREASURE),
    ;

    private static final LegacyEnchantment[] VALUES = values();
    private static final Map<ResourceKey<Enchantment>, LegacyEnchantment> BY_KEY = Util.make(new HashMap<>(VALUES.length), map -> {
        for (LegacyEnchantment ench : VALUES) {
            map.put(ench.enchantmentKey, ench);
        }
    });

    private static final EnumMap<LegacyEnchantment, ExclusiveSet> EXCLUSIVE_SETS = Util.make(new EnumMap<>(LegacyEnchantment.class), map -> {
        map.put(INFINITY, new ExclusiveSet(EnumSet.of(MENDING), MinMaxBounds.Ints.atMost(MultiVersionCompat.V1_11)));
        map.put(MENDING, new ExclusiveSet(EnumSet.of(INFINITY), MinMaxBounds.Ints.atMost(MultiVersionCompat.V1_11)));
        map.put(SHARPNESS, new ExclusiveSet(EnumSet.of(SMITE, BANE_OF_ARTHROPODS), null));
        map.put(SMITE, new ExclusiveSet(EnumSet.of(SHARPNESS, BANE_OF_ARTHROPODS), null));
        map.put(BANE_OF_ARTHROPODS, new ExclusiveSet(EnumSet.of(SHARPNESS, SMITE), null));
        map.put(DEPTH_STRIDER, new ExclusiveSet(EnumSet.of(FROST_WALKER), null));
        map.put(FROST_WALKER, new ExclusiveSet(EnumSet.of(DEPTH_STRIDER), null));
        // looting and luck of the sea exclude silk touch, but not the other way round, which matters in 1.8
        map.put(SILK_TOUCH, new ExclusiveSet(EnumSet.of(FORTUNE), null));
        map.put(LOOTING, new ExclusiveSet(EnumSet.of(SILK_TOUCH), null));
        map.put(FORTUNE, new ExclusiveSet(EnumSet.of(SILK_TOUCH), null));
        map.put(LUCK_OF_THE_SEA, new ExclusiveSet(EnumSet.of(SILK_TOUCH), null));
        map.put(RIPTIDE, new ExclusiveSet(EnumSet.of(LOYALTY, CHANNELING), null));
        map.put(LOYALTY, new ExclusiveSet(EnumSet.of(RIPTIDE), null));
        map.put(CHANNELING, new ExclusiveSet(EnumSet.of(RIPTIDE), null));
        map.put(MULTISHOT, new ExclusiveSet(EnumSet.of(PIERCING), null));
        map.put(PIERCING, new ExclusiveSet(EnumSet.of(MULTISHOT), null));
        map.put(PROTECTION, new ExclusiveSet(EnumSet.of(BLAST_PROTECTION, FIRE_PROTECTION, PROJECTILE_PROTECTION), MinMaxBounds.Ints.between(MultiVersionCompat.V1_14, MultiVersionCompat.V1_14_2)));
        map.put(BLAST_PROTECTION, new ExclusiveSet(EnumSet.of(PROTECTION, FIRE_PROTECTION, PROJECTILE_PROTECTION), MinMaxBounds.Ints.between(MultiVersionCompat.V1_14, MultiVersionCompat.V1_14_2)));
        map.put(FIRE_PROTECTION, new ExclusiveSet(EnumSet.of(PROTECTION, BLAST_PROTECTION, PROJECTILE_PROTECTION), MinMaxBounds.Ints.between(MultiVersionCompat.V1_14, MultiVersionCompat.V1_14_2)));
        map.put(PROJECTILE_PROTECTION, new ExclusiveSet(EnumSet.of(PROTECTION, BLAST_PROTECTION, FIRE_PROTECTION), MinMaxBounds.Ints.between(MultiVersionCompat.V1_14, MultiVersionCompat.V1_14_2)));
    });

    // In 1.8, the max enchantability of the protection enchantments didn't depend on their per level increase
    private static final EnumMap<LegacyEnchantment, IntUnaryOperator> MAX_ENCHANTABILITY_1_8 = Util.make(new EnumMap<>(LegacyEnchantment.class), map -> {
        map.put(PROTECTION, l -> 1 + (l - 1) * 11 + 20);
        map.put(FIRE_PROTECTION, l -> 10 + (l - 1) * 8 + 12);
        map.put(FEATHER_FALLING, l -> 5 + (l - 1) * 6 + 10);
        map.put(BLAST_PROTECTION, l -> 5 + (l - 1) * 8 + 12);
        map.put(PROJECTILE_PROTECTION, l -> 3 + (l - 1) * 6 + 15);
    });

    public final ResourceKey<Enchantment> enchantmentKey;
    // the numeric ID used before 1.13, or -1 if the enchantment was added later
    private final int numericId;
    public final Component displayName;
    public final int maxLevel;
    private final Weight weight;
    public final int introducedVersion;
    private final Category category;
    private final IntUnaryOperator minEnchantability;
    private final IntUnaryOperator maxEnchantability;
    // the max enchantability before 1.20.5, if it was different
    private final @Nullable IntUnaryOperator oldMaxEnchantability;
    @MagicConstant(flagsFromClass = Flags.class)
    private final int flags;

    LegacyEnchantment(
        ResourceKey<Enchantment> enchantmentKey,
        int numericId,
        int maxLevel,
        Weight weight,
        int introducedVersion,
        Category category,
        IntUnaryOperator minEnchantability,
        IntUnaryOperator maxEnchantability,
        @Nullable IntUnaryOperator oldMaxEnchantability,
        @MagicConstant(flagsFromClass = Flags.class) int... flags
    ) {
        this.enchantmentKey = enchantmentKey;
        this.numericId = numericId;
        this.displayName = Component.translatable(enchantmentKey.identifier().toLanguageKey("enchantment"));
        this.maxLevel = maxLevel;
        this.weight = weight;
        this.introducedVersion = introducedVersion;
        this.category = category;
        this.minEnchantability = minEnchantability;
        this.maxEnchantability = maxEnchantability;
        this.oldMaxEnchantability = oldMaxEnchantability;

        int combinedFlags = 0;
        for (int flag : flags) {
            combinedFlags |= flag;
        }
        this.flags = combinedFlags;
    }

    @Nullable
    public static LegacyEnchantment byEnchantmentKey(ResourceKey<Enchantment> enchantmentKey) {
        return BY_KEY.get(enchantmentKey);
    }

    public boolean isTreasure() {
        return (flags & Flags.TREASURE) != 0;
    }

    public boolean isDiscoverable() {
        return (flags & Flags.NON_DISCOVERABLE) == 0;
    }

    public boolean inEnchantmentTable() {
        return !isTreasure() && isDiscoverable();
    }

    public boolean isCompatible(LegacyEnchantment other, int version) {
        return this.canApplyTogether(other, version) && other.canApplyTogether(this, version);
    }

    // Only checks this enchantment's side. 1.8 only checks the side of the enchantments that were already chosen
    private boolean canApplyTogether(LegacyEnchantment other, int version) {
        if (this == other) {
            return false;
        }
        ExclusiveSet set = EXCLUSIVE_SETS.get(this);
        if (set == null) {
            return true;
        }
        if (set.ignoreOnVersions != null && set.ignoreOnVersions.matches(version)) {
            return true;
        }
        return !set.set.contains(other);
    }

    private int getMaxEnchantability(int level, int version) {
        if (version <= MultiVersionCompat.V1_8 && MAX_ENCHANTABILITY_1_8.containsKey(this)) {
            return MAX_ENCHANTABILITY_1_8.get(this).applyAsInt(level);
        }
        if (oldMaxEnchantability != null && version < MultiVersionCompat.V1_20_5) {
            return oldMaxEnchantability.applyAsInt(level);
        }
        return maxEnchantability.applyAsInt(level);
    }

    private boolean canEnchant(ItemStack item, boolean treasure, int version) {
        // non-discoverable enchantments are never randomly selected, even as treasure
        if (version < introducedVersion || (isTreasure() && !treasure) || !isDiscoverable()) {
            return false;
        }
        // books can have all enchantments
        return item.is(Items.BOOK) || category.canEnchant(item, version);
    }

    // The highest level of the enchantment allowed at the given enchantment level, or 0 if none is allowed
    private int getHighestAllowedLevel(int level, int version) {
        // 1.14 enchantment nerf: there was no max enchantability
        boolean checkMax = version < MultiVersionCompat.V1_14 || version > MultiVersionCompat.V1_14_2;
        for (int enchLevel = maxLevel; enchLevel >= 1; enchLevel--) {
            if (level >= minEnchantability.applyAsInt(enchLevel) && (!checkMax || level <= getMaxEnchantability(enchLevel, version))) {
                return enchLevel;
            }
        }
        return 0;
    }

    private static List<Instance> getHighestAllowedEnchantments(int level, ItemStack item, boolean treasure, int version) {
        List<Instance> allowedEnchantments = new ArrayList<>();

        for (LegacyEnchantment enchantment : VALUES) {
            if (enchantment.canEnchant(item, treasure, version)) {
                int enchLevel = enchantment.getHighestAllowedLevel(level, version);
                if (enchLevel > 0) {
                    allowedEnchantments.add(new Instance(enchantment, enchLevel));
                }
            }
        }

        return allowedEnchantments;
    }

    public static List<Instance> addRandomEnchantments(RandomSource rand, ItemStack item, int level, boolean treasure, int version) {
        Enchantable enchantable = item.get(DataComponents.ENCHANTABLE);
        int enchantability = enchantable == null ? 0 : enchantable.value();
        List<Instance> enchantments = new ArrayList<>();

        if (enchantability <= 0) {
            return enchantments;
        }

        if (version <= MultiVersionCompat.V1_8) {
            return addRandomEnchantments1_8(rand, item, level, enchantability, version);
        }

        // Modify the enchantment level randomly and according to enchantability
        level = level + 1 + rand.nextInt(enchantability / 4 + 1) + rand.nextInt(enchantability / 4 + 1);
        float percentChange = (rand.nextFloat() + rand.nextFloat() - 1) * 0.15f;
        level = Math.max(Math.round((float) level + (float) level * percentChange), 1);

        // Get a list of allowed enchantments with their max allowed levels
        List<Instance> allowedEnchantments = getHighestAllowedEnchantments(level, item, treasure, version);
        if (allowedEnchantments.isEmpty()) {
            return enchantments;
        }

        // Get first enchantment
        Instance enchantmentInstance = getRandomEnchantment(rand, allowedEnchantments, version);
        if (enchantmentInstance == null) {
            return enchantments;
        }
        enchantments.add(enchantmentInstance);

        // Get optional extra enchantments
        while (rand.nextInt(50) <= level) {
            // 1.14 enchantment nerf
            if (version >= MultiVersionCompat.V1_14 && version <= MultiVersionCompat.V1_14_2) {
                level = level * 4 / 5 + 1;
                allowedEnchantments = getHighestAllowedEnchantments(level, item, treasure, version);

                // Remove incompatible enchantments from allowed list
                for (Instance ench : enchantments) {
                    allowedEnchantments.removeIf(it -> !it.ench.isCompatible(ench.ench, version));
                }
            } else {
                // Remove incompatible enchantments from allowed list with last enchantment
                allowedEnchantments.removeIf(it -> !it.ench.isCompatible(enchantments.getLast().ench, version));
            }

            if (allowedEnchantments.isEmpty()) {
                // no enchantments left
                break;
            }

            // Get extra enchantment
            enchantmentInstance = getRandomEnchantment(rand, allowedEnchantments, version);
            if (enchantmentInstance == null) {
                break;
            }
            enchantments.add(enchantmentInstance);

            // Make it less likely for another enchantment to happen
            level /= 2;
        }

        return enchantments;
    }

    private static List<Instance> addRandomEnchantments1_8(RandomSource rand, ItemStack item, int level, int enchantability, int version) {
        List<Instance> enchantments = new ArrayList<>();

        // Modify the enchantment level randomly and according to enchantability
        enchantability /= 2;
        level += 1 + rand.nextInt((enchantability >> 1) + 1) + rand.nextInt((enchantability >> 1) + 1);
        float percentChange = (rand.nextFloat() + rand.nextFloat() - 1) * 0.15f;
        level = Math.max((int) (level * (1 + percentChange) + 0.5f), 1);

        // VALUES is in numeric ID order for the enchantments that exist in 1.8
        List<Instance> allowedEnchantments = new ArrayList<>();
        for (LegacyEnchantment enchantment : VALUES) {
            if (enchantment.canEnchant(item, false, version)) {
                int enchLevel = enchantment.getHighestAllowedLevel(level, version);
                if (enchLevel > 0) {
                    allowedEnchantments.add(new Instance(enchantment, enchLevel));
                }
            }
        }
        if (allowedEnchantments.isEmpty()) {
            return enchantments;
        }
        allowedEnchantments = inHashMapIterationOrder(allowedEnchantments);

        enchantments.add(getRandomEnchantment(rand, allowedEnchantments, version));

        // unlike later versions, 1.8 keeps going when there are no enchantments left, and removes the enchantments that
        // are incompatible with any of the enchantments chosen so far
        for (int extraLevel = level; rand.nextInt(50) <= extraLevel; extraLevel >>= 1) {
            allowedEnchantments.removeIf(allowed -> enchantments.stream().anyMatch(chosen -> !chosen.ench.canApplyTogether(allowed.ench, version)));
            if (!allowedEnchantments.isEmpty()) {
                enchantments.add(getRandomEnchantment(rand, allowedEnchantments, version));
            }
        }

        return enchantments;
    }

    /**
     * 1.8 picks enchantments from the values of a {@code HashMap<Integer, EnchantmentData>} keyed by numeric ID, which
     * were inserted in ID order. This simulates the iteration order of that map, rather than using {@link HashMap}
     * directly, so that it doesn't change if {@link HashMap} changes in future versions of Java. This assumes the server
     * runs on Java 8 or later, Java 7 had a different hash function and inserted at the start of buckets.
     *
     * <p>In Java 8, an entry is put at the end of bucket {@code (h ^ (h >>> 16)) & (capacity - 1)}, where h is the
     * key's hash code, which simplifies to {@code id & (capacity - 1)} because the IDs are small. Buckets never get big
     * enough to be treeified, as at most 4 IDs fall in the same bucket. The map does get resized, as books can have
     * all 25 enchantments, but resizing keeps the order within buckets, so it's equivalent to putting the entries
     * straight into a map of the final capacity. Removing entries never shrinks the map, so the remaining entries
     * stay in the same order.
     */
    private static List<Instance> inHashMapIterationOrder(List<Instance> instancesInIdOrder) {
        // the capacity starts at 16 and doubles whenever the size goes over 3/4 of the capacity
        int capacity = 16;
        while (instancesInIdOrder.size() > capacity * 3 / 4) {
            capacity *= 2;
        }

        @SuppressWarnings("unchecked")
        @Nullable List<Instance>[] buckets = new List[capacity];
        for (Instance instance : instancesInIdOrder) {
            int bucket = instance.ench.numericId & (capacity - 1);
            List<Instance> b = buckets[bucket];
            if (b == null) {
                b = buckets[bucket] = new ArrayList<>();
            }
            b.add(instance);
        }

        List<Instance> result = new ArrayList<>(instancesInIdOrder.size());
        for (List<Instance> bucket : buckets) {
            if (bucket != null) {
                result.addAll(bucket);
            }
        }
        return result;
    }

    @Nullable
    private static Instance getRandomEnchantment(RandomSource rand, List<Instance> list, int version) {
        int weight = list.stream().mapToInt(ench -> ench.ench.weight.getValue(version)).sum();
        if (weight <= 0) {
            return null;
        }
        weight = rand.nextInt(weight);
        for (Instance ench : list) {
            weight -= ench.ench.weight.getValue(version);
            if (weight < 0) {
                return ench;
            }
        }
        return null;
    }

    private enum Weight {
        COMMON(10, 30),
        UNCOMMON(5, 10),
        RARE(2, 3),
        VERY_RARE(1, 1),
        ;

        private final int value;
        private final int value114;

        Weight(int value, int value114) {
            this.value = value;
            this.value114 = value114;
        }

        int getValue(int version) {
            return version >= MultiVersionCompat.V1_14 && version <= MultiVersionCompat.V1_14_2 ? value114 : value;
        }
    }

    private enum Category {
        ARMOR {
            @Override
            boolean canEnchant(ItemStack item, int version) {
                return ARMOR_FEET.canEnchant(item, version) || ARMOR_LEGS.canEnchant(item, version) || ARMOR_CHEST.canEnchant(item, version) || ARMOR_HEAD.canEnchant(item, version);
            }
        },
        ARMOR_FEET(EquipmentSlot.FEET),
        ARMOR_LEGS(EquipmentSlot.LEGS),
        ARMOR_CHEST(EquipmentSlot.CHEST),
        ARMOR_HEAD(EquipmentSlot.HEAD),
        WEAPON(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD),
        DIGGER(
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
            Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL
        ) {
            private static final Set<Item> HOES = Set.of(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);

            @Override
            boolean canEnchant(ItemStack item, int version) {
                // hoes became digger items in 1.16
                return super.canEnchant(item, version) || (version >= MultiVersionCompat.V1_16 && HOES.contains(item.getItem()));
            }
        },
        FISHING_ROD(Items.FISHING_ROD),
        TRIDENT(Items.TRIDENT),
        BREAKABLE {
            @Override
            boolean canEnchant(ItemStack item, int version) {
                return item.isDamageableItem();
            }
        },
        BOW(Items.BOW),
        WEARABLE {
            @Override
            boolean canEnchant(ItemStack item, int version) {
                return item.has(DataComponents.EQUIPPABLE);
            }
        },
        CROSSBOW(Items.CROSSBOW),
        VANISHABLE {
            @Override
            boolean canEnchant(ItemStack item, int version) {
                return item.isDamageableItem() || item.has(DataComponents.EQUIPPABLE);
            }
        },
        ;

        private final Set<Item> items;
        private final @Nullable EquipmentSlot armorSlot;

        Category(Item... items) {
            this.items = Set.of(items);
            this.armorSlot = null;
        }

        Category(EquipmentSlot armorSlot) {
            this.items = Set.of();
            this.armorSlot = armorSlot;
        }

        // Before 1.20.5, the categories were based on the item's class, rather than on item tags
        boolean canEnchant(ItemStack item, int version) {
            if (armorSlot != null) {
                Equippable equippable = item.get(DataComponents.EQUIPPABLE);
                return equippable != null && equippable.slot() == armorSlot;
            }
            return items.contains(item.getItem());
        }
    }

    private static class Costs {
        // Many enchantments had max enchantability super.getMinCost(level) + 50 before 1.20.5
        static final IntUnaryOperator SUPER_MIN_PLUS_50 = l -> 1 + l * 10 + 50;
    }

    private record ExclusiveSet(EnumSet<LegacyEnchantment> set, MinMaxBounds.@Nullable Ints ignoreOnVersions) {
    }

    private static class Flags {
        static final int TREASURE = 1;
        static final int NON_DISCOVERABLE = 2;
    }

    public record Instance(LegacyEnchantment ench, int level) {
    }
}
