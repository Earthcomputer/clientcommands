package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CartographyTableMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CartographyTableMenu.class)
public class CartographyTableMenuMixin implements IDroppableInventoryContainer {

    @Override
    public Container getDroppableInventory() {
        return ((CartographyTableMenu) (Object) this).container;
    }
}
