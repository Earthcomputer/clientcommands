package net.earthcomputer.clientcommands.command;

import com.demonwav.mcdev.annotations.Translatable;
import com.mojang.brigadier.context.CommandContext;
import net.earthcomputer.clientcommands.interfaces.IClientCommandSource;
import net.earthcomputer.clientcommands.mixin.InGameHudAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class ClientCommandHelper {

    public static <T> T getArg(CommandContext<FabricClientCommandSource> ctx, Argument<T> arg) {
        return getArg(ctx.getSource(), arg);
    }

    public static <T> T getArg(FabricClientCommandSource source, Argument<T> arg) {
        return ((IClientCommandSource) source).clientcommands_getArg(arg);
    }

    public static <T> FabricClientCommandSource withArg(FabricClientCommandSource source, Argument<T> arg, T value) {
        return (FabricClientCommandSource) ((IClientCommandSource) source).clientcommands_withArg(arg, value);
    }

    public static void sendError(Text error) {
        sendFeedback(Text.literal("").append(error).formatted(Formatting.RED));
    }

    public static void sendHelp(Text help) {
        sendFeedback(Text.literal("").append(help).formatted(Formatting.AQUA));
    }

    public static void sendFeedback(@Translatable String message, Object... args) {
        sendFeedback(Text.translatable(message, args));
    }

    public static void sendFeedback(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    public static void sendRequiresRestart() {
        sendFeedback(Text.translatable("commands.client.requiresRestart").formatted(Formatting.YELLOW));
    }

    public static void addOverlayMessage(Text message, int time) {
        InGameHud inGameHud = MinecraftClient.getInstance().inGameHud;
        inGameHud.setOverlayMessage(message, false);
        ((InGameHudAccessor) inGameHud).setOverlayRemaining(time);
    }

    public static Text getLookCoordsTextComponent(BlockPos pos) {
        return getCommandTextComponent(Text.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ()),
                String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Text getLookCoordsTextComponent(MutableText translatableText, BlockPos pos) {
        return getCommandTextComponent(translatableText, String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Text getGlowCoordsTextComponent(BlockPos pos) {
        return getCommandTextComponent(Text.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ()),
                String.format("/cglow block %d %d %d 10", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Text getGlowCoordsTextComponent(MutableText translatableText, BlockPos pos) {
        return getCommandTextComponent(translatableText, String.format("/cglow block %d %d %d 10", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Text getCommandTextComponent(@Translatable String translationKey, String command) {
        return getCommandTextComponent(Text.translatable(translationKey), command);
    }

    public static Text getCommandTextComponent(MutableText translatableText, String command) {
        return translatableText.styled(style -> style.withFormatting(Formatting.UNDERLINE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command))));
    }

}
