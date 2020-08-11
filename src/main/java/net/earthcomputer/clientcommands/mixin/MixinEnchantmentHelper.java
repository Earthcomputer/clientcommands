package net.earthcomputer.clientcommands.mixin;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import net.earthcomputer.multiconnect.api.MultiConnectAPI;
import net.earthcomputer.multiconnect.api.Protocols;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.WeightedPicker;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelper {

    @Inject(method = "generateEnchantments", at = @At("HEAD"), cancellable = true)
    private static void getEnchantments1140(Random rand, ItemStack stack, int level, boolean allowTreasure, CallbackInfoReturnable<List<EnchantmentLevelEntry>> ci) {
        int protocolVersion = MultiConnectAPI.instance().getProtocolVersion();
        if (protocolVersion < Protocols.V1_14 || protocolVersion > Protocols.V1_14_2)
            return;

        List<EnchantmentLevelEntry> enchantments = Lists.newArrayList();
        ci.setReturnValue(enchantments);

        Item item = stack.getItem();
        int enchantability = item.getEnchantability();
        if (enchantability <= 0)
            return;

        level += 1 + rand.nextInt(enchantability / 4 + 1) + rand.nextInt(enchantability / 4 + 1);
        float change = (rand.nextFloat() + rand.nextFloat() - 1) * 0.15f;
        level = MathHelper.clamp(Math.round(level + level * change), 1, Integer.MAX_VALUE);

        List<EnchantmentLevelEntry> applicableEnchantments = EnchantmentHelper.getPossibleEntries(level, stack, allowTreasure);
        if (!applicableEnchantments.isEmpty()) {
            enchantments.add(WeightedPicker.getRandom(rand, applicableEnchantments));

            while (rand.nextInt(50) <= level) {
                level = level * 4 / 5 + 1;
                applicableEnchantments = EnchantmentHelper.getPossibleEntries(level, stack, allowTreasure);
                for (EnchantmentLevelEntry ench : enchantments) {
                    EnchantmentHelper.removeConflicts(applicableEnchantments, ench);
                }

                if (applicableEnchantments.isEmpty())
                    break;

                enchantments.add(WeightedPicker.getRandom(rand, applicableEnchantments));

                level /= 2;
            }
        }
    }

    @ModifyVariable(method = "getPossibleEntries", ordinal = 0, at = @At(value = "STORE", ordinal = 0))
    private static Iterator<Enchantment> filterServerUnknwonEnchantments(Iterator<Enchantment> itr) {
        return Iterators.filter(itr, enchantment -> MultiConnectAPI.instance().doesServerKnow(Registry.ENCHANTMENT, enchantment));
    }

}
