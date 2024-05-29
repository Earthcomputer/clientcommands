package net.earthcomputer.clientcommands.mixin.commands.findblock;

import net.earthcomputer.clientcommands.event.ClientLevelEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "unload", at = @At("HEAD"))
    private void onChunkUnload(LevelChunk chunk, CallbackInfo ci) {
        ClientLevelEvents.UNLOAD_CHUNK.invoker().onUnloadChunk((ClientLevel) (Object) this, chunk.getPos());
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void onChunkLoad(ChunkPos chunkPos, CallbackInfo ci) {
        ClientLevelEvents.LOAD_CHUNK.invoker().onLoadChunk((ClientLevel) (Object) this, chunkPos);
    }
}
