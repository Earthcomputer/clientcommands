package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandException;
import net.minecraft.text.*;
import net.minecraft.text.event.ClickEvent;
import net.minecraft.text.event.HoverEvent;

import java.util.HashSet;
import java.util.Set;

public class ClientCommandManager {

    private static Set<String> clientSideCommands = new HashSet<>();

    private static DynamicCommandExceptionType INVALID_LONG =
            new DynamicCommandExceptionType(value -> new LiteralMessage("Invalid long '" + value + "'"));

    public static void clearClientSideCommands() {
        clientSideCommands.clear();
    }

    public static void addClientSideCommand(String name) {
        clientSideCommands.add(name);
    }

    public static boolean isClientSideCommand(String name) {
        return clientSideCommands.contains(name);
    }

    public static void sendError(TextComponent error) {
        sendFeedback(new StringTextComponent("").append(error).applyFormat(TextFormat.RED));
    }

    public static void sendFeedback(String message) {
        sendFeedback(new TranslatableTextComponent(message));
    }

    public static void sendFeedback(TextComponent message) {
        MinecraftClient.getInstance().inGameHud.getHudChat().addMessage(message);
    }

    public static void executeCommand(StringReader reader, String command) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        try {
            player.networkHandler.method_2886().execute(reader, new FakeCommandSource(player));
        } catch (CommandException e) {
            ClientCommandManager.sendError(e.getMessageComponent());
        } catch (CommandSyntaxException e) {
            ClientCommandManager.sendError(TextFormatter.message(e.getRawMessage()));
            if (e.getInput() != null && e.getCursor() >= 0) {
                int cursor = Math.min(e.getCursor(), e.getInput().length());
                TextComponent text = new StringTextComponent("").applyFormat(TextFormat.GRAY)
                        .modifyStyle(style -> style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
                if (cursor > 10)
                    text.append("...");

                text.append(e.getInput().substring(Math.max(0, cursor - 10), cursor));
                if (cursor < e.getInput().length()) {
                    text.append(new StringTextComponent(e.getInput().substring(cursor)).applyFormat(TextFormat.RED, TextFormat.UNDERLINE));
                }

                text.append(new TranslatableTextComponent("command.context.here").applyFormat(TextFormat.RED, TextFormat.ITALIC));
                ClientCommandManager.sendError(text);
            }
        } catch (Exception e) {
            TextComponent error = new StringTextComponent(e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            ClientCommandManager.sendError(new TranslatableTextComponent("command.failed")
                    .modifyStyle(style -> style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, error))));
            e.printStackTrace();
        }
    }

    public static long parseLong(String str) throws CommandSyntaxException {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            throw INVALID_LONG.create(str);
        }
    }

}
