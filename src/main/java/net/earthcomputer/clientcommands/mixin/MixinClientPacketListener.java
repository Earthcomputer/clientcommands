package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.StringReader;
import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.ClientcommandsDataQueryHandler;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.PluginsCommand;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener extends ClientCommonPacketListenerImpl implements ClientcommandsDataQueryHandler.IClientPlayNetworkHandler {
    @Unique
    private final ClientcommandsDataQueryHandler ccDataQueryHandler = new ClientcommandsDataQueryHandler((ClientPacketListener) (Object) this);

    protected MixinClientPacketListener(Minecraft minecraft, Connection connection, CommonListenerCookie listenerCookie) {
        super(minecraft, connection, listenerCookie);
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    public void onHandleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        SeedCracker.onEntityCreation(packet);

        if (FishingCracker.canManipulateFishing()) {
            if (packet.getData() == player.getId() && packet.getType() == EntityType.FISHING_BOBBER) {
                FishingCracker.processBobberSpawn(packet.getUUID(), new Vec3(packet.getX(), packet.getY(), packet.getZ()), new Vec3(packet.getXa(), packet.getYa(), packet.getZa()));
            }
        }
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    public void onHandleAddEntityPre(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        // Called on network thread first, FishingCracker.waitingForFishingRod

        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        if (!FishingCracker.canManipulateFishing() || packet.getData() != player.getId() || packet.getType() != EntityType.FISHING_BOBBER) {
            return;
        }

        FishingCracker.onFishingBobberEntity();
    }

    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfo ci) {
        StringReader reader = new StringReader(command);
        String commandName = reader.canRead() ? reader.readUnquotedString() : "";
        if ("give".equals(commandName)) {
            PlayerRandCracker.onGiveCommand();
        }
    }

    @Inject(method = "handleAddExperienceOrb", at = @At("TAIL"))
    public void onHandleAddExperienceOrb(ClientboundAddExperienceOrbPacket packet, CallbackInfo ci) {
        if (FishingCracker.canManipulateFishing()) {
            FishingCracker.processExperienceOrbSpawn(packet.getX(), packet.getY(), packet.getZ(), packet.getValue());
        }
    }

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void onHandleSetTimePre(CallbackInfo ci) {
        if (Configs.getFishingManipulation().isEnabled() && !Minecraft.getInstance().isSameThread()) {
            FishingCracker.onTimeSync();
        }
    }

    @Inject(method = "handleTagQueryPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onHandleTagQueryPacket(ClientboundTagQueryPacket packet, CallbackInfo ci) {
        if (ccDataQueryHandler.handleQueryResponse(packet.getTransactionId(), packet.getTag())) {
            ci.cancel();
        }
    }

    @Override
    public ClientcommandsDataQueryHandler clientcommands_getCCDataQueryHandler() {
        return ccDataQueryHandler;
    }

    @Inject(method = "handleCommandSuggestions", at = @At("TAIL"))
    private void onHandleCommandSuggestions(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
        PluginsCommand.onCommandSuggestions(packet);
    }
}
