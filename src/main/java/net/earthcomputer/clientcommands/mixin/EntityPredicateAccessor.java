package net.earthcomputer.clientcommands.mixin;

import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.FishingHookPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPredicate.class)
public interface EntityPredicateAccessor {
    @Accessor
    FishingHookPredicate getFishingHook();
}
