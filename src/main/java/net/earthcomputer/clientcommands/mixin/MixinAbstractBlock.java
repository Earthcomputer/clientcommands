package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IBlock;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractBlock.class)
public abstract class MixinAbstractBlock implements IBlock {

    @Accessor
    @Override
    public abstract AbstractBlock.Settings getSettings();
}
