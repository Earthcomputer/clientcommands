package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Texts;

import static dev.xpple.clientarguments.arguments.CTextArgumentType.getCTextArgument;
import static dev.xpple.clientarguments.arguments.CTextArgumentType.text;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;

public class CTellRawCommand {
    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctellraw")
            .then(argument("message", text())
                .executes(ctx -> {
                    MutableText text = Texts.parse(new FakeCommandSource(client.player), getCTextArgument(ctx, "message"), client.player, 0);
                    client.inGameHud.getChatHud().addMessage(text);
                    return 1;
                })
            )
        );
    }

}
