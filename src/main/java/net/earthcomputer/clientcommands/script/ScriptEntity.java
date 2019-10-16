package net.earthcomputer.clientcommands.script;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;

import java.lang.ref.WeakReference;

public class ScriptEntity {

    private final WeakReference<Entity> entity;

    public ScriptEntity(Entity entity) {
        this.entity = new WeakReference<>(entity);
    }

    private Entity getEntity() {
        Entity entity = this.entity.get();
        assert entity != null;
        return entity;
    }

    public double getX() {
        return getEntity().x;
    }

    public double getY() {
        return getEntity().y;
    }

    public double getZ() {
        return getEntity().z;
    }

    public float getYaw() {
        return getEntity().yaw;
    }

    public float getPitch() {
        return getEntity().pitch;
    }

    public Object getNbt() {
        return ScriptUtil.fromNbt(getEntity().toTag(new CompoundTag()));
    }

}
