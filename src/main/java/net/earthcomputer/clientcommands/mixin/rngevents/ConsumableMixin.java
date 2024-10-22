package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Consumable.class)
public class ConsumableMixin {
    @Inject(method = "emitParticlesAndSounds", at = @At("HEAD"))
    private void onEmitParticlesAndSounds(RandomSource rand, LivingEntity entity, ItemStack stack, int particleCount, CallbackInfo ci) {
        if (entity == Minecraft.getInstance().player) {
            PlayerRandCracker.onConsume(stack, entity.position(), particleCount, entity.getUseItemRemainingTicks(), (Consumable) (Object) this);
        }
    }
}
