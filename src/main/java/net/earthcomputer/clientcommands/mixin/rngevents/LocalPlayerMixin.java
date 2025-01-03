package net.earthcomputer.clientcommands.mixin.rngevents;

import com.mojang.authlib.GameProfile;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "drop", at = @At("HEAD"))
    public void onDrop(boolean dropAll, CallbackInfoReturnable<ItemEntity> ci) {
        PlayerRandCracker.onDropItem();
    }
}
