package net.earthcomputer.clientcommands.mixin;

import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LandPathNodeMaker.class)
public interface LandPathNodeMakerAccessor {
    @Invoker
    boolean callIsValidAdjacentSuccessor(PathNode node, PathNode successor);
}
