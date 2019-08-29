package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.StonecutterContainer;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StonecutterContainer.class)
public class MixinStonecutterContainer implements IDroppableInventoryContainer {

    @Override
    public Inventory getDroppableInventory() {
        return ((StonecutterContainer) (Object) this).inventory;
    }
}
