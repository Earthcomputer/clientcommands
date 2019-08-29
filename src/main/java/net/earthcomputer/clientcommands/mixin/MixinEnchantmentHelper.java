package net.earthcomputer.clientcommands.mixin;

import com.google.common.collect.Lists;
import net.earthcomputer.multiconnect.api.MultiConnectAPI;
import net.earthcomputer.multiconnect.api.Protocols;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.InfoEnchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Random;

@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelper {

    @Inject(method = "getEnchantments(Ljava/util/Random;Lnet/minecraft/item/ItemStack;IZ)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private static void getEnchantments1140(Random rand, ItemStack stack, int level, boolean allowTreasure, CallbackInfoReturnable<List<InfoEnchantment>> ci) {
        int protocolVersion = MultiConnectAPI.instance().getProtocolVersion();
        if (protocolVersion < Protocols.V1_14 || protocolVersion > Protocols.V1_14_2)
            return;

        List<InfoEnchantment> enchantments = Lists.newArrayList();
        ci.setReturnValue(enchantments);

        Item item = stack.getItem();
        int enchantability = item.getEnchantability();
        if (enchantability <= 0)
            return;

        level += 1 + rand.nextInt(enchantability / 4 + 1) + rand.nextInt(enchantability / 4 + 1);
        float change = (rand.nextFloat() + rand.nextFloat() - 1) * 0.15f;
        level = MathHelper.clamp(Math.round(level + level * change), 1, Integer.MAX_VALUE);

        List<InfoEnchantment> applicableEnchantments = EnchantmentHelper.getHighestApplicableEnchantmentsAtPower(level, stack, allowTreasure);
        if (!applicableEnchantments.isEmpty()) {
            enchantments.add(WeightedPicker.getRandom(rand, applicableEnchantments));

            while (rand.nextInt(50) <= level) {
                level = level * 4 / 5 + 1;
                applicableEnchantments = EnchantmentHelper.getHighestApplicableEnchantmentsAtPower(level, stack, allowTreasure);
                for (InfoEnchantment ench : enchantments) {
                    EnchantmentHelper.remove(applicableEnchantments, ench);
                }

                if (applicableEnchantments.isEmpty())
                    break;

                enchantments.add(WeightedPicker.getRandom(rand, applicableEnchantments));

                level /= 2;
            }
        }
    }

}
