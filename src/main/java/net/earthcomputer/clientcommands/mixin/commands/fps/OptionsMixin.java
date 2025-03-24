package net.earthcomputer.clientcommands.mixin.commands.fps;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Options.class)
public class OptionsMixin {

    @ModifyArg(method = "lambda$new$8", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;setFramerateLimit(I)V"))
    private static int fixMaxFpsSet(int maxFps) {
        if (maxFps == Options.UNLIMITED_FRAMERATE_CUTOFF) {
            return Integer.MAX_VALUE;
        }
        return maxFps;
    }


}
