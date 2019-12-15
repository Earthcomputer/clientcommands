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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient implements IMinecraftClient {

    @Shadow protected int attackCooldown;

    @Shadow protected abstract void method_1590(boolean boolean_1);

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
    @ModifyArg(method = "<init>", index = 2, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/WindowProvider;createWindow(Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/client/util/Window;"))
    private String modifyWindowTitle(String title) {
        String playerName = MinecraftClient.getInstance().getSession().getProfile().getName();
        if (!"Earthcomputer".equals(playerName)
                && !"Azteched".equals(playerName)
                && !"samnrad".equals(playerName)
                && !"allocator".equals(playerName))
            return title;

        List<Character> chars = title.chars().mapToObj(c -> (char)c).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(chars);
        return chars.stream().map(String::valueOf).collect(Collectors.joining());
    }

    @Override
    public void continueBreakingBlock() {
        method_1590(true);
    }

    @Override
    public void resetAttackCooldown() {
        attackCooldown = 0;
    }
}
