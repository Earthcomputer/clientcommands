package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.CartographyTableContainer;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CartographyTableContainer.class)
public class MixinCartographyTableContainer implements IDroppableInventoryContainer {

    @Override
    public Inventory getDroppableInventory() {
        return ((CartographyTableContainer) (Object) this).inventory;
    }
}
