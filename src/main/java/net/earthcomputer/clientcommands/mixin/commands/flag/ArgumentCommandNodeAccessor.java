package net.earthcomputer.clientcommands.mixin.commands.flag;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArgumentCommandNode.class)
public interface ArgumentCommandNodeAccessor<T> {
    @Accessor("type")
    ArgumentType<T> getArgumentType();

    @Accessor("type")
    @Mutable
    @Final
    void setArgumentType(ArgumentType<?> type);
}
