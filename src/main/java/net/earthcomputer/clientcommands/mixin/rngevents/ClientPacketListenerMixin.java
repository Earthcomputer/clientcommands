package net.earthcomputer.clientcommands.mixin.rngevents;

import com.mojang.brigadier.StringReader;
import net.earthcomputer.clientcommands.features.CCrackVillager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.VillagerRNGSim;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfo ci) {
        StringReader reader = new StringReader(command);
        String commandName = reader.canRead() ? reader.readUnquotedString() : "";
        if ("give".equals(commandName)) {
            PlayerRandCracker.onGiveCommand();
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void onSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (packet.getSound().is(SoundEvents.VILLAGER_AMBIENT.getLocation())) {
            CCrackVillager.onAmbient(packet);
        } else if(packet.getSound().is(SoundEvents.AMETHYST_BLOCK_CHIME.getLocation())) {
            CCrackVillager.onAmethyst(packet);
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    void onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci){
        if(packet.getPos().equals(CCrackVillager.clockPos)) {
            CCrackVillager.onClockUpdate();
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    void onBlockUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci){
        packet.runUpdates((pos, block) -> {
            if(pos.equals(CCrackVillager.clockPos)) {
                CCrackVillager.onClockUpdate();
            }
        });
    }

    @Inject(method = "handleMerchantOffers", at = @At("TAIL"))
    void onOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        VillagerRNGSim.INSTANCE.syncOffer(packet);
    }
}
