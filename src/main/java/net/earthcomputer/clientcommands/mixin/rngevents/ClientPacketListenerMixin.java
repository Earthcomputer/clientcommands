package net.earthcomputer.clientcommands.mixin.rngevents;

import com.mojang.brigadier.StringReader;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.VillagerCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfo ci) {
        StringReader reader = new StringReader(command);
        String commandName = reader.canRead() ? reader.readUnquotedString() : "";
        if ("give".equals(commandName)) {
            PlayerRandCracker.onGiveCommand();
        }
    }

    @Inject(method = "handleSoundEvent", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void onHandleSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        Villager targetVillager = VillagerCracker.getVillager();
        if (targetVillager != null && new Vec3(packet.getX(), packet.getY(), packet.getZ()).distanceToSqr(targetVillager.position()) <= 0.1f) {
            VillagerCracker.onSoundEventPlayed(packet);
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void onHandleChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) {
            ResourceKey<Level> key = Minecraft.getInstance().level.dimension();
            packet.runUpdates((pos, state) -> {
                if (new GlobalPos(key, pos).equals(VillagerCracker.getClockPos())) {
                    VillagerCracker.onServerTick();
                }
            });
        }
    }

    @Inject(method = "handleAddExperienceOrb", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void onHandleAddExperienceOrb(ClientboundAddExperienceOrbPacket packet, CallbackInfo ci) {
        Villager targetVillager = VillagerCracker.getVillager();
        if (targetVillager != null && new Vec3(packet.getX(), packet.getY() - 0.5, packet.getZ()).distanceToSqr(targetVillager.position()) <= 0.1f) {
            VillagerCracker.onXpOrbSpawned(packet);
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void onHandleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null && new GlobalPos(Minecraft.getInstance().level.dimension(), packet.getPos()).equals(VillagerCracker.getClockPos())) {
            VillagerCracker.onServerTick();
        }
    }

    @Inject(method = "handleSetTime", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void handleSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (level.getDayTime() < 12000 && packet.getDayTime() >= 12000) {
            Villager targetVillager = VillagerCracker.getVillager();
            if (targetVillager != null) {
                ClientCommandHelper.sendHelp(Component.translatable("commands.cvillager.help.day"));
            }
        }
    }
}
