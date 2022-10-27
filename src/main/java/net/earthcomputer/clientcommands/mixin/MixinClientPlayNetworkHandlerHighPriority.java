package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.ClientCommandFunctions;
import net.earthcomputer.clientcommands.features.Relogger;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPlayNetworkHandler.class, priority = 2000)
public class MixinClientPlayNetworkHandlerHighPriority {
    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void postGameJoin(CallbackInfo ci) {
        if (!Relogger.onRelogSuccess()) {
            ClientCommandFunctions.runStartup();
        }
    }
}
