package net.earthcomputer.clientcommands.mixin;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.earthcomputer.clientcommands.features.SeedfindingUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.Weighting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelper {

    @Inject(method = "generateEnchantments", at = @At("HEAD"), cancellable = true)
    private static void getEnchantments1140(Random rand, ItemStack stack, int level, boolean allowTreasure, CallbackInfoReturnable<List<EnchantmentLevelEntry>> ci) {
        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        if (protocolVersion < MultiVersionCompat.V1_14 || protocolVersion > MultiVersionCompat.V1_14_2) {
            return;
        }

        List<EnchantmentLevelEntry> enchantments = Lists.newArrayList();
        ci.setReturnValue(enchantments);

        Item item = stack.getItem();
        int enchantability = item.getEnchantability();
        if (enchantability <= 0) {
            return;
        }

        level += 1 + rand.nextInt(enchantability / 4 + 1) + rand.nextInt(enchantability / 4 + 1);
        float change = (rand.nextFloat() + rand.nextFloat() - 1) * 0.15f;
        level = MathHelper.clamp(Math.round(level + level * change), 1, Integer.MAX_VALUE);

        List<EnchantmentLevelEntry> applicableEnchantments = EnchantmentHelper.getPossibleEntries(level, stack, allowTreasure);
        if (!applicableEnchantments.isEmpty()) {
            Optional<EnchantmentLevelEntry> optEnch = Weighting.getRandom(rand, applicableEnchantments);
            optEnch.ifPresent(enchantments::add);

            while (rand.nextInt(50) <= level) {
                level = level * 4 / 5 + 1;
                applicableEnchantments = EnchantmentHelper.getPossibleEntries(level, stack, allowTreasure);
                for (EnchantmentLevelEntry ench : enchantments) {
                    EnchantmentHelper.removeConflicts(applicableEnchantments, ench);
                }

                if (applicableEnchantments.isEmpty()) {
                    break;
                }

                optEnch = Weighting.getRandom(rand, applicableEnchantments);
                optEnch.ifPresent(enchantments::add);

                level /= 2;
            }
        }
    }

    @ModifyVariable(method = "getPossibleEntries", ordinal = 0, at = @At(value = "STORE", ordinal = 0))
    private static Iterator<Enchantment> filterServerUnknownEnchantments(Iterator<Enchantment> itr) {
        return Iterators.filter(itr, SeedfindingUtil::doesEnchantmentExist);
    }

}
