package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IBlockChangeListener;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientWorld {

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void onTickEntity(Entity entity, CallbackInfo ci) {
        ((IEntity) entity).tickGlowingTickets();
    }

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void onUpdateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        IBlockChangeListener.LISTENERS.forEach(listener -> listener.onBlockChange(pos, oldState, newState));
    }

}
