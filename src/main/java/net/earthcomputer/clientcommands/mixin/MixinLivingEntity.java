package net.earthcomputer.clientcommands.mixin;

import com.google.common.base.Objects;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.ILivingEntity;
import net.minecraft.world.level.block.state.BlockState; import net.minecraft.world.level.block.Blocks; import net.minecraft.world.level.block.FrostedIceBlock; import net.minecraft.world.phys.shapes.CollisionContext; 
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements ILivingEntity {

    @Shadow private BlockPos lastPos;

    @Shadow protected abstract boolean onSoulSpeedBlock();

    @Shadow protected int useItemRemaining;

    public MixinLivingEntity(EntityType<?> entityType_1, Level world_1) {
        super(entityType_1, world_1);
    }

    @Inject(method = "pushEntities", at = @At("HEAD"))
    public void onEntityCramming(CallbackInfo ci) {
        if (isThePlayer() && level().getEntities(this, getBoundingBox(), Entity::isPushable).size() >= 24) {
            PlayerRandCracker.onEntityCramming();
        }
    }

    @Inject(method = "triggerItemUseEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getDrinkingSound(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/sounds/SoundEvent;"))
    public void onDrink(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onDrink();
        }
    }

    @Inject(method = "triggerItemUseEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getEatingSound(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/sounds/SoundEvent;"))
    public void onEat(ItemStack stack, int particleCount, CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onEat(stack, this.position(), particleCount, this.useItemRemaining);
        }
    }

    @Inject(method = "baseTick",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/tags/FluidTags;WATER:Lnet/minecraft/tags/TagKey;", ordinal = 0)),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 0))
    public void onUnderwater(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onUnderwater();
        }
    }

    @Inject(method = "breakItem", at = @At("HEAD"))
    public void onEquipmentBreak(ItemStack stack, CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onEquipmentBreak();
        }
    }

    @Inject(method = "tickEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisible()Z"))
    public void onPotionParticles(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onPotionParticles();
        }
    }

    @Inject(method = "baseTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;isClientSide:Z", ordinal = 2))
    public void testFrostWalker(CallbackInfo ci) {
        if (!isThePlayer()) {
            return;
        }

        BlockPos pos = blockPosition();
        if (!Objects.equal(pos, this.lastPos)) {
            this.lastPos = pos;
            if (onGround()) {
                int frostWalkerLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FROST_WALKER, (LivingEntity) (Object) this);
                if (frostWalkerLevel > 0) {
                    BlockState frostedIce = Blocks.FROSTED_ICE.defaultBlockState();
                    int radius = Math.min(16, frostWalkerLevel + 2);
                    for (BlockPos offsetPos : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, -1, radius))) {
                        if (offsetPos.closerToCenterThan(position(), radius)) {
                            BlockState offsetState = level().getBlockState(offsetPos);
                            if (offsetState == FrostedIceBlock.meltsInto() && level().isUnobstructed(frostedIce, offsetPos, CollisionContext.empty())) {
                                if (level().isEmptyBlock(offsetPos.above())) {
                                    PlayerRandCracker.onFrostWalker();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;canSpawnSoulSpeedParticle()Z"))
    private void testSoulSpeed(CallbackInfo ci) {
        if (!isThePlayer()) {
            return;
        }

        boolean hasSoulSpeed = EnchantmentHelper.hasSoulSpeed((LivingEntity) (Object) this);
        if (hasSoulSpeed && onSoulSpeedBlock()) {
            PlayerRandCracker.onSoulSpeed();
        }
    }

    @Unique
    private boolean isThePlayer() {
        return (Object) this instanceof LocalPlayer;
    }

    @Accessor("lastHurt")
    @Override
    public abstract float getLastDamageTaken();

    @Invoker("isDamageSourceBlocked")
    @Override
    public abstract boolean callBlockedByShield(DamageSource damageSource);
}
