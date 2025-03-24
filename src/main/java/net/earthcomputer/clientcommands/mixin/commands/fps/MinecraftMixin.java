package net.earthcomputer.clientcommands.mixin.commands.fps;

import net.earthcomputer.clientcommands.command.FramerateCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @ModifyConstant(method = "runTick", constant = @Constant(intValue = Options.UNLIMITED_FRAMERATE_CUTOFF))
    private int changeCutoff(int original) {
        return FramerateCommand.MAX_REFRESH_RATE.getAsInt();
    }
}
