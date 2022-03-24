package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.condition.WeatherCheckLootCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WeatherCheckLootCondition.class)
public interface WeatherCheckLootConditionAccessor {
    @Accessor
    Boolean getRaining();

    @Accessor
    Boolean getThundering();
}
