package net.earthcomputer.clientcommands.mixin.rngevents;

import com.mojang.authlib.GameProfile;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"))
    private void onTick(CallbackInfo ci) {
        if (!level().getEntitiesOfClass(ExperienceOrb.class, getBoundingBox().inflate(0.5), entity -> true).isEmpty()) {
            PlayerRandCracker.onXpOrb();
            if (Arrays.stream(EquipmentSlot.values()).anyMatch(slot -> couldMendingRepair(getItemBySlot(slot)))) {
                PlayerRandCracker.onMending();
            }
        }
    }

    @Unique
    private boolean couldMendingRepair(ItemStack stack) {
        if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP)) {
            return false;
        }
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() <= MultiVersionCompat.V1_15_2) {
            return true; // xp may try to mend items even if they're fully repaired pre-1.16
        }
        return stack.isDamaged();
    }

    @Inject(method = "drop", at = @At("HEAD"))
    public void onDrop(boolean dropAll, CallbackInfoReturnable<ItemEntity> ci) {
        PlayerRandCracker.onDropItem();
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    public void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onDamage();
    }
}
