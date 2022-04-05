package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.condition.LootCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.loot.condition.LootConditionManager$AndCondition")
public interface AndConditionAccessor {
    @Accessor
    LootCondition[] getTerms();
}
