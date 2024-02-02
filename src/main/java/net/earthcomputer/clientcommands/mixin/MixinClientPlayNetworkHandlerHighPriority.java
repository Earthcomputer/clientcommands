package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.ClientCommandFunctions;
import net.earthcomputer.clientcommands.features.Relogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 2000)
public class MixinClientPlayNetworkHandlerHighPriority {
    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void postGameJoin(CallbackInfo ci) {
        if (!Relogger.onRelogSuccess()) {
            ClientCommandFunctions.runStartup();
        }
    }
}
