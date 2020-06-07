package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.StonecutterScreenHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StonecutterScreenHandler.class)
public class MixinStonecutterContainer implements IDroppableInventoryContainer {

    @Override
    public Inventory getDroppableInventory() {
        return ((StonecutterScreenHandler) (Object) this).input;
    }
}
