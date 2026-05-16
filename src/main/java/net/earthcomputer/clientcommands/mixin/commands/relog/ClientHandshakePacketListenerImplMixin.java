package net.earthcomputer.clientcommands.mixin.commands.relog;

import com.llamalad7.mixinextras.sugar.Local;
import net.earthcomputer.clientcommands.features.Relogger;
import net.earthcomputer.clientcommands.interfaces.IDisconnectedScreen;
import net.fabricmc.fabric.impl.client.screen.ScreenExtensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;

@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerImplMixin {
    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "lambda$handleHello$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/chat/Component;)V", shift = At.Shift.AFTER))
    private void onRelogFail(String digest, ServerboundKeyPacket setKeyPacket, Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci, @Local(name = "error") Component error) {
        if (!Relogger.isRelogging) {
            return;
        }

        if (!Relogger.isRateLimitMessage(error)) {
            Relogger.isRelogging = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        mc.executeBlocking(() -> {
            connection.handleDisconnection();
            if (mc.screen instanceof DisconnectedScreen screen && Relogger.isRateLimitMessage(screen.details.reason())) {
                ((IDisconnectedScreen) screen).clientcommands_setCountdownMs(Relogger.RELOG_RETRY_DELAY_MS);
                ScreenExtensions.getExtensions(screen).fabric_getAfterRenderEvent().register(Relogger::onDisconnectScreenRender);
            }
        });

        new Thread(() -> {
            try {
                Thread.sleep(Relogger.RELOG_RETRY_DELAY_MS);
                mc.execute(Relogger::onFailedRelog);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}
