package net.earthcomputer.clientcommands.mixin.screen;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    @Nullable
    public Screen screen;

    @Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    public Options options;

    @Shadow
    public abstract boolean isEnforceUnicode();

    @Definition(id = "screen", field = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;")
    @Expression("this.screen = ?")
    @Inject(method = "setScreen", at = @At(value = "MIXINEXTRAS:EXPRESSION", shift = At.Shift.AFTER))
    private void onReplacement(Screen newScreen, CallbackInfo ci) {
        // recache required due to `IScreenSafeZone`
        window.setGuiScale(window.calculateScale(options.guiScale().get(), isEnforceUnicode()));
    }
}
