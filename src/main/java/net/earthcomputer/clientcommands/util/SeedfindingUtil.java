package net.earthcomputer.clientcommands.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.effect.Effect;
import com.seedfinding.mcfeature.loot.effect.Effects;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

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

    private static final BiMap<Holder<MobEffect>, Effect> SEEDFINDING_EFFECTS = Util.make(HashBiMap.create(), map -> {
        map.put(MobEffects.SPEED, Effects.MOVEMENT_SPEED);
        map.put(MobEffects.SLOWNESS, Effects.MOVEMENT_SLOWDOWN);
        map.put(MobEffects.HASTE, Effects.DIG_SPEED);
        map.put(MobEffects.MINING_FATIGUE, Effects.DIG_SLOWDOWN);
        map.put(MobEffects.STRENGTH, Effects.DAMAGE_BOOST);
        map.put(MobEffects.INSTANT_HEALTH, Effects.HEAL);
        map.put(MobEffects.INSTANT_DAMAGE, Effects.HARM);
        map.put(MobEffects.JUMP_BOOST, Effects.JUMP);
        map.put(MobEffects.NAUSEA, Effects.CONFUSION);
        map.put(MobEffects.REGENERATION, Effects.REGENERATION);
        map.put(MobEffects.RESISTANCE, Effects.DAMAGE_RESISTANCE);
        map.put(MobEffects.FIRE_RESISTANCE, Effects.FIRE_RESISTANCE);
        map.put(MobEffects.WATER_BREATHING, Effects.WATER_BREATHING);
        map.put(MobEffects.INVISIBILITY, Effects.INVISIBILITY);
        map.put(MobEffects.BLINDNESS, Effects.BLINDNESS);
        map.put(MobEffects.NIGHT_VISION, Effects.NIGHT_VISION);
        map.put(MobEffects.HUNGER, Effects.HUNGER);
        map.put(MobEffects.WEAKNESS, Effects.WEAKNESS);
        map.put(MobEffects.POISON, Effects.POISON);
        map.put(MobEffects.WITHER, Effects.WITHER);
        map.put(MobEffects.HEALTH_BOOST, Effects.HEALTH_BOOST);
        map.put(MobEffects.ABSORPTION, Effects.ABSORPTION);
        map.put(MobEffects.SATURATION, Effects.SATURATION);
        map.put(MobEffects.GLOWING, Effects.GLOWING);
        map.put(MobEffects.LEVITATION, Effects.LEVITATION);
        map.put(MobEffects.LUCK, Effects.LUCK);
        map.put(MobEffects.UNLUCK, Effects.UNLUCK);
        map.put(MobEffects.SLOW_FALLING, Effects.SLOW_FALLING);
        map.put(MobEffects.CONDUIT_POWER, Effects.CONDUIT_POWER);
        map.put(MobEffects.DOLPHINS_GRACE, Effects.DOLPHINS_GRACE);
        map.put(MobEffects.BAD_OMEN, Effects.BAD_OMEN);
    });

    private SeedfindingUtil() {
    }

    public static com.seedfinding.mcbiome.biome.@Nullable Biome toSeedfindingBiome(Level level, Holder<Biome> biome) {
        Identifier name = level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(biome.value());
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
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(stack.getItem().getName()));
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

        for (var effectAndDuration : stack.getItem().getEffects()) {
            Holder<MobEffect> effectHolder = Objects.requireNonNull(SEEDFINDING_EFFECTS.inverse().get(effectAndDuration.getFirst()), () -> "missing seedfinding effect " + effectAndDuration.getFirst());
            SuspiciousStewEffects.Entry entry = new SuspiciousStewEffects.Entry(effectHolder, effectAndDuration.getSecond());
            ret.update(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY, entry, SuspiciousStewEffects::withEffectAdded);
        }
        return ret;
    }

    public static MCVersion getMCVersion() {
        return Objects.requireNonNullElseGet(MCVersion.fromString(MultiVersionCompat.INSTANCE.getProtocolName()), MCVersion::latest);
    }
}
