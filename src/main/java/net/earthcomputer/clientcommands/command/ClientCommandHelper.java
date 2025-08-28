package net.earthcomputer.clientcommands.command;

import com.demonwav.mcdev.annotations.Translatable;
import com.mojang.brigadier.context.CommandContext;
import net.earthcomputer.clientcommands.interfaces.IClientSuggestionsProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCommandHelper {

    public static <T> T getFlag(CommandContext<FabricClientCommandSource> ctx, Flag<T> flag) {
        return getFlag(Flag.getActualSource(ctx), flag);
    }

    public static <T> T getFlag(FabricClientCommandSource source, Flag<T> flag) {
        return ((IClientSuggestionsProvider) source).clientcommands_getFlag(flag);
    }

    public static <T> FabricClientCommandSource withFlag(FabricClientCommandSource source, Flag<T> flag, T value) {
        return (FabricClientCommandSource) ((IClientSuggestionsProvider) source).clientcommands_withFlag(flag, value);
    }

    public static void sendError(Component error) {
        sendFeedback(Component.literal("").append(error).withStyle(ChatFormatting.RED));
    }

    public static void sendHelp(Component help) {
        sendFeedback(Component.literal("").append(help).withStyle(ChatFormatting.AQUA));
    }

    public static void sendFeedback(@Translatable String message, Object... args) {
        sendFeedback(Component.translatable(message, args));
    }

    public static void sendFeedback(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }

    public static void sendRequiresRestart() {
        sendFeedback(Component.translatable("commands.client.requiresRestart").withStyle(ChatFormatting.YELLOW));
    }

    public static void addOverlayMessage(Component message, int time) {
        Gui gui = Minecraft.getInstance().gui;
        gui.setOverlayMessage(message, false);
        gui.overlayMessageTime = time;
    }

    public static Component getLookCoordsTextComponent(BlockPos pos) {
        return getCommandTextComponent(Component.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ()),
            String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getLookCoordsTextComponent(MutableComponent component, BlockPos pos) {
        return getCommandTextComponent(component, String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getGlowCoordsTextComponent(BlockPos pos) {
        return getCommandTextComponent(Component.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ()),
            String.format("/cglow block %d %d %d 10", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getGlowButtonTextComponent(BlockPos pos) {
        return getCommandTextComponent(Component.translatable("commands.client.glow"), String.format("/cglow block %d %d %d 60", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getGlowButtonTextComponent(Entity entity) {
        return getCommandTextComponent(Component.translatable("commands.client.glow"), "/cglow entities " + entity.getStringUUID());
    }

    public static Component getCommandTextComponent(@Translatable String translationKey, String command) {
        return getCommandTextComponent(Component.translatable(translationKey), command);
    }

    public static Component getCommandTextComponent(MutableComponent component, String command) {
        return component.withStyle(style -> style.applyFormat(ChatFormatting.UNDERLINE)
            .withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    private static final Map<UUID, Callback> callbacks = new ConcurrentHashMap<>();

    public static ClickEvent callbackClickEvent(Runnable runnable) {
        return callbackClickEvent(runnable, 60_000_000_000L); // 1 minute timeout
    }

    public static ClickEvent callbackClickEvent(Runnable callback, long timeoutNanos) {
        UUID callbackId = UUID.randomUUID();
        callbacks.put(callbackId, new Callback(callback, System.nanoTime() + timeoutNanos));
        return new ClickEvent.RunCommand("/ccallback " + callbackId);
    }

    public static boolean runCallback(UUID callbackId) {
        Callback callback = callbacks.get(callbackId);
        if (callback == null) {
            return false;
        }
        callback.callback.run();
        return true;
    }

    private record Callback(Runnable callback, long timeout) {
        static {
            Thread.ofVirtual().name("Clientcommands callback cleanup").start(() -> {
                while (true) {
                    try {
                        // This isn't really "busy-waiting" since the wait time is so long
                        //noinspection BusyWait
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    long now = System.nanoTime();
                    callbacks.values().removeIf(callback -> now - callback.timeout <= 0);
                }
            });
        }
    }
}
