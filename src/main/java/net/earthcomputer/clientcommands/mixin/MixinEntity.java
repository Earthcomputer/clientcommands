package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.EntityGlowingTicket;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mixin(Entity.class)
public class MixinEntity implements IEntity {

    @Unique
    private List<EntityGlowingTicket> glowingTickets = new ArrayList<>(0);

    @Override
    public void addGlowingTicket(int ticks, int color) {
        glowingTickets.add(new EntityGlowingTicket(ticks, color));
    }

    @Override
    public boolean hasGlowingTicket() {
        return !glowingTickets.isEmpty();
    }

    @Override
    public int getGlowingTicketColor() {
        return glowingTickets.isEmpty() ? 0xffffff : glowingTickets.get(glowingTickets.size() - 1).getColor();
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void overrideIsGlowing(CallbackInfoReturnable<Boolean> ci) {
        if (!glowingTickets.isEmpty())
            ci.setReturnValue(Boolean.TRUE);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (((Entity) (Object) this).world.isClient) {
            Iterator<EntityGlowingTicket> itr = glowingTickets.iterator();
            //noinspection Java8CollectionRemoveIf
            while (itr.hasNext()) {
                EntityGlowingTicket glowingTicket = itr.next();
                if (!glowingTicket.tick())
                    itr.remove();
            }
        }
    }

    @Inject(method = "onSwimmingStart", at = @At("HEAD"))
    public void onOnSwimmingStart(CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onSwimmingStart();
    }

    @Inject(method = "spawnSprintingParticles", at = @At("HEAD"))
    public void onSprinting(CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onSprinting();
    }

    private boolean isThePlayer() {
        //noinspection ConstantConditions
        return (Object) this instanceof ClientPlayerEntity;
    }
}
