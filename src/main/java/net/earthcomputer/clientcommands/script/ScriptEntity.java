package net.earthcomputer.clientcommands.script;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.registry.Registry;

import java.lang.ref.WeakReference;

@SuppressWarnings("unused")
public class ScriptEntity {

    private WeakReference<Entity> entity;
    private int entityId;

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
        if (entity != null) {
            this.entity = new WeakReference<>(entity);
            this.entityId = entity.getEntityId();
        }
    }

    Entity getNullableEntity() {
        ClientWorld theWorld = MinecraftClient.getInstance().world;
        if (this.entity == null || theWorld == null) {
            return null;
        }
        Entity entity = this.entity.get();
        if (entity == null || entity.world != theWorld) {
            entity = theWorld.getEntityById(entityId);
            if (entity != null) {
                this.entity = new WeakReference<>(entity);
            }
        }
        if (entity != null && entity.removed) {
            entity = null;
        }
        if (entity == null) {
            this.entity = null;
        }
        return entity;
    }

    Entity getEntity() {
        Entity entity = getNullableEntity();
        if (entity == null) {
            throw new NullPointerException("Invalid entity reference");
        }
        return entity;
    }

    public boolean isValid() {
        return getNullableEntity() != null;
    }

    public String getType() {
        return ScriptUtil.simplifyIdentifier(Registry.ENTITY_TYPE.getId(getEntity().getType()));
    }

    public double getX() {
        return getEntity().getX();
    }

    public double getY() {
        return getEntity().getY();
    }

    public double getZ() {
        return getEntity().getZ();
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
