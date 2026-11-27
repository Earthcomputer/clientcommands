package net.earthcomputer.clientcommands.mixin.commands.serverseed;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleContainerSetData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;setData(II)V", shift = At.Shift.AFTER))
    private void onContainerData(ClientboundContainerSetDataPacket packet, CallbackInfo ci, @Local Player player) {
        if (player.containerMenu instanceof EnchantmentMenu menu) {
            player.enchantmentSeed = menu.getEnchantmentSeed();
        }
    }
}
