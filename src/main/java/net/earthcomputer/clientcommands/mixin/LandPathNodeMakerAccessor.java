package net.earthcomputer.clientcommands.mixin;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WalkNodeEvaluator.class)
public interface LandPathNodeMakerAccessor {
    @Invoker("isNeighborValid")
    boolean callIsValidAdjacentSuccessor(Node node, Node successor);
}
