package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IBlock;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Block.class)
public abstract class MixinBlock implements IBlock {

    @Accessor
    @Override
    public abstract float getHardness();
}
