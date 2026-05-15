package net.earthcomputer.clientcommands.mixin.debug;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.earthcomputer.clientcommands.util.DebugRandom;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class LevelMixin {
    @Shadow
    @Final
    public RandomSource random;

    @Definition(id = "random", field = "Lnet/minecraft/world/level/Level;random:Lnet/minecraft/util/RandomSource;")
    @Expression("this.random = @(?)")
    @ModifyExpressionValue(method = "<init>", at = @At("MIXINEXTRAS:EXPRESSION"))
    private RandomSource onInitRandom(RandomSource original, @Local(argsOnly = true) ResourceKey<Level> dimension) {
        if ((Object) this instanceof ServerLevel && dimension.identifier().equals(DebugRandom.DEBUG_DIMENSION)) {
            return new DebugRandom((Level) (Object) this);
        }

        return original;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (this.random instanceof DebugRandom debugRandom) {
            debugRandom.writeToFile();
        }
    }
}
