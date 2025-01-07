package net.earthcomputer.clientcommands.mixin.rngevents.droppableinventory;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MerchantMenu.class)
public class MerchantMenuMixin implements IDroppableInventoryContainer {

    @Shadow @Final private MerchantContainer tradeContainer;

    @Override
    public Container getDroppableInventory() {
        return new SimpleContainer(tradeContainer.getItem(0), tradeContainer.getItem(1));
    }
}
