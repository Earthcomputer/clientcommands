package net.earthcomputer.clientcommands.mixin.rngevents;

import com.mojang.brigadier.StringReader;
import net.earthcomputer.clientcommands.features.CCrackVillager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
        if(packet.getSound().is(SoundEvents.AMETHYST_BLOCK_CHIME.getLocation())) {
            CCrackVillager.onAmethyst(packet);
        } else if (packet.getSound().is(SoundEvents.VILLAGER_AMBIENT.getLocation())) {
            CCrackVillager.onAmbient();
        }
    }
}
