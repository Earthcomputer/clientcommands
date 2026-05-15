package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.MutableComponent;

import static dev.xpple.clientarguments.arguments.CComponentArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class CTellRawCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("ctellraw")
            .then(argument("message", component(context))
                .executes(ctx -> {
                    MutableComponent component = CComponentUtil.updateForEntity(ctx.getSource(), getComponent(ctx, "message"), ctx.getSource().getPlayer(), 0);
                    ClientCommandHelper.sendFeedback(component);
                    return Command.SINGLE_SUCCESS;
                })
            )
        );
    }
}
