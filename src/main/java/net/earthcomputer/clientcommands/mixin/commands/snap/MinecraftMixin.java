package net.earthcomputer.clientcommands.mixin.commands.snap;

import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.SnapCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    @Nullable
    public ClientLevel level;

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 0), cancellable = true)
    private void clickToTeleport(CallbackInfo ci) {
        if (!SnapCommand.clickToTeleport) {
            return;
        }
        if (this.hitResult == null || this.level == null || this.player == null) {
            return;
        }
        if (this.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockHitResult blockHitResult = (BlockHitResult) this.hitResult;
        Vec3 location = blockHitResult.getLocation();

        if (location.distanceToSqr(this.player.position()) > 1) {
            ClientCommandHelper.sendError(Component.translatable("commands.csnap.tooFar"));
            return;
        }
        if (blockHitResult.getDirection() != Direction.UP) {
            ClientCommandHelper.sendError(Component.translatable("commands.csnap.wall"));
            return;
        }
        if (!SnapCommand.canStay(this.player, location)) {
            ClientCommandHelper.sendError(Component.translatable("commands.csnap.cannotFit"));
            return;
        }
        if (!this.player.onGround() && !this.player.isSwimming()) {
            ClientCommandHelper.sendError(Component.translatable("commands.csnap.airborne"));
            return;
        }

        this.player.setPos(location);
        ClientCommandHelper.sendFeedback(Component.translatable("commands.csnap.success", location.x, location.y, location.z));
        ci.cancel();
    }
}
