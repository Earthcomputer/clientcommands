package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.minecraft.container.PlayerContainer;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerContainer.class)
public class MixinPlayerContainer implements IDroppableInventoryContainer {

    @Shadow @Final private CraftingInventory invCrafting;

    @Override
    public Inventory getDroppableInventory() {
        return invCrafting;
    }
}
