package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.IServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommandSource.class)
public abstract class MixinServerCommandSource implements IServerCommandSource {

    @Accessor
    @Override
    public abstract int getLevel();

}
