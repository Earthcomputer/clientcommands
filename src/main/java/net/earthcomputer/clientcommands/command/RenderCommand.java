package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.RenderSettings;
import net.minecraft.server.command.ServerCommandSource;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class RenderCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("crender");

        dispatcher.register(literal("crender")
            .then(literal("enable")
                .then(literal("entities")
                    .then(argument("filter", entities())
                        .executes(ctx -> enableEntityRendering(ctx.getSource(), getEntitySelector(ctx, "filter"), true)))))
            .then(literal("disable")
                .then(literal("entities")
                    .then(argument("filter", entities())
                        .executes(ctx -> enableEntityRendering(ctx.getSource(), getEntitySelector(ctx, "filter"), false))))));
    }

    private static int enableEntityRendering(ServerCommandSource source, ClientEntitySelector selector, boolean enable) {
        RenderSettings.addEntityRenderSelector(selector, enable);
        sendFeedback("commands.crender.entities.success");
        return 0;
    }

}
