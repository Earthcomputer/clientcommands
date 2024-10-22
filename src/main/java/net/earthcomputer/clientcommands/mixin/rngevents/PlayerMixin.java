package net.earthcomputer.clientcommands.mixin.rngevents;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

    protected PlayerMixin(EntityType<? extends LivingEntity> entityType_1, Level level_1) {
        super(entityType_1, level_1);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"))
    public void onDrop(ItemStack stack, boolean randomDirection, boolean thisIsThrower, CallbackInfoReturnable<ItemEntity> ci) {
        if (isThePlayer()) {
            PlayerRandCracker.onDropItem();
        }
    }

    // TODO: update-sensitive: type hierarchy of Entity.damage
    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal = 0))
    public boolean clientSideAttackDamage(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        if (!level().isClientSide || !isThePlayer()) {
            return original.call(target, source, amount);
        }

        Player _this = (Player) (Object) this;


        // Oh God this took ages to write
        boolean canAttack = true;

        if (target instanceof ArmorStand armorStand) {
            if (armorStand.isRemoved()) {
                canAttack = false;
            } else if (armorStand.isInvulnerableToBase(source) || armorStand.invisible || armorStand.isMarker()) {
                canAttack = false;
            } else if (!_this.getAbilities().mayBuild) {
                canAttack = false;
            } else if (source.isCreativePlayer()) {
                canAttack = false;
            }
        } else if (target instanceof Player player) {
            if (player.getAbilities().invulnerable) {
                canAttack = false;
            }
        } else if (target instanceof SmallFireball || target instanceof WitherSkull) {
            canAttack = false;
        } else if (target instanceof WitherBoss wither) {
            if (wither.getInvulnerableTicks() > 0) {
                canAttack = false;
            }
        }

        if (target instanceof LivingEntity living) {
            if (living.getHealth() <= 0) {
                canAttack = false;
            }
            if (living.isDamageSourceBlocked(source)) {
                canAttack = false;
            }
            if (living.invulnerableTime >= 10 && amount <= living.lastHurt) {
                canAttack = false;
            }
        }

        if (target.isInvulnerableToBase(source)) {
            canAttack = false;
        }

        if (canAttack) {
            ItemStack heldStack = getMainHandItem();
            if (!heldStack.isEmpty() && target instanceof LivingEntity) {
                Item item = heldStack.getItem();
                if (item instanceof DiggerItem) {
                    PlayerRandCracker.onItemDamage(2, this, heldStack);
                } else if (item instanceof SwordItem || item instanceof TridentItem) {
                    PlayerRandCracker.onItemDamage(1, this, heldStack);
                }

                if (target.getType().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
                    registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.BANE_OF_ARTHROPODS).ifPresent(baneOfArthropods -> {
                        if (EnchantmentHelper.getItemEnchantmentLevel(baneOfArthropods, heldStack) > 0) {
                            PlayerRandCracker.onBaneOfArthropods();
                        }
                    });
                }
            }
        }

        return original.call(target, source, amount);
    }

    @Unique
    private boolean isThePlayer() {
        return (Object) this instanceof LocalPlayer;
    }

}
