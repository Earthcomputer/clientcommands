package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.function.Function;

@Mixin(LootContext.class)
public interface LootContextAccessor {
    @Invoker("<init>")
    static LootContext createLootContext(Random random,
                                         float luck,
                                         ServerWorld world,
                                         Function<Identifier, LootTable> tableGetter,
                                         Function<Identifier, LootCondition> predicateGetter,
                                         Map<LootContextParameter<?>, Object> parameters,
                                         Map<Identifier, LootContext.Dropper> drops) {
        throw new UnsupportedOperationException();
    }
}
