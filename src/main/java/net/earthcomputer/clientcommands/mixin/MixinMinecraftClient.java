package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.GuiBlocker;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.RenderSettings;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.interfaces.IMinecraftClient;
import net.earthcomputer.clientcommands.script.ScriptManager;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient implements IMinecraftClient {

    @Shadow protected int attackCooldown;

    @Shadow protected abstract void handleBlockBreaking(boolean boolean_1);

    @Inject(method = "tick", at = @At("HEAD"))
    public void onTick(CallbackInfo ci) {
        TaskManager.tick();
        GuiBlocker.tick();
        ScriptManager.tick();
    }

    @Inject(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z", ordinal = 0), cancellable = true)
    public void onHandleInputEvents(CallbackInfo ci) {
        if (ScriptManager.blockingInput())
            ci.cancel();
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    public void onSetWorld(CallbackInfo ci) {
        PlayerRandCracker.onRecreatePlayer();
        TaskManager.onWorldUnload();
    }

    @Inject(method = "openScreen", at = @At("HEAD"), cancellable = true)
    public void onOpenScreen(Screen screen, CallbackInfo ci) {
        if (!GuiBlocker.onOpenGui(screen))
            ci.cancel();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("RETURN"))
    public void onDisconnect(Screen screen, CallbackInfo ci) {
        for (String rule : TempRules.getRules())
            TempRules.reset(rule);
        RenderSettings.clearEntityRenderSelectors();
        ServerBrandManager.onDisconnect();
    }

    // Earth annoying his friends <3 nothing to see here
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void modifyWindowTitle(CallbackInfoReturnable<String> ci) {
        String playerName = MinecraftClient.getInstance().getSession().getProfile().getName();
        if ("Earthcomputer".equals(playerName)
                || "Azteched".equals(playerName)
                || "samnrad".equals(playerName)
                || "allocator".equals(playerName)) {
            List<Character> chars = ci.getReturnValue().chars().mapToObj(c -> (char) c).collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(chars);
            ci.setReturnValue(chars.stream().map(String::valueOf).collect(Collectors.joining()));
        }
    }

    @Override
    public void continueBreakingBlock() {
        handleBlockBreaking(true);
    }

    @Override
    public void resetAttackCooldown() {
        attackCooldown = 0;
    }
}
