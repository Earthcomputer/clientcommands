package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.condition.InvertedLootCondition;
import net.minecraft.loot.condition.LootCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InvertedLootCondition.class)
public interface InvertedLootConditionAccessor {
    @Accessor
    LootCondition getTerm();
}
