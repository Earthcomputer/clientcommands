package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.LoomScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LoomScreenHandler.class)
public class MixinLoomContainer implements IDroppableInventoryContainer {

    @Shadow @Final private Inventory input;

    @Override
    public Inventory getDroppableInventory() {
        return input;
    }
}
