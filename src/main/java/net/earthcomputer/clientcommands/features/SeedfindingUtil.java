package net.earthcomputer.clientcommands.features;

import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.enchantment.Enchantments;
import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.minecraft.SharedConstants;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SeedfindingUtil {
    private SeedfindingUtil() {
    }

    @Nullable
    public static com.seedfinding.mcbiome.biome.Biome toSeedfindingBiome(World world, RegistryEntry<Biome> biome) {
        Identifier name = world.getRegistryManager().get(RegistryKeys.BIOME).getId(biome.value());
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

    public static ItemStack fromSeedfindingItem(com.seedfinding.mcfeature.loot.item.Item item) {
        return fromSeedfindingItem(new com.seedfinding.mcfeature.loot.item.ItemStack(item));
    }

    public static ItemStack fromSeedfindingItem(com.seedfinding.mcfeature.loot.item.ItemStack stack) {
        Item item = Registries.ITEM.get(new Identifier(stack.getItem().getName()));
        if (!stack.getItem().getEnchantments().isEmpty() && item == Items.BOOK) {
            item = Items.ENCHANTED_BOOK;
        }

        ItemStack ret = new ItemStack(item, stack.getCount());
        for (var enchAndLevel : stack.getItem().getEnchantments()) {
            Enchantment enchantment = Registries.ENCHANTMENT.get(new Identifier(enchAndLevel.getFirst()));
            if (enchantment == null) {
                continue;
            }
            if (item == Items.ENCHANTED_BOOK) {
                EnchantedBookItem.addEnchantment(ret, new EnchantmentLevelEntry(enchantment, enchAndLevel.getSecond()));
            } else {
                ret.addEnchantment(enchantment, enchAndLevel.getSecond());
            }
        }
        return ret;
    }

    public static boolean doesEnchantmentExist(Enchantment enchantment) {
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() == SharedConstants.getProtocolVersion()) {
            return true;
        }

        Identifier id = Registries.ENCHANTMENT.getId(enchantment);
        if (id == null || !id.getNamespace().equals("minecraft")) {
            return false;
        }
        String name = id.getPath();
        return Enchantments.getFor(SeedfindingUtil.getMCVersion()).stream().anyMatch(ench -> ench.getName().equals(name));
    }

    public static MCVersion getMCVersion() {
        return Objects.requireNonNullElseGet(MCVersion.fromString(MultiVersionCompat.INSTANCE.getProtocolName()), MCVersion::latest);
    }
}
