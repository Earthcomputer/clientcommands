package net.earthcomputer.clientcommands.mixin.events;

import net.earthcomputer.clientcommands.event.MoreClientEntityEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public class ExperienceOrbMixin extends EntityMixin {
    @Shadow
    @Final
    protected static EntityDataAccessor<Integer> DATA_VALUE;
    @Unique
    private boolean hasXpUpdated = false;

    @Override
    protected void onSynchedDataUpdated(EntityDataAccessor<?> dataAccessor, CallbackInfo ci) {
        super.onSynchedDataUpdated(dataAccessor, ci);
        if (dataAccessor == DATA_VALUE && !hasXpUpdated) {
            hasXpUpdated = true;
            MoreClientEntityEvents.POST_SET_INITIAL_XP_ORB_VALUE.invoker().onSetInitialXpOrbValue((ExperienceOrb) (Object) this);
        }
    }
}
