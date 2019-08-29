package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.earthcomputer.clientcommands.interfaces.IEnchantingTableContainer;
import net.minecraft.container.BlockContext;
import net.minecraft.container.EnchantingTableContainer;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantingTableContainer.class)
public abstract class MixinEnchantingTableContainer implements IEnchantingTableContainer, IDroppableInventoryContainer {

    @Shadow @Final private Inventory inventory;

    @Accessor
    @Override
    public abstract BlockContext getContext();

    @Override
    public Inventory getDroppableInventory() {
        return inventory;
    }
}
