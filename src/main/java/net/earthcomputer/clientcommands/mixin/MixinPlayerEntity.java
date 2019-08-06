package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.interfaces.IArmorStandEntity;
import net.earthcomputer.clientcommands.interfaces.ILivingEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity extends LivingEntity {

    protected MixinPlayerEntity(EntityType<? extends LivingEntity> entityType_1, World world_1) {
        super(entityType_1, world_1);
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"))
    public void onDropItem(ItemStack stack, boolean randomDirection, boolean thisIsThrower, CallbackInfoReturnable<ItemEntity> ci) {
        if (isThePlayer())
            EnchantmentCracker.onDropItem();
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", ordinal = 0))
    public boolean clientSideAttackDamage(Entity target, DamageSource source, float amount) {
        if (!world.isClient || !isThePlayer())
            return target.damage(source, amount);

        PlayerEntity _this = (PlayerEntity) (Object) this;


        // Oh God this took ages to write
        boolean canAttack = true;

        if (target instanceof ArmorStandEntity) {
            ArmorStandEntity armorStand = (ArmorStandEntity) target;
            if (armorStand.removed) {
                canAttack = false;
            } else if (armorStand.isInvulnerableTo(source) || ((IArmorStandEntity) armorStand).getField_7111() || armorStand.isMarker()) {
                canAttack = false;
            } else if (!_this.abilities.allowModifyWorld) {
                canAttack = false;
            } else if (source.isSourceCreativePlayer()) {
                canAttack = false;
            }
        } else if (target instanceof HorseBaseEntity) {
            if (target.hasPassengers() && target.hasPassengerDeep(_this)) {
                canAttack = false;
            }
        } else if (target instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) target;
            if (player.abilities.invulnerable) {
                canAttack = false;
            }
        } else if (target instanceof SmallFireballEntity || target instanceof WitherSkullEntity) {
            canAttack = false;
        } else if (target instanceof WitherEntity) {
            WitherEntity wither = (WitherEntity) target;
            if (wither.getInvulTimer() > 0) {
                canAttack = false;
            }
        }

        if (target instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) target;
            if (living.getHealth() <= 0) {
                canAttack = false;
            }
            if (living.method_6039()) { // isBlockingWithShield
                // check if the attack vector is broadly in the opposite direction to the shield
                Vec3d attackVector = source.method_5510();
                if (attackVector != null) {
                    Vec3d livingRotation = this.getRotationVec(1.0F);
                    Vec3d rotationDelta = attackVector.reverseSubtract(new Vec3d(this.x, this.y, this.z)).normalize();
                    rotationDelta = new Vec3d(rotationDelta.x, 0.0D, rotationDelta.z);
                    if (rotationDelta.dotProduct(livingRotation) < 0.0D) {
                        canAttack = false;
                    }
                }
            }
            if (living.timeUntilRegen >= 10 && amount <= ((ILivingEntity) living).getLastDamage()) {
                canAttack = false;
            }
        }

        if (target.isInvulnerableTo(source)) {
            canAttack = false;
        }

        if (canAttack) {
            ItemStack heldStack = getMainHandStack();
            if (!heldStack.isEmpty() && target instanceof LivingEntity) {
                Item item = heldStack.getItem();
                if (item instanceof MiningToolItem) {
                    EnchantmentCracker.onItemDamage(2, this, heldStack);
                } else if (item instanceof HoeItem || item instanceof SwordItem || item instanceof TridentItem) {
                    EnchantmentCracker.onItemDamage(1, this, heldStack);
                }
            }
        }

        return target.damage(source, amount);
    }

    private boolean isThePlayer() {
        //noinspection ConstantConditions
        return (Object) this instanceof ClientPlayerEntity;
    }

}
