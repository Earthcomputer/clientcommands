package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.DebugRandom;
import net.earthcomputer.clientcommands.features.EntityGlowingTicket;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin; import org.spongepowered.asm.mixin.Shadow; import org.spongepowered.asm.mixin.Final; import org.spongepowered.asm.mixin.Mutable; import org.spongepowered.asm.mixin.Unique; 
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntity {

    @Shadow @Final @Mutable
    protected RandomSource random;

    @Unique
    private final List<EntityGlowingTicket> glowingTickets = new ArrayList<>(0);

    @Override
    public void addGlowingTicket(int ticks, int color) {
        glowingTickets.add(new EntityGlowingTicket(ticks, color));
    }

    @Override
    public boolean hasGlowingTicket() {
        return !glowingTickets.isEmpty();
    }

    @Unique
    private int getGlowingTicketColor() {
        return glowingTickets.isEmpty() ? 0xffffff : glowingTickets.get(glowingTickets.size() - 1).getColor();
    }

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;random:Lnet/minecraft/util/RandomSource;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void onInitRandom(EntityType<?> type, Level world, CallbackInfo ci) {
        if (type == DebugRandom.DEBUG_ENTITY_TYPE && !world.isClientSide) {
            this.random = new DebugRandom((Entity) (Object) this);
        }
    }

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void overrideIsGlowing(CallbackInfoReturnable<Boolean> ci) {
        if (!glowingTickets.isEmpty()) {
            ci.setReturnValue(Boolean.TRUE);
        }
    }

    @Override
    public void tickGlowingTickets() {
        if (((Entity) (Object) this).level().isClientSide) {
            Iterator<EntityGlowingTicket> itr = glowingTickets.iterator();
            //noinspection Java8CollectionRemoveIf
            while (itr.hasNext()) {
                EntityGlowingTicket glowingTicket = itr.next();
                if (!glowingTicket.tick()) {
                    itr.remove();
                }
            }
        }
    }

    @Override
    public void tickDebugRandom() {
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

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"))
    public void onOnSwimmingStart(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onSwimmingStart();
        }
    }

    @Inject(method = "playAmethystStepSound", at = @At("HEAD"))
    private void onPlayAmethystChimeSound(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onAmethystChime();
        }
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"))
    public void onSprinting(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onSprinting();
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    public void injectGetTeamColorValue(CallbackInfoReturnable<Integer> ci) {
        if (hasGlowingTicket()) {
            ci.setReturnValue(getGlowingTicketColor());
        }
    }

    @Unique
    private boolean isThePlayer() {
        return (Object) this instanceof LocalPlayer;
    }

    @Override
    @Invoker
    public abstract int callGetPermissionLevel();
}
