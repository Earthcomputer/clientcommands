package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.*;
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
        if (isThePlayer()) {
            PlayerRandCracker.onDropItem();
        }
    }

    // TODO: update-sensitive: type hierarchy of Entity.damage
    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", ordinal = 0))
    public boolean clientSideAttackDamage(Entity target, DamageSource source, float amount) {
        if (!getWorld().isClient || !isThePlayer()) {
            return target.damage(source, amount);
        }

        PlayerEntity _this = (PlayerEntity) (Object) this;


        // Oh God this took ages to write
        boolean canAttack = true;

        if (target instanceof ArmorStandEntity armorStand) {
            if (armorStand.isRemoved()) {
                canAttack = false;
            } else if (armorStand.isInvulnerableTo(source) || ((IArmorStandEntity) armorStand).isArmorStandInvisible() || armorStand.isMarker()) {
                canAttack = false;
            } else if (!_this.getAbilities().allowModifyWorld) {
                canAttack = false;
            } else if (source.isSourceCreativePlayer()) {
                canAttack = false;
            }
        } else if (target instanceof PlayerEntity player) {
            if (player.getAbilities().invulnerable) {
                canAttack = false;
            }
        } else if (target instanceof SmallFireballEntity || target instanceof WitherSkullEntity) {
            canAttack = false;
        } else if (target instanceof WitherEntity wither) {
            if (wither.getInvulnerableTimer() > 0) {
                canAttack = false;
            }
        }

        if (target instanceof LivingEntity living) {
            if (living.getHealth() <= 0) {
                canAttack = false;
            }
            if (((ILivingEntity) living).callBlockedByShield(source)) {
                canAttack = false;
            }
            if (living.timeUntilRegen >= 10 && amount <= ((ILivingEntity) living).getLastDamageTaken()) {
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
                    PlayerRandCracker.onItemDamage(2, this, heldStack);
                } else if (item instanceof SwordItem || item instanceof TridentItem) {
                    PlayerRandCracker.onItemDamage(1, this, heldStack);
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
