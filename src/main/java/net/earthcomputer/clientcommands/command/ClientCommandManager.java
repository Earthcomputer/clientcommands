package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandException;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class ClientCommandManager {

    private static Set<String> clientSideCommands = new HashSet<>();

    public static void clearClientSideCommands() {
        clientSideCommands.clear();
    }

    public static void addClientSideCommand(String name) {
        clientSideCommands.add(name);
    }

    public static boolean isClientSideCommand(String name) {
        return clientSideCommands.contains(name);
    }

    public static void sendError(Text error) {
        sendFeedback(new LiteralText("").append(error).formatted(Formatting.RED));
    }

    public static void sendFeedback(String message) {
        sendFeedback(new TranslatableText(message));
    }

    public static void sendFeedback(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    public static void executeCommand(StringReader reader, String command) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        try {
            player.networkHandler.getCommandDispatcher().execute(reader, new FakeCommandSource(player));
        } catch (CommandException e) {
            ClientCommandManager.sendError(e.getMessageText());
        } catch (CommandSyntaxException e) {
            ClientCommandManager.sendError(Texts.toText(e.getRawMessage()));
            if (e.getInput() != null && e.getCursor() >= 0) {
                int cursor = Math.min(e.getCursor(), e.getInput().length());
                Text text = new LiteralText("").formatted(Formatting.GRAY)
                        .styled(style -> style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
                if (cursor > 10)
                    text.append("...");

                text.append(e.getInput().substring(Math.max(0, cursor - 10), cursor));
                if (cursor < e.getInput().length()) {
                    text.append(new LiteralText(e.getInput().substring(cursor)).formatted(Formatting.RED, Formatting.UNDERLINE));
                }

                text.append(new TranslatableText("command.context.here").formatted(Formatting.RED, Formatting.ITALIC));
                ClientCommandManager.sendError(text);
            }
        } catch (Exception e) {
            LiteralText error = new LiteralText(e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            ClientCommandManager.sendError(new TranslatableText("command.failed")
                    .styled(style -> style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, error))));
            e.printStackTrace();
        }
    }

    public static Text getCoordsTextComponent(BlockPos pos) {
        Text text = new TranslatableText("commands.client.blockpos", pos.getX(), pos.getY(),
                pos.getZ());
        text.getStyle().setUnderline(true);
        text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ())));
        text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new LiteralText(String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()))));
        return text;
    }

    public static Text getCommandTextComponent(String translationKey, String command) {
        Text text = new TranslatableText(translationKey).styled(style -> style.setUnderline(true));
        text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText(command)));
        return text;
    }

}
