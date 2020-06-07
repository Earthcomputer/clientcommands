package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.CartographyTableScreenHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CartographyTableScreenHandler.class)
public class MixinCartographyTableContainer implements IDroppableInventoryContainer {

    @Override
    public Inventory getDroppableInventory() {
        return ((CartographyTableScreenHandler) (Object) this).inventory;
    }
}
