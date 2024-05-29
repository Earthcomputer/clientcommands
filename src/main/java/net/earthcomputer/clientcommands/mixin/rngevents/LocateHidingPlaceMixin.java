package net.earthcomputer.clientcommands.mixin.rngevents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.ai.behavior.LocateHidingPlace;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocateHidingPlace.class)
public class LocateHidingPlaceMixin {
    @Redirect(method = "lambda$create$2", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private static boolean isCloseEnough(BlockPos instance, Position position, double v) {
        boolean result = instance.closerToCenterThan(position, v);
//        System.out.println("Close enough? " + result);
//        if (!result) {
//            System.out.println("BlockPos: " + instance + ", Position: " + new Vec3(position.x(), position.y(), position.z()) + ", Distance: " + v);
//        }
        return result;
    }
}
