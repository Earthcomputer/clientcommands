package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

public enum LegacyEnchantment {
    PROTECTION(Enchantments.PROTECTION, 4, Weight.COMMON, MultiVersionCompat.V1_7_2, ItemTags.ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE, l -> 1 + (l - 1) * 11, l -> 1 + l * 11),
    FIRE_PROTECTION(Enchantments.FIRE_PROTECTION, 4, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, ItemTags.ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE, l -> 10 + (l - 1) * 8, l -> 10 + l * 8),
    FEATHER_FALLING(Enchantments.FEATHER_FALLING, 4, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, ItemTags.FOOT_ARMOR_ENCHANTABLE, ItemTags.FOOT_ARMOR_ENCHANTABLE, l -> 5 + (l - 1) * 6, l -> 5 + l * 6),
    BLAST_PROTECTION(Enchantments.BLAST_PROTECTION, 4, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE, l -> 5 + (l - 1) * 8, l -> 5 + l * 8),
    PROJECTILE_PROTECTION(Enchantments.PROJECTILE_PROTECTION, 4, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, ItemTags.ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE, l -> 3 + (l - 1) * 6, l -> 3 + l * 6),
    RESPIRATION(Enchantments.RESPIRATION, 3, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.HEAD_ARMOR_ENCHANTABLE, ItemTags.HEAD_ARMOR_ENCHANTABLE, l -> l * 10, l -> 30 + l * 10),
    AQUA_AFFINITY(Enchantments.AQUA_AFFINITY, 1, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.HEAD_ARMOR_ENCHANTABLE, ItemTags.HEAD_ARMOR_ENCHANTABLE, l -> 1, l -> 41),
    THORNS(Enchantments.THORNS, 3, Weight.VERY_RARE, MultiVersionCompat.V1_7_2, ItemTags.CHEST_ARMOR_ENCHANTABLE, ItemTags.ARMOR_ENCHANTABLE, l -> 10 + (l - 1) * 20, l -> 40 + l * 20),
    DEPTH_STRIDER(Enchantments.DEPTH_STRIDER, 3, Weight.RARE, MultiVersionCompat.V1_8, ItemTags.FOOT_ARMOR_ENCHANTABLE, ItemTags.FOOT_ARMOR_ENCHANTABLE, l -> l * 10, l -> 15 + l * 10),
    FROST_WALKER(Enchantments.FROST_WALKER, 2, Weight.RARE, MultiVersionCompat.V1_9, ItemTags.FOOT_ARMOR_ENCHANTABLE, ItemTags.FOOT_ARMOR_ENCHANTABLE, l -> l * 10, l -> 15 + l * 10, Flags.TREASURE),
    BINDING_CURSE(Enchantments.BINDING_CURSE, 1, Weight.VERY_RARE, MultiVersionCompat.V1_11, ItemTags.EQUIPPABLE_ENCHANTABLE, ItemTags.EQUIPPABLE_ENCHANTABLE, l -> 25, l -> 50, Flags.TREASURE),
    SOUL_SPEED(Enchantments.SOUL_SPEED, 3, Weight.VERY_RARE, MultiVersionCompat.V1_16, ItemTags.FOOT_ARMOR_ENCHANTABLE, ItemTags.FOOT_ARMOR_ENCHANTABLE, l -> l * 10, l -> 15 + l * 10, Flags.TREASURE, Flags.NON_DISCOVERABLE),
    SHARPNESS(Enchantments.SHARPNESS, 5, Weight.COMMON, MultiVersionCompat.V1_7_2, ItemTags.SWORD_ENCHANTABLE, ItemTags.SHARP_WEAPON_ENCHANTABLE, l -> 1 + (l - 1) * 11, l -> 21 + (l - 1) * 11),
    SMITE(Enchantments.SMITE, 5, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, ItemTags.SWORD_ENCHANTABLE, ItemTags.WEAPON_ENCHANTABLE, l -> 5 + (l - 1) * 8, l -> 25 + (l - 1) * 8),
    BANE_OF_ARTHROPODS(Enchantments.BANE_OF_ARTHROPODS, 5, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, ItemTags.SWORD_ENCHANTABLE, ItemTags.WEAPON_ENCHANTABLE, l -> 5 + (l - 1) * 8, l -> 25 + (l - 1) * 8),
    KNOCKBACK(Enchantments.KNOCKBACK, 2, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, ItemTags.SWORD_ENCHANTABLE, ItemTags.SWORD_ENCHANTABLE, l -> 5 + (l - 1) * 20, l -> 55 + (l - 1) * 20),
    FIRE_ASPECT(Enchantments.FIRE_ASPECT, 2, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.SWORD_ENCHANTABLE, ItemTags.FIRE_ASPECT_ENCHANTABLE, l -> 10 + (l - 1) * 20, l -> 40 + l * 20),
    LOOTING(Enchantments.LOOTING, 3, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.SWORD_ENCHANTABLE, ItemTags.SWORD_ENCHANTABLE, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9),
    SWEEPING_EDGE(Enchantments.SWEEPING_EDGE, 3, Weight.RARE, MultiVersionCompat.V1_11_1, ItemTags.SWORD_ENCHANTABLE, ItemTags.SWORD_ENCHANTABLE, l -> 5 + (l - 1) * 9, l -> 20 + (l - 1) * 9),
    EFFICIENCY(Enchantments.EFFICIENCY, 5, Weight.COMMON, MultiVersionCompat.V1_7_2, ItemTags.MINING_ENCHANTABLE, ItemTags.MINING_ENCHANTABLE, l -> 1 + (l - 1) * 10, l -> 50 + l * 10),
    SILK_TOUCH(Enchantments.SILK_TOUCH, 1, Weight.VERY_RARE, MultiVersionCompat.V1_7_2, ItemTags.MINING_LOOT_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE, l -> 15, l -> 65),
    UNBREAKING(Enchantments.UNBREAKING, 3, Weight.UNCOMMON, MultiVersionCompat.V1_7_2, ItemTags.DURABILITY_ENCHANTABLE, ItemTags.DURABILITY_ENCHANTABLE, l -> 5 + (l - 1) * 8, l -> 55 + (l - 1) * 8),
    FORTUNE(Enchantments.FORTUNE, 3, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.MINING_LOOT_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9),
    POWER(Enchantments.POWER, 5, Weight.COMMON, MultiVersionCompat.V1_7_2, ItemTags.BOW_ENCHANTABLE, ItemTags.BOW_ENCHANTABLE, l -> 1 + (l - 1) * 10, l -> 16 + (l - 1) * 10),
    PUNCH(Enchantments.PUNCH, 2, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.BOW_ENCHANTABLE, ItemTags.BOW_ENCHANTABLE, l -> 12 + (l - 1) * 20, l -> 37 + (l - 1) * 20),
    FLAME(Enchantments.FLAME, 1, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.BOW_ENCHANTABLE, ItemTags.BOW_ENCHANTABLE, l -> 20, l -> 50),
    INFINITY(Enchantments.INFINITY, 1, Weight.VERY_RARE, MultiVersionCompat.V1_7_2, ItemTags.BOW_ENCHANTABLE, ItemTags.BOW_ENCHANTABLE, l -> 20, l -> 50),
    LUCK_OF_THE_SEA(Enchantments.LUCK_OF_THE_SEA, 3, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.FISHING_ENCHANTABLE, ItemTags.FISHING_ENCHANTABLE, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9),
    LURE(Enchantments.LURE, 3, Weight.RARE, MultiVersionCompat.V1_7_2, ItemTags.FISHING_ENCHANTABLE, ItemTags.FISHING_ENCHANTABLE, l -> 15 + (l - 1) * 9, l -> 65 + (l - 1) * 9),
    LOYALTY(Enchantments.LOYALTY, 3, Weight.UNCOMMON, MultiVersionCompat.V1_13, ItemTags.TRIDENT_ENCHANTABLE, ItemTags.TRIDENT_ENCHANTABLE, l -> 5 + l * 7, l -> 50),
    IMPALING(Enchantments.IMPALING, 5, Weight.RARE, MultiVersionCompat.V1_13, ItemTags.TRIDENT_ENCHANTABLE, ItemTags.TRIDENT_ENCHANTABLE, l -> 1 + (l - 1) * 8, l -> 21 + (l - 1) * 8),
    RIPTIDE(Enchantments.RIPTIDE, 3, Weight.RARE, MultiVersionCompat.V1_13, ItemTags.TRIDENT_ENCHANTABLE, ItemTags.TRIDENT_ENCHANTABLE, l -> 10 + l * 7, l -> 50),
    CHANNELING(Enchantments.CHANNELING, 1, Weight.VERY_RARE, MultiVersionCompat.V1_13, ItemTags.TRIDENT_ENCHANTABLE, ItemTags.TRIDENT_ENCHANTABLE, l -> 25, l -> 50),
    MULTISHOT(Enchantments.MULTISHOT, 1, Weight.RARE, MultiVersionCompat.V1_14, ItemTags.CROSSBOW_ENCHANTABLE, ItemTags.CROSSBOW_ENCHANTABLE, l -> 20, l -> 50),
    QUICK_CHARGE(Enchantments.QUICK_CHARGE, 3, Weight.UNCOMMON, MultiVersionCompat.V1_14, ItemTags.CROSSBOW_ENCHANTABLE, ItemTags.CROSSBOW_ENCHANTABLE, l -> 12 + (l - 1) * 20, l -> 50),
    PIERCING(Enchantments.PIERCING, 4, Weight.COMMON, MultiVersionCompat.V1_14, ItemTags.CROSSBOW_ENCHANTABLE, ItemTags.CROSSBOW_ENCHANTABLE, l -> 1 + (l - 1) * 10, l -> 50),
    MENDING(Enchantments.MENDING, 1, Weight.RARE, MultiVersionCompat.V1_9, ItemTags.DURABILITY_ENCHANTABLE, ItemTags.DURABILITY_ENCHANTABLE, l -> 25, l -> 75, Flags.TREASURE),
    VANISHING_CURSE(Enchantments.VANISHING_CURSE, 1, Weight.VERY_RARE, MultiVersionCompat.V1_11, ItemTags.VANISHING_ENCHANTABLE, ItemTags.VANISHING_ENCHANTABLE, l -> 25, l -> 50, Flags.TREASURE),
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
        map.put(SILK_TOUCH, new ExclusiveSet(EnumSet.of(LOOTING, FORTUNE, LUCK_OF_THE_SEA), null));
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

    public final ResourceKey<Enchantment> enchantmentKey;
    public final Component displayName;
    public final int maxLevel;
    private final Weight weight;
    public final int introducedVersion;
    public final TagKey<Item> primaryItems;
    public final TagKey<Item> supportedItems;
    private final IntUnaryOperator minEnchantability;
    private final IntUnaryOperator maxEnchantability;
    @MagicConstant(flagsFromClass = Flags.class)
    private final int flags;

    LegacyEnchantment(
        ResourceKey<Enchantment> enchantmentKey,
        int maxLevel,
        Weight weight,
        int introducedVersion,
        TagKey<Item> primaryItems,
        TagKey<Item> supportedItems,
        IntUnaryOperator minEnchantability,
        IntUnaryOperator maxEnchantability,
        @MagicConstant(flagsFromClass = Flags.class) int... flags
    ) {
        this.enchantmentKey = enchantmentKey;
        this.displayName = Component.translatable(enchantmentKey.location().toLanguageKey("enchantment"));
        this.maxLevel = maxLevel;
        this.weight = weight;
        this.introducedVersion = introducedVersion;
        this.primaryItems = primaryItems;
        this.supportedItems = supportedItems;
        this.minEnchantability = minEnchantability;
        this.maxEnchantability = maxEnchantability;

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
        ExclusiveSet set = EXCLUSIVE_SETS.get(this);
        if (set == null) {
            return true;
        }
        if (set.ignoreOnVersions != null && set.ignoreOnVersions.matches(version)) {
            return true;
        }
        return !set.set.contains(other);
    }

    private static List<Instance> getHighestAllowedEnchantments(int level, ItemStack item, boolean treasure, int version) {
        List<Instance> allowedEnchantments = new ArrayList<>();

        for (LegacyEnchantment enchantment : VALUES) {
            if (version < enchantment.introducedVersion) {
                continue;
            }

            if ((treasure || !enchantment.isTreasure()) && item.is(enchantment.primaryItems)) {
                for (int enchLevel = enchantment.maxLevel; enchLevel >= 1; enchLevel--) {
                    if (level >= enchantment.minEnchantability.applyAsInt(enchLevel) && level <= enchantment.maxEnchantability.applyAsInt(enchLevel)) {
                        allowedEnchantments.add(new Instance(enchantment, enchLevel));
                        break;
                    }
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

        // Modify the enchantment level randomly and according to enchantability
        level = level + 1 + rand.nextInt(enchantability / 4 + 1) + rand.nextInt(enchantability / 4 + 1);
        float percentChange = (rand.nextFloat() + rand.nextFloat() - 1) * 0.15f;
        level += Math.round(level * percentChange);
        if (level < 1) {
            level = 1;
        }

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

    private record ExclusiveSet(EnumSet<LegacyEnchantment> set, @Nullable MinMaxBounds.Ints ignoreOnVersions) {
    }

    private static class Flags {
        static final int TREASURE = 1;
        static final int NON_DISCOVERABLE = 2;
    }

    public record Instance(LegacyEnchantment ench, int level) {
    }
}
