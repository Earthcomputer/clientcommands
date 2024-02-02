package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

import static dev.xpple.clientarguments.arguments.CTextArgumentType.getCTextArgument;
import static dev.xpple.clientarguments.arguments.CTextArgumentType.text;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CTellRawCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctellraw")
            .then(argument("message", text())
                .executes(ctx -> {
                    MutableComponent component = ComponentUtils.updateForEntity(new FakeCommandSource(ctx.getSource().getPlayer()), getCTextArgument(ctx, "message"), ctx.getSource().getPlayer(), 0);
                    ctx.getSource().getClient().gui.getChat().addMessage(component);
                    return Command.SINGLE_SUCCESS;
                })
            )
        );
    }
}
