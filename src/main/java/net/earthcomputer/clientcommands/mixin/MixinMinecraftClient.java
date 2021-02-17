package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.GuiBlocker;
import net.earthcomputer.clientcommands.ServerBrandManager;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.Relogger;
import net.earthcomputer.clientcommands.features.RenderSettings;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.interfaces.IMinecraftClient;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.script.ScriptManager;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.world.ClientWorld;
import org.apache.commons.lang3.tuple.Pair;
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
        RenderQueue.tick();
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
    public void onSetWorld(ClientWorld world, CallbackInfo ci) {
        PlayerRandCracker.onRecreatePlayer();
        TaskManager.onWorldUnload(world == null);
    }

    @Inject(method = "openScreen", at = @At("HEAD"), cancellable = true)
    public void onOpenScreen(Screen screen, CallbackInfo ci) {
        if (screen != null
                && !(screen instanceof SaveLevelScreen)
                && !(screen instanceof LevelLoadingScreen)
                && !(screen instanceof ProgressScreen)
                && !(screen instanceof ConnectScreen)
                && !(screen instanceof GameMenuScreen)
                && !(screen instanceof DownloadingTerrainScreen)
                && !(screen instanceof TitleScreen)
                && !(screen instanceof MultiplayerScreen)) {
            Relogger.cantHaveRelogSuccess();
        }
        if (!GuiBlocker.onOpenGui(screen))
            ci.cancel();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("RETURN"))
    public void onDisconnect(Screen screen, CallbackInfo ci) {
        ServerBrandManager.onDisconnect();
        if (Relogger.isRelogging) {
            List<Pair<String, Object>> oldRules = TempRules.getRules().stream().map(rule -> Pair.of(rule, TempRules.get(rule))).collect(Collectors.toList());
            Relogger.relogSuccessTasks.add(() -> oldRules.forEach(rule -> TempRules.set(rule.getLeft(), rule.getRight())));
        }
        for (String rule : TempRules.getRules())
            TempRules.reset(rule);
        RenderSettings.clearEntityRenderSelectors();
    }

    // Earth annoying his friends <3 nothing to see here
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void modifyWindowTitle(CallbackInfoReturnable<String> ci) {
        String playerName = MinecraftClient.getInstance().getSession().getProfile().getName();
        if ("Earthcomputer".equals(playerName)
                || "Azteched".equals(playerName)
                || "samnrad".equals(playerName)
                || "allocator".equals(playerName)
                || "Google_Alt".equals(playerName)) {
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
