package net.earthcomputer.clientcommands.script;

import net.minecraft.entity.LivingEntity;

@SuppressWarnings("unused")
public class ScriptLivingEntity extends ScriptEntity {

    ScriptLivingEntity(LivingEntity entity) {
        super(entity);
    }

    @Override
    LivingEntity getEntity() {
        return (LivingEntity) super.getEntity();
    }

    public double getStandingEyeHeight() {
        return getEntity().getStandingEyeHeight();
    }

    public double getEyeHeight() {
        return getEntity().getEyeHeight(getEntity().getPose());
    }
}
