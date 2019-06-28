package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractContainerScreen;
import net.minecraft.client.gui.screen.ingame.EnchantingScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.container.EnchantingTableContainer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantingScreen.class)
public abstract class MixinEnchantingScreen extends AbstractContainerScreen<EnchantingTableContainer> {

    public MixinEnchantingScreen(EnchantingTableContainer container_1, PlayerInventory playerInventory_1, Text text_1) {
        super(container_1, playerInventory_1, text_1);
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void postRender(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (EnchantmentCracker.isEnchantingPredictionEnabled())
            EnchantmentCracker.drawEnchantmentGUIOverlay();
    }

    @Inject(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickButton(II)V"))
    public void onItemEnchanted(double mouseX, double mouseY, int mouseButton, CallbackInfoReturnable<Boolean> ci) {
        EnchantmentCracker.onEnchantedItem();
    }

    @Override
    protected void init() {
        super.init();
        if (EnchantmentCracker.isEnchantingPredictionEnabled()) {
            addButton(new ButtonWidget(width - 100, 0, 100, 20, I18n.translate("enchCrack.addInfo"), button -> {
                EnchantmentCracker.addEnchantmentSeedInfo(MinecraftClient.getInstance().world, getContainer());
            }));
        }
    }
}
