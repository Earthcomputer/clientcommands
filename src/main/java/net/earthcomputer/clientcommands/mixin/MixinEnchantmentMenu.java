package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IDroppableInventoryContainer;
import net.earthcomputer.clientcommands.interfaces.IEnchantmentMenu;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantmentMenu.class)
public abstract class MixinEnchantmentMenu implements IEnchantmentMenu, IDroppableInventoryContainer {

    @Shadow @Final private Container enchantSlots;

    @Accessor("access")
    @Override
    public abstract ContainerLevelAccess getContext();

    @Override
    public Container getDroppableInventory() {
        return enchantSlots;
    }
}
