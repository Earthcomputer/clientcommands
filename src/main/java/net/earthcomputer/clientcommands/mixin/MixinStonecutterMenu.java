package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.StonecutterMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StonecutterMenu.class)
public class MixinStonecutterMenu implements IDroppableInventoryContainer {

    @Override
    public Container getDroppableInventory() {
        return ((StonecutterMenu) (Object) this).container;
    }
}
