package net.earthcomputer.clientcommands.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.cortex.clientAddon.cracker.SeedCracker;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
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
    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(MinecraftClient mc, Screen screen, ClientConnection connection, GameProfile profile, CallbackInfo ci) {
        ClientCommands.registerCommands((CommandDispatcher<ServerCommandSource>) (Object) commandDispatcher);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "onCommandTree", at = @At("TAIL"))
    public void onOnCommandTree(CommandTreeS2CPacket packet, CallbackInfo ci) {
        ClientCommands.registerCommands((CommandDispatcher<ServerCommandSource>) (Object) commandDispatcher);
    }

    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
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
