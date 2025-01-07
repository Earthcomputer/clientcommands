package net.earthcomputer.clientcommands.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mccore.version.MCVersion;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SeedfindingUtil {
    private static final BiMap<ResourceKey<Enchantment>, String> SEEDFINDING_ENCHANTMENTS = Util.make(HashBiMap.create(), map -> {
        map.put(Enchantments.PROTECTION, "protection");
        map.put(Enchantments.FIRE_PROTECTION, "fire_protection");
        map.put(Enchantments.FEATHER_FALLING, "feather_falling");
        map.put(Enchantments.BLAST_PROTECTION, "blast_protection");
        map.put(Enchantments.PROJECTILE_PROTECTION, "projectile_protection");
        map.put(Enchantments.RESPIRATION, "respiration");
        map.put(Enchantments.AQUA_AFFINITY, "aqua_affinity");
        map.put(Enchantments.THORNS, "thorns");
        map.put(Enchantments.DEPTH_STRIDER, "depth_strider");
        map.put(Enchantments.FROST_WALKER, "frost_walker");
        map.put(Enchantments.BINDING_CURSE, "binding_curse");
        map.put(Enchantments.SOUL_SPEED, "soul_speed");
        map.put(Enchantments.SHARPNESS, "sharpness");
        map.put(Enchantments.SMITE, "smite");
        map.put(Enchantments.BANE_OF_ARTHROPODS, "bane_of_arthropods");
        map.put(Enchantments.KNOCKBACK, "knockback");
        map.put(Enchantments.FIRE_ASPECT, "fire_aspect");
        map.put(Enchantments.LOOTING, "looting");
        map.put(Enchantments.SWEEPING_EDGE, "sweeping");
        map.put(Enchantments.EFFICIENCY, "efficiency");
        map.put(Enchantments.SILK_TOUCH, "silk_touch");
        map.put(Enchantments.UNBREAKING, "unbreaking");
        map.put(Enchantments.FORTUNE, "fortune");
        map.put(Enchantments.POWER, "power");
        map.put(Enchantments.PUNCH, "punch");
        map.put(Enchantments.FLAME, "flame");
        map.put(Enchantments.INFINITY, "infinity");
        map.put(Enchantments.LUCK_OF_THE_SEA, "luck_of_the_sea");
        map.put(Enchantments.LURE, "lure");
        map.put(Enchantments.LOYALTY, "loyalty");
        map.put(Enchantments.IMPALING, "impaling");
        map.put(Enchantments.RIPTIDE, "riptide");
        map.put(Enchantments.CHANNELING, "channeling");
        map.put(Enchantments.MULTISHOT, "multishot");
        map.put(Enchantments.QUICK_CHARGE, "quick_charge");
        map.put(Enchantments.PIERCING, "piercing");
        map.put(Enchantments.MENDING, "mending");
        map.put(Enchantments.VANISHING_CURSE, "vanishing_curse");
    });

    private SeedfindingUtil() {
    }

    @Nullable
    public static com.seedfinding.mcbiome.biome.Biome toSeedfindingBiome(Level level, Holder<Biome> biome) {
        ResourceLocation name = level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(biome.value());
        if (name == null || !"minecraft".equals(name.getNamespace())) {
            return null;
        }
        for (var b : Biomes.REGISTRY.values()) {
            if (name.getPath().equals(b.getName())) {
                return b;
            }
        }
        return null;
    }

    public static ItemStack fromSeedfindingItem(com.seedfinding.mcfeature.loot.item.Item item, RegistryAccess registryAccess) {
        return fromSeedfindingItem(new com.seedfinding.mcfeature.loot.item.ItemStack(item), registryAccess);
    }

    public static ItemStack fromSeedfindingItem(com.seedfinding.mcfeature.loot.item.ItemStack stack, RegistryAccess registryAccess) {
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.withDefaultNamespace(stack.getItem().getName()));
        if (!stack.getItem().getEnchantments().isEmpty() && item == Items.BOOK) {
            item = Items.ENCHANTED_BOOK;
        }

        Registry<Enchantment> enchantmentRegistry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);

        ItemStack ret = new ItemStack(item, stack.getCount());
        for (var enchAndLevel : stack.getItem().getEnchantments()) {
            ResourceKey<Enchantment> enchKey = Objects.requireNonNull(SEEDFINDING_ENCHANTMENTS.inverse().get(enchAndLevel.getFirst()), () -> "missing seedfinding enchantment " + enchAndLevel.getFirst());
            enchantmentRegistry.get(enchKey).ifPresent(enchantment -> {
                ret.enchant(enchantment, enchAndLevel.getSecond());
            });
        }
        return ret;
    }

    public static MCVersion getMCVersion() {
        return Objects.requireNonNullElseGet(MCVersion.fromString(MultiVersionCompat.INSTANCE.getProtocolName()), MCVersion::latest);
    }
}
