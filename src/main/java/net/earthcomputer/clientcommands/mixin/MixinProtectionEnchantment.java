package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProtectionEnchantment.class)
public abstract class MixinProtectionEnchantment extends Enchantment {

    protected MixinProtectionEnchantment(Rarity weight, EnchantmentCategory target, EquipmentSlot[] slots) {
        super(weight, target, slots);
    }

    @Inject(method = "checkCompatibility", at = @At("HEAD"), cancellable = true)
    public void isCompatible1140(Enchantment other, CallbackInfoReturnable<Boolean> ci) {
        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        if (protocolVersion < MultiVersionCompat.V1_14 || protocolVersion > MultiVersionCompat.V1_14_2) {
            return;
        }

        ci.setReturnValue(other != this);
    }

}
