package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.TraderInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MerchantScreenHandler.class)
public class MixinMerchantContainer implements IDroppableInventoryContainer {

    @Shadow @Final private TraderInventory traderInventory;

    @Override
    public Inventory getDroppableInventory() {
        return new SimpleInventory(traderInventory.getStack(0), traderInventory.getStack(1));
    }
}
