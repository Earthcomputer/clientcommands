package net.earthcomputer.clientcommands.mixin.rngevents.droppableinventory;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin implements IDroppableInventoryContainer {

    @Shadow @Final private CraftingContainer craftSlots;

    @Override
    public Container getDroppableInventory() {
        return craftSlots;
    }
}
