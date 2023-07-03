package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.PlayerScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerScreenHandler.class)
public class MixinPlayerContainer implements IDroppableInventoryContainer {

    @Shadow @Final private RecipeInputInventory craftingInput;

    @Override
    public Inventory getDroppableInventory() {
        return craftingInput;
    }
}
