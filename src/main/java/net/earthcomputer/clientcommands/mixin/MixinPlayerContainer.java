package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(InventoryMenu.class)
public class MixinPlayerContainer implements IDroppableInventoryContainer {

    @Shadow @Final private CraftingContainer craftSlots;

    @Override
    public Container getDroppableInventory() {
        return craftSlots;
    }
}
