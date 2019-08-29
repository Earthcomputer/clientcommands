package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.AnvilContainer;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AnvilContainer.class)
public class MixinAnvilContainer implements IDroppableInventoryContainer {

    @Shadow @Final private Inventory inventory;

    @Override
    public Inventory getDroppableInventory() {
        return inventory;
    }
}
