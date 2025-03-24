package net.earthcomputer.clientcommands.mixin.commands.fps;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @ModifyExpressionValue(method = "runTick", at = @At(value = "CONSTANT", args = "intValue=" + Options.UNLIMITED_FRAMERATE_CUTOFF))
    private int fixMaxFps(int fps) {
        return Integer.MAX_VALUE;
    }

}
