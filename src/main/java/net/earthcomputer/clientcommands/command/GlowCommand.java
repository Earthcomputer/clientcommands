package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;

import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.MultibaseIntegerArgumentType.*;
import static net.minecraft.command.argument.ColorArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class GlowCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cglow");

        dispatcher.register(literal("cglow")
            .then(argument("targets", entities())
                .executes(ctx -> glowEntities(ctx.getSource(), getEntities(ctx, "targets"), 30, 0xffffff))
                .then(argument("seconds", integer(1))
                    .executes(ctx -> glowEntities(ctx.getSource(), getEntities(ctx, "targets"), getInteger(ctx, "seconds"), 0xffffff))
                    .then(literal("color")
                        .then(argument("color", color())
                            .executes(ctx -> glowEntities(ctx.getSource(), getEntities(ctx, "targets"), getInteger(ctx, "seconds"), Optional.ofNullable(getColor(ctx, "color").getColorValue()).orElse(0xffffff)))))
                    .then(literal("colorCode")
                        .then(argument("color", multibaseInteger(0, 0xffffff))
                            .executes(ctx -> glowEntities(ctx.getSource(), getEntities(ctx, "targets"), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color"))))))));
    }

    private static int glowEntities(ServerCommandSource source, List<Entity> entities, int seconds, int color) {
        for (Entity entity : entities) {
            ((IEntity) entity).addGlowingTicket(seconds * 20, color);
        }
        return entities.size();
    }

}
