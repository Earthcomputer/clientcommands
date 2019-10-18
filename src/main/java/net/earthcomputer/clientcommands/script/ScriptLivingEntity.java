package net.earthcomputer.clientcommands.script;

import net.minecraft.entity.LivingEntity;

public class ScriptLivingEntity extends ScriptEntity {

    ScriptLivingEntity(LivingEntity entity) {
        super(entity);
    }

    @Override
    LivingEntity getEntity() {
        return (LivingEntity) super.getEntity();
    }

    public double getEyeHeight() {
        return getEntity().getEyeHeight(getEntity().getPose());
    }
}
