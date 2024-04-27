package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemCombinerMenu.class)
public class ItemCombinerMenuMixin implements IDroppableInventoryContainer {

    @Shadow @Final protected Container inputSlots;

    @Override
    public Container getDroppableInventory() {
        return inputSlots;
    }
}
