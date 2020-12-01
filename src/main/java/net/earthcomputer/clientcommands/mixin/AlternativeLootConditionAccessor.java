package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.condition.AlternativeLootCondition;
import net.minecraft.loot.condition.LootCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AlternativeLootCondition.class)
public interface AlternativeLootConditionAccessor {
    @Accessor
    LootCondition[] getTerms();
}
