package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentScreen.class)
public abstract class MixinEnchantingScreen extends HandledScreen<EnchantmentScreenHandler> {

    public MixinEnchantingScreen(EnchantmentScreenHandler container_1, PlayerInventory playerInventory_1, Text text_1) {
        super(container_1, playerInventory_1, text_1);
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void postRender(MatrixStack matrices, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (EnchantmentCracker.isEnchantingPredictionEnabled()) {
            EnchantmentCracker.drawEnchantmentGUIOverlay(matrices);
        }
    }

    @Inject(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickButton(II)V"))
    public void onItemEnchanted(double mouseX, double mouseY, int mouseButton, CallbackInfoReturnable<Boolean> ci) {
        EnchantmentCracker.onEnchantedItem();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (EnchantmentCracker.isEnchantingPredictionEnabled()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("enchCrack.addInfo"), button -> {
                EnchantmentCracker.addEnchantmentSeedInfo(MinecraftClient.getInstance().world, getScreenHandler());
            }).position(width - 150, 0).build());
        }
    }
}
