package net.earthcomputer.clientcommands.mixin.rngevents;

import com.llamalad7.mixinextras.sugar.Local;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "removeEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;onClientRemoval()V"))
    private void onRemoveEntity(int entityId, Entity.RemovalReason reason, CallbackInfo ci, @Local Entity entity) {
        if (entity instanceof ExperienceOrb) {
            LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            if (player.getBoundingBox().inflate(1.25, 0.75, 1.25).intersects(entity.getBoundingBox())) {
                PlayerRandCracker.onXpOrb();
                if (Arrays.stream(EquipmentSlot.values()).anyMatch(slot -> couldMendingRepair(player.getItemBySlot(slot)))) {
                    PlayerRandCracker.onMending();
                }
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
}
