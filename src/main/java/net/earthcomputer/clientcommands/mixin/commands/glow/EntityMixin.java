package net.earthcomputer.clientcommands.mixin.commands.glow;

import net.earthcomputer.clientcommands.features.EntityGlowingTicket;
import net.earthcomputer.clientcommands.interfaces.IEntity_Glowable;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mixin(Entity.class)
public class EntityMixin implements IEntity_Glowable {
    @Unique
    private final List<EntityGlowingTicket> glowingTickets = new ArrayList<>(0);

    @Override
    public void clientcommands_addGlowingTicket(int ticks, int color) {
        glowingTickets.add(new EntityGlowingTicket(ticks, color));
    }

    @Override
    public boolean clientcommands_hasGlowingTicket() {
        return !glowingTickets.isEmpty();
    }

    @Unique
    private int getGlowingTicketColor() {
        return glowingTickets.isEmpty() ? 0xffffff : glowingTickets.getLast().getColor();
    }

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void overrideIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> ci) {
        if (!glowingTickets.isEmpty()) {
            ci.setReturnValue(Boolean.TRUE);
        }
    }

    @Override
    public void clientcommands_tickGlowingTickets() {
        if (((Entity) (Object) this).level().isClientSide) {
            Iterator<EntityGlowingTicket> itr = glowingTickets.iterator();
            //noinspection Java8CollectionRemoveIf
            while (itr.hasNext()) {
                EntityGlowingTicket glowingTicket = itr.next();
                if (!glowingTicket.tick()) {
                    itr.remove();
                }
            }
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    public void injectGetTeamColor(CallbackInfoReturnable<Integer> ci) {
        if (clientcommands_hasGlowingTicket()) {
            ci.setReturnValue(getGlowingTicketColor());
        }
    }
}
