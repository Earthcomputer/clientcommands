package net.earthcomputer.clientcommands.mixin;

import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocationPredicate.class)
public interface LocationPredicateAccessor {
    @Accessor("x")
    NumberRange.FloatRange getX();

    @Accessor("y")
    NumberRange.FloatRange getY();

    @Accessor("z")
    NumberRange.FloatRange getZ();

    @Accessor
    RegistryKey<Biome> getBiome();
}
