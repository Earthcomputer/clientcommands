package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.condition.LocationCheckLootCondition;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocationCheckLootCondition.class)
public interface LocationCheckLootConditionAccessor {
    @Accessor
    LocationPredicate getPredicate();

    @Accessor
    BlockPos getOffset();
}
