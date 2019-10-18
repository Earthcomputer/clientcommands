package net.earthcomputer.clientcommands.script;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.network.packet.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;

public class ScriptPlayer extends ScriptLivingEntity {

    ScriptPlayer() {
        super(getPlayer());
    }

    private static ClientPlayerEntity getPlayer() {
        return MinecraftClient.getInstance().player;
    }

    @Override
    ClientPlayerEntity getEntity() {
        return getPlayer();
    }

    public void setYaw(float yaw) {
        getPlayer().yaw = yaw;
    }

    public void setPitch(float pitch) {
        getPlayer().pitch = pitch;
    }

    public void lookAt(double x, double y, double z) {
        ClientPlayerEntity player = getPlayer();
        double dx = x - player.x;
        double dy = y - (player.y + getEyeHeight());
        double dz = z - player.z;
        double dh = Math.sqrt(dx * dx + dz * dz);
        player.yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        player.pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
    }

    public void lookAt(ScriptEntity entity) {
        if (entity instanceof ScriptLivingEntity) {
            double eyeHeight = ((ScriptLivingEntity) entity).getEyeHeight();
            lookAt(entity.getX(), entity.getY() + eyeHeight, entity.getZ());
        } else {
            lookAt(entity.getX(), entity.getY(), entity.getZ());
        }
    }

    public void syncRotation() {
        getPlayer().networkHandler.sendPacket(new PlayerMoveC2SPacket.LookOnly(getPlayer().yaw, getPlayer().pitch, getPlayer().onGround));
    }

    public int getSelectedSlot() {
        return getPlayer().inventory.selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        getPlayer().inventory.selectedSlot = MathHelper.clamp(slot, 0, 8);
    }

    public ScriptInventory getInventory() {
        return new ScriptInventory(getPlayer().playerContainer);
    }

    public ScriptInventory getOpenContainer() {
        return new ScriptInventory(getPlayer().container);
    }

}
