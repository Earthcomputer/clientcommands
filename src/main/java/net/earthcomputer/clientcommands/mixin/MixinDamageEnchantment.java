package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DamageEnchantment.class)
public class MixinDamageEnchantment {

    @Inject(method = "doPostAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    public void onAttackArthropod(LivingEntity attacker, Entity attacked, int level, CallbackInfo ci) {
        if (attacker instanceof LocalPlayer) {
            PlayerRandCracker.onBaneOfArthropods();
        }
    }

}
