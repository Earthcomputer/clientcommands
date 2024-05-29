package net.earthcomputer.clientcommands.mixin.rngevents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.VillageBoundRandomStroll;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillageBoundRandomStroll.class)
public class VillageBoundRandomStrollMixin {
    @Inject(method = "lambda$create$1", at = @At("HEAD"))
    private static void youFuckedUp(int i, int j, MemoryAccessor memoryAccessor, float f, ServerLevel level, PathfinderMob mob, long gameTime, CallbackInfoReturnable<Boolean> cir) {
        System.out.println("rand called [x30]");
    }
}
