package net.earthcomputer.clientcommands.mixin.commands.snap;

import net.earthcomputer.clientcommands.command.SnapCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow @Final public Options options;

    @Shadow @Nullable public LocalPlayer player;

    @Shadow @Nullable public ClientLevel level;

    @Shadow @Nullable public Entity cameraEntity;

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void clickToTeleport(CallbackInfo ci) {
        if (!SnapCommand.clickToTeleport) {
            return;
        }
        if (!Minecraft.getInstance().mouseHandler.isRightPressed()) {
            return;
        }
        Vec3 source = this.cameraEntity.getEyePosition();
        Vec3 direction = this.player.getForward();
        HitResult clip = this.level.clip(new ClipContext(source, source.add(direction.scale(Player.DEFAULT_EYE_HEIGHT + 1)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.player));
        if (clip.distanceTo(this.player) > 1) {
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.csnap.tooFar"));
            return;
        }
        if (clip.getType() == HitResult.Type.BLOCK) {
            Vec3 location = clip.getLocation();
            this.player.setPos(location);
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.csnap.success", location.x, location.y, location.z));
            ci.cancel();
        }
    }
}
