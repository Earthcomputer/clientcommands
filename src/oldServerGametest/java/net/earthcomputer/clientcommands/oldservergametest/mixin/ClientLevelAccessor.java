package net.earthcomputer.clientcommands.oldservergametest.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Deque;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Accessor("lightUpdateQueue")
    Deque<Runnable> getLightUpdateQueue();
}
