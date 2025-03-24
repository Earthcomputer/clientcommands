package net.earthcomputer.clientcommands.mixin.commands.fps;

import net.earthcomputer.clientcommands.command.FramerateCommand;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @ModifyConstant(method = "runTick", constant = @Constant(intValue = 260))
    private int uncapFps(int original) {
        return FramerateCommand.MAX_FRAMERATE + 1;
    }
}
