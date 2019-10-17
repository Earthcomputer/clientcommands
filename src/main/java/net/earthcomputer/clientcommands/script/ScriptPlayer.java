package net.earthcomputer.clientcommands.script;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

public class ScriptPlayer extends ScriptEntity {

    public ScriptPlayer() {
        super(getPlayer());
    }

    private static ClientPlayerEntity getPlayer() {
        return MinecraftClient.getInstance().player;
    }

    @Override
    Entity getEntity() {
        return getPlayer();
    }

    public void setYaw(float yaw) {
        getPlayer().yaw = yaw;
    }

    public void setPitch(float pitch) {
        getPlayer().pitch = pitch;
    }

}
