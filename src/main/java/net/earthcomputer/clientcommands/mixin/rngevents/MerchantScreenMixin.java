package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.VillagerCommand;
import net.earthcomputer.clientcommands.features.VillagerCracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
    public MerchantScreenMixin(MerchantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {
        if (VillagerCracker.targetOffer != null) {
            if (menu.getOffers().stream().map(offer -> new VillagerCommand.Offer(offer.getBaseCostA(), offer.getItemCostB().map(ItemCost::itemStack).orElse(null), offer.getResult())).peek(offer -> System.out.println("offer: " + offer)).anyMatch(offer -> offer.toString().equals(VillagerCracker.targetOffer.toString()))) {
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.success", Configs.villagerAdjustment * 50).withStyle(ChatFormatting.GREEN), 100);
                minecraft.player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
            } else {
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.failure", Configs.villagerAdjustment * 50).withStyle(ChatFormatting.RED), 100);
                minecraft.player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            VillagerCracker.targetOffer = null;
        }
    }
}
