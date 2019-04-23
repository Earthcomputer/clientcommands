package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TextFormat;
import net.minecraft.text.TranslatableTextComponent;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class FindCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cfind");

        dispatcher.register(literal("cfind")
            .then(argument("filter", entities())
                .executes(ctx -> listEntities(ctx.getSource(), getEntities(ctx, "filter")))));
    }

    private static int listEntities(ServerCommandSource source, List<Entity> entities) {
        if (entities.isEmpty()) {
            sendError(new TranslatableTextComponent("commands.cfind.noMatch"));
            return 0;
        }

        sendFeedback(new TranslatableTextComponent("commands.cfind.success", entities.size()).applyFormat(TextFormat.BOLD));
        for (Entity entity : entities) {
            double distance = Math.sqrt(entity.squaredDistanceTo(source.getPosition()));
            sendFeedback(new TranslatableTextComponent("commands.cfind.found.left", entity.getName(), distance)
                    .append(getCoordsTextComponent(new BlockPos(entity)))
                    .append(new TranslatableTextComponent("commands.cfind.found.right", entity.getName(), distance)));
        }

        return entities.size();
    }

}
