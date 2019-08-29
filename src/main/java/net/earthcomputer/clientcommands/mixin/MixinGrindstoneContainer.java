package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.GrindstoneContainer;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GrindstoneContainer.class)
public class MixinGrindstoneContainer implements IDroppableInventoryContainer {

    @Shadow @Final private Inventory craftingInventory;

    @Override
    public Inventory getDroppableInventory() {
        return craftingInventory;
    }
}
