package net.earthcomputer.clientcommands.mixin.events;

import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.earthcomputer.clientcommands.event.ClientLevelEvents;
import net.earthcomputer.clientcommands.event.MoreScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private boolean isLevelLoaded = false;

    @Inject(method = "updateLevelInEngines", at = @At("HEAD"))
    public void onUpdateLevelInEngines(ClientLevel level, CallbackInfo ci) {
        if (isLevelLoaded) {
            ClientLevelEvents.UNLOAD_LEVEL.invoker().onUnloadLevel(level == null);
        }
        isLevelLoaded = level != null;
        if (isLevelLoaded) {
            ClientLevelEvents.LOAD_LEVEL.invoker().onLoadLevel(level);
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    public void onOpenScreen(@Nullable Screen screen, CallbackInfo ci) {
        if (!MoreScreenEvents.BEFORE_ADD.invoker().beforeScreenAdd(screen)) {
            ci.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    public void onDisconnect(Screen screen, CallbackInfo ci) {
        ClientConnectionEvents.DISCONNECT.invoker().onDisconnect();
    }
}
