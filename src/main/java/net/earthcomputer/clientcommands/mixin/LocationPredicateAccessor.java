package net.earthcomputer.clientcommands.mixin;

import net.minecraft.predicate.BlockPredicate;
import net.minecraft.predicate.FluidPredicate;
import net.minecraft.predicate.LightPredicate;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;
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

    @Accessor
    RegistryKey<Structure> getFeature();

    @Accessor
    RegistryKey<World> getDimension();

    @Accessor
    Boolean getSmokey();

    @Accessor
    LightPredicate getLight();

    @Accessor
    BlockPredicate getBlock();

    @Accessor
    FluidPredicate getFluid();
}
