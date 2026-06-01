package net.earthcomputer.clientcommands.mixin.screen;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.Window;
import net.earthcomputer.clientcommands.interfaces.ISafeZoneScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Window.class)
public class WindowMixin {
    @ModifyExpressionValue(method = "calculateScale", at = @At(value = "CONSTANT", args = "intValue=" + Window.BASE_WIDTH))
    private int getSafeZoneWidth(int original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ISafeZoneScreen safeZone) {
            original = Math.max(original, safeZone.getSafeZoneWidth());
        }
        return original;
    }

    @ModifyExpressionValue(method = "calculateScale", at = @At(value = "CONSTANT", args = "intValue=" + Window.BASE_HEIGHT))
    private int getSafeZoneHeight(int original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ISafeZoneScreen safeZone) {
            original = Math.max(original, safeZone.getSafeZoneHeight());
        }
        return original;
    }
}
