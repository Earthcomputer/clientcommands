package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Texts;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.minecraft.command.argument.TextArgumentType.getTextArgument;
import static net.minecraft.command.argument.TextArgumentType.text;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CTellRawCommand {
    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ctellraw");

        dispatcher.register(literal("ctellraw")
            .then(argument("message", text())
                .executes(ctx -> {
                    MutableText text = Texts.parse(ctx.getSource(), getTextArgument(ctx, "message"), client.player, 0);
                    client.inGameHud.getChatHud().addMessage(text);
                    return 1;
                })
            )
        );
    }

}
