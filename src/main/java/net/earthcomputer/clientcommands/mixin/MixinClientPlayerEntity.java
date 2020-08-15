package net.earthcomputer.clientcommands.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.StringReader;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.IKeyBinding;
import net.earthcomputer.clientcommands.script.ScriptManager;
import net.earthcomputer.multiconnect.api.MultiConnectAPI;
import net.earthcomputer.multiconnect.api.Protocols;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity extends AbstractClientPlayerEntity {

    @Unique private boolean wasSprintPressed = false;

    public MixinClientPlayerEntity(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

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

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V"))
    private void onTick(CallbackInfo ci) {
        if (Enchantments.MENDING.getEquipment(this).values().stream().anyMatch(this::couldMendingRepair)) {
            if (!world.getEntitiesByClass(ExperienceOrbEntity.class, getBoundingBox(), null).isEmpty()) {
                PlayerRandCracker.onMending();
            }
        }
    }

    @Unique
    private boolean couldMendingRepair(ItemStack stack) {
        if (EnchantmentHelper.getLevel(Enchantments.MENDING, stack) <= 0) {
            return false;
        }
        if (MultiConnectAPI.instance().getProtocolVersion() <= Protocols.V1_15_2) {
            return true; // xp may try to mend items even if they're fully repaired pre-1.16
        }
        return stack.isDamaged();
    }

    @Inject(method = "dropSelectedItem", at = @At("HEAD"))
    public void onDropSelectedItem(boolean dropAll, CallbackInfoReturnable<ItemEntity> ci) {
        PlayerRandCracker.onDropItem();
    }

    @Inject(method = "damage", at = @At("HEAD"))
    public void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onDamage();
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    public void onStartTickMovement(CallbackInfo ci) {
        wasSprintPressed = MinecraftClient.getInstance().options.keySprint.isPressed();
        boolean shouldBeSprinting = (wasSprintPressed && !ScriptManager.blockingInput()) || ScriptManager.isSprinting();
        ((IKeyBinding) MinecraftClient.getInstance().options.keySprint).setPressed(shouldBeSprinting);
    }

    @Inject(method = "tickMovement", at = @At("RETURN"))
    public void onEndTickMovement(CallbackInfo ci) {
        ((IKeyBinding) MinecraftClient.getInstance().options.keySprint).setPressed(wasSprintPressed);
    }

}
