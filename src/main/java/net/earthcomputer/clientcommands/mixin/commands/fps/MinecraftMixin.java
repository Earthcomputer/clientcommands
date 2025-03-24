package net.earthcomputer.clientcommands.mixin.commands.fps;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    // make sure if the fps isn't our new unlimited value, then set it to 259 so that it still gets limited
    @ModifyExpressionValue(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;getFramerateLimit()I"))
    private int higherFpsCap(int original, @Share("realMaxFps") LocalIntRef maxFps) {
        maxFps.set(original);
        return original > 260 && original != Integer.MAX_VALUE ? 259 : original;
    }

    // set the limiter to the real max fps
    @ModifyArg(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V", remap = false))
    private int setToRealFps(int fps, @Share("realMaxFps") LocalIntRef maxFps) {
        return maxFps.get();
    }

}
