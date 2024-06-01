package net.earthcomputer.clientcommands.mixin.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Shadow @Final private Minecraft minecraft;

    @ModifyConstant(method = "render", constant = @Constant(intValue = 13))
    int modifyWidth(int constant) {
        return constant + 45;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;renderPingIcon(Lnet/minecraft/client/gui/GuiGraphics;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"))
    void onRender(PlayerTabOverlay instance, GuiGraphics guiGraphics, int width, int x, int y, PlayerInfo playerInfo) {
        String ping = "%dms".formatted(playerInfo.getLatency());
        var w = minecraft.font.width(ping);
        var textX = width + x - w;
        guiGraphics.drawString(minecraft.font, ping, textX, y, 0xffffff, true);
        RenderSystem.setShaderColor(1f,1f,1f,1f);
    }
}
