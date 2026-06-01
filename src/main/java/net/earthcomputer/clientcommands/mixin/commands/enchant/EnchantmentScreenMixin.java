package net.earthcomputer.clientcommands.mixin.commands.enchant;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
    public EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void postExtract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float ignored, CallbackInfo ci) {
        if (EnchantmentCracker.isEnchantingPredictionEnabled()) {
            EnchantmentCracker.extractEnchantmentGUIOverlay(graphics);
        }
    }

    @Inject(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleInventoryButtonClick(II)V"))
    public void onItemEnchanted(CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onEnchantedItem();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (EnchantmentCracker.isEnchantingPredictionEnabled()) {
            addRenderableWidget(Button.builder(Component.translatable("enchCrack.addInfo"), button -> {
                EnchantmentCracker.addEnchantmentSeedInfo(Minecraft.getInstance().level, getMenu());
            }).pos(width - 150, 0).build());
        }
    }
}
