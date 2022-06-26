package net.earthcomputer.clientcommands.mixin;

import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.TntFinderManager;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.Relogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void postGameJoin(CallbackInfo ci) {
        Relogger.onRelogSuccess();
    }

    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    public void onOnEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        SeedCracker.onEntityCreation(packet);

        if (FishingCracker.canManipulateFishing()) {
            if (packet.getEntityData() == player.getId() && packet.getEntityTypeId() == EntityType.FISHING_BOBBER) {
                FishingCracker.processBobberSpawn(packet.getUuid(), new Vec3d(packet.getX(), packet.getY(), packet.getZ()), new Vec3d(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ()));
            }
        }
    }

    @Inject(method = "onEntitySpawn", at = @At("HEAD"))
    public void onOnEntitySpawnPre(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        // Called on network thread first, FishingCracker.waitingForFishingRod

        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        if (!FishingCracker.canManipulateFishing() || packet.getEntityData() != player.getId() || packet.getEntityTypeId() != EntityType.FISHING_BOBBER) {
            return;
        }

        FishingCracker.onFishingBobberEntity();
    }

    @Inject(method = "onExperienceOrbSpawn", at = @At("TAIL"))
    public void onOnExperienceOrbSpawn(ExperienceOrbSpawnS2CPacket packet, CallbackInfo ci) {
        if (FishingCracker.canManipulateFishing()) {
            FishingCracker.processExperienceOrbSpawn(packet.getX(), packet.getY(), packet.getZ(), packet.getExperience());
        }
    }

    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"))
    private void onOnWorldTimeUpdatePre(CallbackInfo ci) {
        if (TempRules.getFishingManipulation().isEnabled() && !MinecraftClient.getInstance().isOnThread()) {
            FishingCracker.onTimeSync();
        }
    }

    @Inject(method = "onCustomPayload", at = @At("TAIL"))
    public void onOnCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (CustomPayloadS2CPacket.BRAND.equals(packet.getChannel())) {
            ServerBrandManager.setServerBrand(this.client.player.getServerBrand());
        }
    }

    @Inject(method = "onExplosion", at = @At("TAIL"))
    private void onExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        if (!TempRules.getTntFinder()) {
            return;
        }
        final float velX = packet.getPlayerVelocityX();
        final float velZ = packet.getPlayerVelocityZ();
        if (velX != 0.0 || velZ != 0.0) {
            sendError(Text.translatable("tntFinder.explosionInterferenceWarning"));
        }
    }

    @Inject(method = "onEntityVelocityUpdate", at = @At("TAIL"))
    private void onEntityVelocityUpdate(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        if (!TempRules.getTntFinder()) {
            return;
        }
        if (packet.getId() == this.client.player.getId()) {
            boolean isReady = TntFinderManager.set(new Vec3d(this.client.player.getX(), 0, this.client.player.getZ()), new Vec3d(packet.getVelocityX(), 0, packet.getVelocityZ()));
            if (isReady) {
                Vec3d loc = TntFinderManager.triangulate();
                if (loc.y == -1184951860) {
                    sendError(Text.translatable("tntFinder.parallelVectorsWarning"));
                }
                sendFeedback(Text.translatable("tntFinder.triangulatedLocation", loc.x, loc.z));
            }
        }
    }
}
