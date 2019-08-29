package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.MerchantContainer;
import net.minecraft.inventory.BasicInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.village.TraderInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MerchantContainer.class)
public class MixinMerchantContainer implements IDroppableInventoryContainer {

    @Shadow @Final private TraderInventory traderInventory;

    @Override
    public Inventory getDroppableInventory() {
        return new BasicInventory(traderInventory.getInvStack(0), traderInventory.getInvStack(1));
    }
}
