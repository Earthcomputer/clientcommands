package net.earthcomputer.clientcommands.mixin;

import net.minecraft.util.math.random.CheckedRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(CheckedRandom.class)
public interface CheckedRandomAccessor {
    @Accessor
    AtomicLong getSeed();
}
