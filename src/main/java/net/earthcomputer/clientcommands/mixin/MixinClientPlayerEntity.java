package net.earthcomputer.clientcommands.mixin;

import com.mojang.brigadier.StringReader;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (message.startsWith("/")) {
            StringReader reader = new StringReader(message);
            reader.skip();
            int cursor = reader.getCursor();
            String commandName = reader.canRead() ? reader.readUnquotedString() : "";
            reader.setCursor(cursor);
            if (ClientCommandManager.isClientSideCommand(commandName)) {
                ClientCommandManager.executeCommand(reader, message);
                ci.cancel();
            } else if ("give".equals(commandName)) {
                PlayerRandCracker.onGiveCommand();
            }
        }
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"))
    public void onDropSelectedItem(boolean dropAll, CallbackInfoReturnable<ItemEntity> ci) {
        PlayerRandCracker.onDropItem();
    }

    @Inject(method = "damage", at = @At("HEAD"))
    public void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onDamage();
    }

}
