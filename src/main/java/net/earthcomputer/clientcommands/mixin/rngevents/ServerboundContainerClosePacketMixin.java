package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.earthcomputer.clientcommands.util.SimulatedPlayerInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerboundContainerClosePacket.class)
public class ServerboundContainerClosePacketMixin {

    @Inject(method = "<init>(I)V", at = @At("RETURN"))
    public void onCreate(int syncId, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;

        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        AbstractContainerMenu menu = player.containerMenu;
        SimulatedPlayerInventory inventory = new SimulatedPlayerInventory(player.getInventory());

        // the carried item has been placed back in the inventory since 1.17.1, rather than dropped
        if (!menu.getCarried().isEmpty()) {
            if (protocolVersion < MultiVersionCompat.V1_17_1 || !inventory.placeItemBackInInventory(menu.getCarried())) {
                PlayerRandCracker.onDropItem();
            }
        }

        if (menu instanceof IDroppableInventoryContainer droppableInventoryContainer) {
            // items have been placed back in the inventory since 1.12 (1.14 for villagers), rather than dropped
            int placeBackVersion = menu instanceof MerchantMenu ? MultiVersionCompat.V1_14 : MultiVersionCompat.V1_12;
            Container toDrop = droppableInventoryContainer.getDroppableInventory();
            for (int slot = 0; slot < toDrop.getContainerSize(); slot++) {
                ItemStack stack = toDrop.getItem(slot);
                if (!stack.isEmpty()) {
                    if (protocolVersion < placeBackVersion || !inventory.placeItemBackInInventory(stack)) {
                        PlayerRandCracker.onDropItem();
                    }
                }
            }
        }

        // the beacon payment item is always dropped
        if (menu instanceof BeaconMenu && menu.getSlot(0).hasItem()) {
            PlayerRandCracker.onDropItem();
        }
    }
}
