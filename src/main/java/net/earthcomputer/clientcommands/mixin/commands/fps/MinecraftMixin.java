package net.earthcomputer.clientcommands.mixin.commands.fps;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.earthcomputer.clientcommands.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @ModifyExpressionValue(method = "renderFrame", at = @At(value = "CONSTANT", args = "intValue=" + Options.UNLIMITED_FRAMERATE_CUTOFF))
    private int fixMaxFps(int fps) {
        if (Configs.overriddenFps > 0) {
            return Integer.MAX_VALUE;
        }

        return fps;
    }
}
