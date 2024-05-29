package net.earthcomputer.clientcommands.mixin.commands.findblock;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.earthcomputer.clientcommands.event.ClientLevelEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {

    @Shadow @Final private Level level;

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState onSetBlockState(LevelChunkSection instance, int x, int y, int z, BlockState state, Operation<BlockState> original, BlockPos pos, BlockState redundant, boolean isMoving) {
        BlockState oldState = original.call(instance, x, y, z, state);
        if (level.isClientSide) {
            ClientLevelEvents.CHUNK_UPDATE.invoker().onBlockStateUpdate((ClientLevel) level, pos, oldState, state);
        }
        return oldState;
    }
}
