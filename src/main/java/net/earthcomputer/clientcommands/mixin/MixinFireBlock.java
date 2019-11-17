package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FireBlock.class)
public abstract class MixinFireBlock implements IFireBlock {

    @Invoker
    @Override
    public abstract int callGetBurnChance(BlockState state);

    @Invoker
    @Override
    public abstract int callGetSpreadChance(BlockState state);
}
