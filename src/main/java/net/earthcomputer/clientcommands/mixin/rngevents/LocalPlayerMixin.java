package net.earthcomputer.clientcommands.mixin.rngevents;

import com.mojang.authlib.GameProfile;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() >= MultiVersionCompat.V1_21_9) {
            if (this.getShoulderParrotLeft().isPresent() || this.getShoulderParrotRight().isPresent()) {
                PlayerRandCracker.resetCracker(PlayerRandCracker.RNGCallType.SHOULDER_PARROT);
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"))
    public void onDrop(boolean dropAll, CallbackInfoReturnable<ItemEntity> ci) {
        PlayerRandCracker.onDropItem();
    }
}
