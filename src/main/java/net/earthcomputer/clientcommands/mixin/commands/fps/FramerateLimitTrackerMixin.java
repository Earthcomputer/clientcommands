package net.earthcomputer.clientcommands.mixin.commands.fps;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.earthcomputer.clientcommands.Configs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
    @ModifyExpressionValue(method = "getFramerateLimit", at = @At(value = "FIELD", target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;framerateLimit:I"))
    private int fixFps(int fps) {
        if (Configs.overriddenFps > 0) {
            return Configs.overriddenFps;
        }

        return fps;
    }

    @Inject(method = "setFramerateLimit", at = @At("HEAD"))
    private void onSetFramerateLimit(CallbackInfo ci) {
        // Avoid overriding the FPS when something else (e.g. the user) has explicitly set it
        Configs.overriddenFps = 0;
        Configs.save();
    }
}
