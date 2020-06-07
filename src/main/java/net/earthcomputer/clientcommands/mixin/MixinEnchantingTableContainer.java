package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.earthcomputer.clientcommands.interfaces.IEnchantingTableContainer;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantmentScreenHandler.class)
public abstract class MixinEnchantingTableContainer implements IEnchantingTableContainer, IDroppableInventoryContainer {

    @Shadow @Final private Inventory inventory;

    @Accessor
    @Override
    public abstract ScreenHandlerContext getContext();

    @Override
    public Inventory getDroppableInventory() {
        return inventory;
    }
}
