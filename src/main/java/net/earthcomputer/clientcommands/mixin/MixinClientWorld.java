package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IBlockChangeListener;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientWorld {

    @Inject(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void onTickEntity(Entity entity, CallbackInfo ci) {
        ((IEntity) entity).tickGlowingTickets();
    }

    @Inject(method = "updateListeners", at = @At("HEAD"))
    private void onUpdateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        IBlockChangeListener.LISTENERS.forEach(listener -> listener.onBlockChange(pos, oldState, newState));
    }

}
