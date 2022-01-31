package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GrindstoneScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GrindstoneScreenHandler.class)
public class MixinGrindstoneContainer implements IDroppableInventoryContainer {

    @Shadow @Final Inventory input;

    @Override
    public Inventory getDroppableInventory() {
        return input;
    }
}
