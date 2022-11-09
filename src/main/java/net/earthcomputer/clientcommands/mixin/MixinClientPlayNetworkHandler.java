package net.earthcomputer.clientcommands.mixin;

import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.c2c.*;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.interfaces.IProfileKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.DynamicRegistryManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Optional;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
    @Shadow @Final private MinecraftClient client;

    @Shadow private DynamicRegistryManager.Immutable registryManager;

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

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    public void onC2CPacket(ChatMessageS2CPacket packet, CallbackInfo ci) {
        Optional<MessageType.Parameters> optionalParameters = packet.getParameters(this.registryManager);
        if (optionalParameters.isEmpty()) {
            return;
        }
        Text content = optionalParameters.get().applyChatDecoration(packet.message().getContent());
        handleIfPacket(content, ci);
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    public void onC2CPacket(GameMessageS2CPacket packet, CallbackInfo ci) {
        Text content = packet.content();
        handleIfPacket(content, ci);
    }

    private void handleIfPacket(Text content, CallbackInfo ci) {
        String string = content.getString();
        int index = string.indexOf("CCENC:");
        if (index == -1) {
            return;
        }
        String packetString = string.substring(index + 6);
        if (!TempRules.acceptC2CPackets) {
            if (PacketCacheHelper.removeIfContains(packetString)) {
                this.client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.sentC2CPacket"));
            } else {
                this.client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.receivedC2CPacket").styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, content))));
            }
            ci.cancel();
            return;
        }
        if (PacketCacheHelper.removeIfContains(packetString)) {
            ci.cancel();
            return;
        }
        if (handleC2CPacket(packetString)) {
            ci.cancel();
        } else {
            this.client.inGameHud.getChatHud().addMessage(Text.translatable("ccpacket.malformedPacket"));
        }
    }

    private static boolean handleC2CPacket(String content) {
        byte[] encrypted = ConversionHelper.BaseUTF8.fromUnicode(content);
        encrypted = Arrays.copyOf(encrypted, 256);
        Optional<PrivateKey> key = ((IProfileKeys) MinecraftClient.getInstance().getProfileKeys()).getPrivateKey();
        if (key.isEmpty()) {
            return false;
        }
        byte[] decrypted = ConversionHelper.RsaEcb.decrypt(encrypted, key.get());
        if (decrypted == null) {
            return false;
        }
        byte[] uncompressed = ConversionHelper.Gzip.uncompress(decrypted);
        StringBuf buf = new StringBuf(new String(uncompressed, StandardCharsets.UTF_8));
        int id = buf.readInt();
        CCPacket ccPacket = CCPacketHandler.createPacket(id, buf);
        if (ccPacket == null) {
            return false;
        }
        if (buf.getRemainingLength() > 0) {
            return false;
        }
        ccPacket.apply(CCNetworkHandler.getInstance());
        return true;
    }
}
