package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.StringReader;
import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.ClientcommandsDataQueryHandler;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.PluginsCommand;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceOrbSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.NbtQueryResponseS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler extends ClientCommonNetworkHandler implements ClientcommandsDataQueryHandler.IClientPlayNetworkHandler {
    @Unique
    private final ClientcommandsDataQueryHandler ccDataQueryHandler = new ClientcommandsDataQueryHandler((ClientPlayNetworkHandler) (Object) this);

    protected MixinClientPlayNetworkHandler(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
        super(client, connection, connectionState);
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

    @Inject(method = "onNbtQueryResponse", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onOnNbtQueryResponse(NbtQueryResponseS2CPacket packet, CallbackInfo ci) {
        if (ccDataQueryHandler.handleQueryResponse(packet.getTransactionId(), packet.getNbt())) {
            ci.cancel();
        }
    }

    @Override
    public ClientcommandsDataQueryHandler clientcommands_getCCDataQueryHandler() {
        return ccDataQueryHandler;
    }

    @Inject(method = "onCommandSuggestions", at = @At("TAIL"))
    private void onCommandSuggestions(CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
        PluginsCommand.onCommandSuggestions(packet);
    }
}
