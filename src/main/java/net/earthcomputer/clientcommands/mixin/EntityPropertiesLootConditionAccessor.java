package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.condition.EntityPropertiesLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.predicate.entity.EntityPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPropertiesLootCondition.class)
public interface EntityPropertiesLootConditionAccessor {
    @Accessor
    EntityPredicate getPredicate();

    @Accessor
    LootContext.EntityTarget getEntity();
}
