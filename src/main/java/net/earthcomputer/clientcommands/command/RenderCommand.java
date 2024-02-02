package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.earthcomputer.clientcommands.features.RenderSettings;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class RenderCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("crender")
            .then(literal("enable")
                .then(literal("entities")
                    .then(argument("filter", entities())
                        .executes(ctx -> enableEntityRendering(ctx.getSource(), ctx.getArgument("filter", CEntitySelector.class), true)))))
            .then(literal("disable")
                .then(literal("entities")
                    .then(argument("filter", entities())
                        .executes(ctx -> enableEntityRendering(ctx.getSource(), ctx.getArgument("filter", CEntitySelector.class), false))))));
    }

    private static int enableEntityRendering(FabricClientCommandSource source, CEntitySelector selector, boolean enable) {
        RenderSettings.addEntityRenderSelector(selector, enable);
        source.sendFeedback(Component.translatable("commands.crender.entities.success"));
        return Command.SINGLE_SUCCESS;
    }

}
