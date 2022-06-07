package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Texts;

import static dev.xpple.clientarguments.arguments.CTextArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class CTellRawCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctellraw")
            .then(argument("message", text())
                .executes(ctx -> {
                    MutableText text = Texts.parse(new FakeCommandSource(ctx.getSource().getPlayer()), getCTextArgument(ctx, "message"), ctx.getSource().getPlayer(), 0);
                    ctx.getSource().getClient().inGameHud.getChatHud().addMessage(text);
                    return 0;
                })
            )
        );
    }
}
