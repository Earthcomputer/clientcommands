package net.earthcomputer.clientcommands.script;

import net.minecraft.client.network.ClientPlayerEntity;

import java.lang.ref.WeakReference;

public class ScriptPlayer extends ScriptEntity {

    private final WeakReference<ClientPlayerEntity> player;

    public ScriptPlayer(ClientPlayerEntity player) {
        super(player);
        this.player = new WeakReference<>(player);
    }

    private ClientPlayerEntity getPlayer() {
        ClientPlayerEntity player = this.player.get();
        assert player != null;
        return player;
    }

    public void setYaw(float yaw) {
        getPlayer().yaw = yaw;
    }

    public void setPitch(float pitch) {
        getPlayer().pitch = pitch;
    }

}
