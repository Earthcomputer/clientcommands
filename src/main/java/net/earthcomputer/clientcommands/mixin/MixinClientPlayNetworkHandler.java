package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.StringReader;
import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.earthcomputer.clientcommands.features.BypassRequiredResourcePackScreen;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.entity.EntityType;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceOrbSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler {

    @Shadow @Final private MinecraftClient client;

    @Shadow protected abstract void sendResourcePackStatus(ResourcePackStatusC2SPacket.Status packStatus);

    @Shadow @Final private @Nullable ServerInfo serverInfo;

    @Shadow protected abstract void feedbackAfterDownload(CompletableFuture<?> downloadFuture);

    @Shadow @Final private ClientConnection connection;

    @Shadow private static @Nullable URL resolveUrl(String url) {
        throw new AssertionError();
    }

    @Shadow private static Text getServerResourcePackPrompt(Text defaultPrompt, @Nullable Text customPrompt) {
        throw new AssertionError();
    }

    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    public void onOnEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        SeedCracker.onEntityCreation(packet);

        if (FishingCracker.canManipulateFishing()) {
            if (packet.getEntityData() == player.getId() && packet.getEntityType() == EntityType.FISHING_BOBBER) {
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

        if (!FishingCracker.canManipulateFishing() || packet.getEntityData() != player.getId() || packet.getEntityType() != EntityType.FISHING_BOBBER) {
            return;
        }

        FishingCracker.onFishingBobberEntity();
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"))
    private void onSendChatCommand(String command, CallbackInfo ci) {
        StringReader reader = new StringReader(command);
        String commandName = reader.canRead() ? reader.readUnquotedString() : "";
        if ("give".equals(commandName)) {
            PlayerRandCracker.onGiveCommand();
        }
    }

    @Inject(method = "onExperienceOrbSpawn", at = @At("TAIL"))
    public void onOnExperienceOrbSpawn(ExperienceOrbSpawnS2CPacket packet, CallbackInfo ci) {
        if (FishingCracker.canManipulateFishing()) {
            FishingCracker.processExperienceOrbSpawn(packet.getX(), packet.getY(), packet.getZ(), packet.getExperience());
        }
    }

    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"))
    private void onOnWorldTimeUpdatePre(CallbackInfo ci) {
        if (Configs.getFishingManipulation().isEnabled() && !MinecraftClient.getInstance().isOnThread()) {
            FishingCracker.onTimeSync();
        }
    }

    @Inject(method = "onCustomPayload", at = @At("TAIL"))
    public void onOnCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (CustomPayloadS2CPacket.BRAND.equals(packet.getChannel())) {
            ServerBrandManager.setServerBrand(this.client.player.getServerBrand());
        }
    }

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void onOnResourcePackSend(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        if (this.serverInfo != null && this.serverInfo.getResourcePackPolicy() == ServerInfo.ResourcePackPolicy.ENABLED) {
            return;
        }
        if (this.serverInfo != null && this.serverInfo.getResourcePackPolicy() == ServerInfo.ResourcePackPolicy.DISABLED) {
            this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.ACCEPTED);
            this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
            this.client.inGameHud.getChatHud().addMessage(Text.translatable("resourcePackBypass.bypassed"));
            ci.cancel();
            return;
        }
        if (this.serverInfo == null || this.serverInfo.getResourcePackPolicy() == ServerInfo.ResourcePackPolicy.PROMPT) {
            if (Configs.resourcePackBypassPolicy == Configs.ResourcePackBypassPolicy.VANILLA && packet.isRequired()) {
                this.client.execute(() -> this.client.setScreen(new BypassRequiredResourcePackScreen(proceed -> {
                    this.client.setScreen(null);
                    if (proceed) {
                        if (this.serverInfo != null) {
                            this.serverInfo.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.ENABLED);
                        }
                        this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.ACCEPTED);
                        this.feedbackAfterDownload(this.client.getServerResourcePackProvider().download(resolveUrl(packet.getURL()), packet.getSHA1(), true));
                    } else {
                        this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.DECLINED);
                        this.connection.disconnect(Text.translatable("multiplayer.requiredTexturePrompt.disconnect"));
                    }
                    if (this.serverInfo != null) {
                        ServerList.updateServerListEntry(this.serverInfo);
                    }
                }, () -> {
                    this.client.setScreen(null);
                    Configs.resourcePackBypassPolicy = Configs.ResourcePackBypassPolicy.BYPASS_FORCED;
                    this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.ACCEPTED);
                    this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
                    this.client.inGameHud.getChatHud().addMessage(Text.translatable("resourcePackBypass.bypassed"));
                }, getServerResourcePackPrompt(Text.translatable("resourcePackBypass.rejectOrBypass"), packet.getPrompt()))));
                ci.cancel();
                return;
            }
            if (Configs.resourcePackBypassPolicy == Configs.ResourcePackBypassPolicy.BYPASS_FORCED && packet.isRequired() || Configs.resourcePackBypassPolicy == Configs.ResourcePackBypassPolicy.BYPASS_ALL) {
                this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.ACCEPTED);
                this.sendResourcePackStatus(ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
                this.client.inGameHud.getChatHud().addMessage(Text.translatable("resourcePackBypass.bypassed"));
                ci.cancel();
            }
        }
    }
}
