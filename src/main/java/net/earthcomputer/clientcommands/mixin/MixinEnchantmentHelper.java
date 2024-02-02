package net.earthcomputer.clientcommands.mixin;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.earthcomputer.clientcommands.features.SeedfindingUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
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

    @Inject(method = "selectEnchantment", at = @At("HEAD"), cancellable = true)
    private static void getEnchantments1140(RandomSource rand, ItemStack stack, int level, boolean allowTreasure, CallbackInfoReturnable<List<EnchantmentInstance>> ci) {
        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        if (protocolVersion < MultiVersionCompat.V1_14 || protocolVersion > MultiVersionCompat.V1_14_2) {
            return;
        }

        List<EnchantmentInstance> enchantments = Lists.newArrayList();
        ci.setReturnValue(enchantments);

        Item item = stack.getItem();
        int enchantability = item.getEnchantmentValue();
        if (enchantability <= 0) {
            return;
        }

        level += 1 + rand.nextInt(enchantability / 4 + 1) + rand.nextInt(enchantability / 4 + 1);
        float change = (rand.nextFloat() + rand.nextFloat() - 1) * 0.15f;
        level = Mth.clamp(Math.round(level + level * change), 1, Integer.MAX_VALUE);

        List<EnchantmentInstance> applicableEnchantments = EnchantmentHelper.getAvailableEnchantmentResults(level, stack, allowTreasure);
        if (!applicableEnchantments.isEmpty()) {
            Optional<EnchantmentInstance> optEnch = WeightedRandom.getRandomItem(rand, applicableEnchantments);
            optEnch.ifPresent(enchantments::add);

            while (rand.nextInt(50) <= level) {
                level = level * 4 / 5 + 1;
                applicableEnchantments = EnchantmentHelper.getAvailableEnchantmentResults(level, stack, allowTreasure);
                for (EnchantmentInstance ench : enchantments) {
                    EnchantmentHelper.filterCompatibleEnchantments(applicableEnchantments, ench);
                }

                if (applicableEnchantments.isEmpty()) {
                    break;
                }

                optEnch = WeightedRandom.getRandomItem(rand, applicableEnchantments);
                optEnch.ifPresent(enchantments::add);

                level /= 2;
            }
        }
    }

    @ModifyVariable(method = "getAvailableEnchantmentResults", ordinal = 0, at = @At(value = "STORE", ordinal = 0))
    private static Iterator<Enchantment> filterServerUnknownEnchantments(Iterator<Enchantment> itr) {
        return Iterators.filter(itr, SeedfindingUtil::doesEnchantmentExist);
    }

}
