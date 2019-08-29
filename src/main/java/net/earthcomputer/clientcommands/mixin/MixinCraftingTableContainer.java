package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.CraftingTableContainer;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CraftingTableContainer.class)
public class MixinCraftingTableContainer implements IDroppableInventoryContainer {

    @Shadow @Final private CraftingInventory craftingInv;

    @Override
    public Inventory getDroppableInventory() {
        return craftingInv;
    }
}
