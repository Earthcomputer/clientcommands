package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.DamageEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DamageEnchantment.class)
public class MixinDamageEnchantment {

    @Inject(method = "onTargetDamaged", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", remap = false))
    public void onAttackArthropod(LivingEntity attacker, Entity attacked, int level, CallbackInfo ci) {
        if (attacker instanceof ClientPlayerEntity) {
            PlayerRandCracker.onBaneOfArthropods();
        }
    }

}
