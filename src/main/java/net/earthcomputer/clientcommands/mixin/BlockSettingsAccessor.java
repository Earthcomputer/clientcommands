package net.earthcomputer.clientcommands.mixin;

import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractBlock.Settings.class)
public interface BlockSettingsAccessor {
    @Accessor
    float getHardness();
}
