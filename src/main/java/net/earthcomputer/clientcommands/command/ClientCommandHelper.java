package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.context.CommandContext;
import net.earthcomputer.clientcommands.interfaces.IFlaggedCommandSource;
import net.earthcomputer.clientcommands.mixin.InGameHudAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class ClientCommandHelper {

    public static boolean getFlag(CommandContext<FabricClientCommandSource> ctx, int flag) {
        return getFlag(ctx.getSource(), flag);
    }

    public static boolean getFlag(FabricClientCommandSource source, int flag) {
        return (((IFlaggedCommandSource) source).getFlags() & flag) != 0;
    }

    public static FabricClientCommandSource withFlags(FabricClientCommandSource source, int flags, boolean value) {
        IFlaggedCommandSource flaggedSource = (IFlaggedCommandSource) source;

        if (value) {
            return (FabricClientCommandSource) flaggedSource.withFlags(flaggedSource.getFlags() | flags);
        } else {
            return (FabricClientCommandSource) flaggedSource.withFlags(flaggedSource.getFlags() & ~flags);
        }
    }

    public static void sendError(Text error) {
        sendFeedback(Text.literal("").append(error).formatted(Formatting.RED));
    }

    public static void sendFeedback(String message, Object... args) {
        sendFeedback(Text.translatable(message, args));
    }

    public static void sendFeedback(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
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

    public static Text getCommandTextComponent(String translationKey, String command) {
        return getCommandTextComponent(Text.translatable(translationKey), command);
    }

    public static Text getCommandTextComponent(MutableText translatableText, String command) {
        return translatableText.styled(style -> style.withFormatting(Formatting.UNDERLINE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command))));
    }

}
