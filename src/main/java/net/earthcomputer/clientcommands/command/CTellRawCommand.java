package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

import static dev.xpple.clientarguments.arguments.CComponentArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTellRawCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("ctellraw")
            .then(argument("message", textComponent(context))
                .executes(ctx -> {
                    MutableComponent component = ComponentUtils.updateForEntity(new FakeCommandSource(ctx.getSource().getPlayer()), getComponent(ctx, "message"), ctx.getSource().getPlayer(), 0);
                    ctx.getSource().getClient().gui.getChat().addMessage(component);
                    return Command.SINGLE_SUCCESS;
                })
            )
        );
    }
}
