package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public class MixinItemStack {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    public <T extends LivingEntity> void onDamage(int amount, T holder, Consumer<T> breakHandler, CallbackInfo ci) {
        PlayerRandCracker.onItemDamage(amount, holder, (ItemStack) (Object) this);
    }

}
