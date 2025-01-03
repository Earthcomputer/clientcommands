package net.earthcomputer.clientcommands.mixin.rngevents;

import com.google.common.base.Objects;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.util.CUtil;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow
    public abstract boolean isAlive();

    @Unique
    private BlockPos clientLastPos;

    public LivingEntityMixin(EntityType<?> entityType_1, Level level_1) {
        super(entityType_1, level_1);
    }

    @Inject(method = "pushEntities", at = @At("HEAD"))
    private void onEntityCramming(CallbackInfo ci) {
        if (isThePlayer() && level().getEntities(this, getBoundingBox(), Entity::isPushable).size() >= 24) {
            PlayerRandCracker.onEntityCramming();
        }
    }

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAlive()Z", ordinal = 0))
    private void onUnderwater(CallbackInfo ci) {
        if (isThePlayer() && isAlive() && isEyeInFluid(FluidTags.WATER)) {
            PlayerRandCracker.onUnderwater();
        }
    }

    @Inject(method = "breakItem", at = @At("HEAD"))
    private void onEquipmentBreak(ItemStack stack, CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onEquipmentBreak();
        }
    }

    @Inject(method = "onEquipItem", at = @At("HEAD"))
    private void onOnEquipItem(EquipmentSlot slot, ItemStack oldItem, ItemStack newItem, CallbackInfo ci) {
        if (isThePlayer()) {
            boolean emptySlotClickWithEmpty = newItem.isEmpty() && oldItem.isEmpty();
            if (!emptySlotClickWithEmpty && !ItemStack.isSameItemSameComponents(oldItem, newItem) && !firstTick) {
                Equippable equippable = newItem.get(DataComponents.EQUIPPABLE);
                if (equippable != null && slot == equippable.slot()) {
                    PlayerRandCracker.onEquipItem();
                }
            }
        }
    }

    @Inject(method = "updateFallFlying", at = @At("HEAD"))
    private void onUpdateFallFlying(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onFallFlying();
        }
    }

    @Inject(method = "tickEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisible()Z"))
    private void onPotionParticles(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onPotionParticles();
        }
    }

    @Inject(method = "baseTick", at = @At("RETURN"))
    private void testFrostWalker(CallbackInfo ci) {
        if (!isThePlayer()) {
            return;
        }

        if (MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_21) {
            BlockPos pos = blockPosition();
            if (!Objects.equal(pos, this.clientLastPos)) {
                this.clientLastPos = pos;
                if (onGround()) {
                    int frostWalkerLevel = CUtil.getEnchantmentLevel(Enchantments.FROST_WALKER, (LivingEntity) (Object) this);
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
    }

    @Inject(method = "baseTick", at = @At("RETURN"))
    private void testSoulSpeed(CallbackInfo ci) {
        if (!isThePlayer()) {
            return;
        }

        boolean hasSoulSpeed = CUtil.getEnchantmentLevel(Enchantments.SOUL_SPEED, (LivingEntity) (Object) this) > 0;
        if (hasSoulSpeed && level().getBlockState(getBlockPosBelowThatAffectsMyMovement()).is(BlockTags.SOUL_SPEED_BLOCKS)) {
            PlayerRandCracker.onSoulSpeed();
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void onHandleDamageEvent(CallbackInfo ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onDamage();
        }
    }

    @Unique
    private boolean isThePlayer() {
        return (Object) this instanceof LocalPlayer;
    }
}
