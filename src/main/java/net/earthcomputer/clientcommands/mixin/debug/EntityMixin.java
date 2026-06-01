package net.earthcomputer.clientcommands.mixin.debug;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.earthcomputer.clientcommands.interfaces.IEntity_Debug;
import net.earthcomputer.clientcommands.util.DebugRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntity_Debug {

    @Shadow
    @Final
    protected RandomSource random;

    @Definition(id = "random", field = "Lnet/minecraft/world/entity/Entity;random:Lnet/minecraft/util/RandomSource;")
    @Expression("this.random = @(?)")
    @ModifyExpressionValue(method = "<init>", at = @At("MIXINEXTRAS:EXPRESSION"))
    private RandomSource onInitRandom(RandomSource original, EntityType<?> type, Level level) {
        if (type == DebugRandom.DEBUG_ENTITY_TYPE && !level.isClientSide()) {
            return new DebugRandom((Entity) (Object) this);
        }

        return original;
    }

    @Override
    public void clientcommands_tickDebugRandom() {
        if (this.random instanceof DebugRandom debugRandom) {
            debugRandom.tick();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void onRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if (this.random instanceof DebugRandom debugRandom && (!((Object) this instanceof Player) || reason != Entity.RemovalReason.CHANGED_DIMENSION)) {
            debugRandom.writeToFile();
        }
    }
}
