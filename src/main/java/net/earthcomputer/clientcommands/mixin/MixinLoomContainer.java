package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.LoomContainer;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LoomContainer.class)
public class MixinLoomContainer implements IDroppableInventoryContainer {

    @Shadow @Final private Inventory inputInventory;

    @Override
    public Inventory getDroppableInventory() {
        return inputInventory;
    }
}
