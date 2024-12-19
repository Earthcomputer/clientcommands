package net.earthcomputer.clientcommands.mixin.c2c;

import com.llamalad7.mixinextras.sugar.Local;
import net.earthcomputer.clientcommands.features.TwoPlayerGame;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handlePlayerInfoRemove", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getPlayerSocialManager()Lnet/minecraft/client/gui/screens/social/PlayerSocialManager;"))
    private void onHandlePlayerInfoRemove(CallbackInfo ci, @Local UUID uuid) {
        TwoPlayerGame.onPlayerLeave(uuid);
    }
}
