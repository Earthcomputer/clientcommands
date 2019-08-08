package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.packet.CommandTreeS2CPacket;
import net.minecraft.client.network.packet.CustomPayloadS2CPacket;
import net.minecraft.client.network.packet.EntitySpawnS2CPacket;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    @Shadow
    private CommandDispatcher<CommandSource> commandDispatcher;

    @Shadow
    private MinecraftClient client;

    @SuppressWarnings("unchecked")
    @Inject(method = "onCommandTree", at = @At("TAIL"))
    public void onOnCommandTree(CommandTreeS2CPacket packet, CallbackInfo ci) {
        ClientCommands.registerCommands((CommandDispatcher<ServerCommandSource>) (Object) commandDispatcher);
    }

    @Inject(method = "onEntitySpawn", at = @At("RETURN"))
    public void onOnEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        SeedCracker.onEntityCreation(packet);
    }

    @Inject(method = "onCustomPayload", at = @At("TAIL"))
    public void onOnCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (CustomPayloadS2CPacket.BRAND.equals(packet.getChannel())) {
            ServerBrandManager.setServerBrand(this.client.player.getServerBrand());
        }
    }

}
