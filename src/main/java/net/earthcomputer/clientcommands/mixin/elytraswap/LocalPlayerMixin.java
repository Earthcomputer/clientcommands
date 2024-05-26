package net.earthcomputer.clientcommands.mixin.elytraswap;

import com.mojang.authlib.GameProfile;
import net.earthcomputer.clientcommands.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    public Input input;
    @Unique
    private boolean releasedJumpKey = false;

    public LocalPlayerMixin(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private void swapToElytra(CallbackInfo ci) {
        if (!Configs.elytraSwap) {
            return;
        }

        ItemStack chestStack = this.getItemBySlot(EquipmentSlot.CHEST);
        boolean shouldFallFlying = !onGround() && !isFallFlying() && !isInWater() && !hasEffect(MobEffects.LEVITATION) && !isPassenger() && !onClimbable();
        if (!chestStack.is(Items.ELYTRA) && shouldFallFlying) {
            if (trySwap(stack -> stack.getItem() instanceof ElytraItem && ElytraItem.isFlyEnabled(stack))) {
                releasedJumpKey = false;
            }
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void swapFromElytra(CallbackInfo ci) {
        if (!Configs.elytraSwap) {
            return;
        }

        ItemStack chestStack = getItemBySlot(EquipmentSlot.CHEST);
        boolean contactDisable = !isFallFlying() && !hasEffect(MobEffects.LEVITATION) && !isPassenger() && !onClimbable();
        boolean jumpDisable = input.jumping && releasedJumpKey;
        boolean brokenDisable = chestStack.is(Items.ELYTRA) && !ElytraItem.isFlyEnabled(chestStack);
        if (chestStack.is(Items.ELYTRA) && (contactDisable || jumpDisable || brokenDisable)) {
            trySwap(stack -> stack.getItem() instanceof ArmorItem armorItem && armorItem.getEquipmentSlot() == EquipmentSlot.CHEST);
        }

        if (!input.jumping && chestStack.is(Items.ELYTRA)) {
            releasedJumpKey = true;
        }
    }

    @Unique
    private boolean trySwap(Predicate<ItemStack> predicate) {
        for (int i = 0; i < getInventory().items.size(); i++) {
            ItemStack stack = getInventory().items.get(i);
            if (predicate.test(stack)) {
                swapSlots(6, indexToSlotIndex(i));
                return true;
            }
        }

        return false;
    }

    @Unique
    private int indexToSlotIndex(int id) {
        return id < 9 ? id + 36 : id;
    }

    @Unique
    private void swapSlots(int a, int b) {
        int containerId = minecraft.screen instanceof AbstractContainerScreen<?> containerScreen ? containerScreen.getMenu().containerId : 0;
        minecraft.gameMode.handleInventoryMouseClick(containerId, a, 0, ClickType.PICKUP, this);
        minecraft.gameMode.handleInventoryMouseClick(containerId, b, 0, ClickType.PICKUP, this);
        minecraft.gameMode.handleInventoryMouseClick(containerId, a, 0, ClickType.PICKUP, this);
    }
}
