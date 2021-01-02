package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Predicate;

@Mixin(LootPoolEntry.class)
public interface LootPoolEntryAccessor {
    @Accessor
    LootCondition[] getConditions();

    @Mutable
    @Accessor
    void setConditionPredicate(Predicate<LootContext> conditionPredicate);
}
