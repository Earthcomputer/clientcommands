package net.earthcomputer.clientcommands.mixin.debug;

import net.earthcomputer.clientcommands.features.DebugRandom;
import net.earthcomputer.clientcommands.interfaces.IEntity_Debug;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntity_Debug {

    @Shadow
    @Final
    @Mutable
    protected RandomSource random;

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;random:Lnet/minecraft/util/RandomSource;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void onInitRandom(EntityType<?> type, Level level, CallbackInfo ci) {
        if (type == DebugRandom.DEBUG_ENTITY_TYPE && !level.isClientSide) {
            this.random = new DebugRandom((Entity) (Object) this);
        }
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
