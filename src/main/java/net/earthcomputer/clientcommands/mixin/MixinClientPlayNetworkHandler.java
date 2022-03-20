package net.earthcomputer.clientcommands.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.TntFinderManager;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.Relogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.telemetry.TelemetrySender;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.EntityType;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    @Shadow
    private CommandDispatcher<CommandSource> commandDispatcher;

    @Shadow @Final private MinecraftClient client;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(MinecraftClient mc, Screen screen, ClientConnection connection, GameProfile profile, TelemetrySender telemetrySender, CallbackInfo ci) {
        ClientCommands.registerCommands((CommandDispatcher<ServerCommandSource>) (Object) commandDispatcher);
    }

    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void postGameJoin(CallbackInfo ci) {
        Relogger.onRelogSuccess();
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "onCommandTree", at = @At("TAIL"))
    public void onOnCommandTree(CommandTreeS2CPacket packet, CallbackInfo ci) {
        ClientCommands.registerCommands((CommandDispatcher<ServerCommandSource>) (Object) commandDispatcher);
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
    public void onExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        System.out.println(this.client.player.getX() + ", " + this.client.player.getY() + ", " + this.client.player.getZ());
        System.out.println(packet.getPlayerVelocityX() + ", " + packet.getPlayerVelocityY() + ", " + packet.getPlayerVelocityZ());
        if (TempRules.getTntFinder()) {
            boolean isReady = TntFinderManager.set(new Vec3d(this.client.player.getX(), 0, this.client.player.getZ()), new Vec3d(packet.getPlayerVelocityX(), 0, packet.getPlayerVelocityZ()));
            if (isReady) {
                Vec3d loc = TntFinderManager.triangulate();
                if (loc == null) {
                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new TranslatableText("tntFinder.parallelVectors"));
                } else {
                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new TranslatableText("tntFinder.triangulatedLocation", loc.x, loc.z));
                }
            }
        }
    }
}
