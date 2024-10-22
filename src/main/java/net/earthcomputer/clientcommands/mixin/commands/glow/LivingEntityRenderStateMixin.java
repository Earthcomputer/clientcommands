package net.earthcomputer.clientcommands.mixin.commands.glow;

import net.earthcomputer.clientcommands.interfaces.ILivingEntityRenderState_Glowable;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements ILivingEntityRenderState_Glowable {
    @Unique
    private boolean hasGlowingTicket;

    @Override
    public boolean clientcommands_hasGlowingTicket() {
        return hasGlowingTicket;
    }

    @Override
    public void clientcommands_setHasGlowingTicket(boolean hasGlowingTicket) {
        this.hasGlowingTicket = hasGlowingTicket;
    }
}
