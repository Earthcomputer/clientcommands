package net.earthcomputer.clientcommands.script;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.registry.Registry;

import java.lang.ref.WeakReference;

@SuppressWarnings("unused")
public class ScriptEntity {

    private final WeakReference<Entity> entity;

    static ScriptEntity create(Entity entity) {
        if (entity == MinecraftClient.getInstance().player) {
            return new ScriptPlayer();
        } else if (entity instanceof LivingEntity) {
            return new ScriptLivingEntity((LivingEntity) entity);
        } else {
            return new ScriptEntity(entity);
        }
    }

    ScriptEntity(Entity entity) {
        this.entity = new WeakReference<>(entity);
    }

    Entity getEntity() {
        Entity entity = this.entity.get();
        assert entity != null;
        return entity;
    }

    public String getType() {
        return ScriptUtil.simplifyIdentifier(Registry.ENTITY_TYPE.getId(getEntity().getType()));
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

    public double getMotionX() {
        return getEntity().getVelocity().x;
    }

    public double getMotionY() {
        return getEntity().getVelocity().y;
    }

    public double getMotionZ() {
        return getEntity().getVelocity().z;
    }

    public Object getNbt() {
        return ScriptUtil.fromNbt(getEntity().toTag(new CompoundTag()));
    }

    @Override
    public int hashCode() {
        return entity.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScriptEntity)) return false;
        return entity.equals(((ScriptEntity) o).entity);
    }
}
