package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.IEnchantingTableContainer;
import net.minecraft.container.BlockContext;
import net.minecraft.container.EnchantingTableContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantingTableContainer.class)
public abstract class MixinEnchantingTableContainer implements IEnchantingTableContainer {

    @Accessor
    @Override
    public abstract BlockContext getContext();
}
