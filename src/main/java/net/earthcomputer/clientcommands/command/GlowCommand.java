package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.MultibaseIntegerArgumentType.*;
import static net.minecraft.command.argument.ColorArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class GlowCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cglow.failed"));

    private static final int FLAG_KEEP_SEARCHING = 1;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cglow");

        LiteralCommandNode<ServerCommandSource> cglow = dispatcher.register(literal("cglow"));
        dispatcher.register(literal("cglow")
            .then(literal("--keep-searching")
                .redirect(cglow, ctx -> withFlags(ctx.getSource(), FLAG_KEEP_SEARCHING, true)))
            .then(argument("targets", entities())
                .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getFlag(ctx, FLAG_KEEP_SEARCHING) ? 0 : 30, 0xffffff))
                .then(argument("seconds", integer(0))
                    .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getInteger(ctx, "seconds"), 0xffffff))
                    .then(literal("color")
                        .then(argument("color", color())
                            .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getInteger(ctx, "seconds"), Optional.ofNullable(getColor(ctx, "color").getColorValue()).orElse(0xffffff)))))
                    .then(literal("colorCode")
                        .then(argument("color", multibaseInteger(0, 0xffffff))
                            .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color"))))))));
    }

    private static int glowEntities(ServerCommandSource source, ClientEntitySelector entitySelector, int seconds, int color) throws CommandSyntaxException {
        boolean keepSearching = getFlag(source, FLAG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cglow", new SimpleTask() {
                @Override
                public boolean condition() {
                    return MinecraftClient.getInstance().player != null;
                }

                @Override
                protected void onTick() {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    assert player != null;
                    for (Entity entity : entitySelector.getEntities(new FakeCommandSource(player))) {
                        ((IEntity) entity).addGlowingTicket(seconds * 20, color);
                    }
                }
            });

            sendFeedback(new TranslatableText("commands.cglow.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return 0;
        } else {
            List<Entity> entities = entitySelector.getEntities(source);
            if (entities.isEmpty()) {
                throw FAILED_EXCEPTION.create();
            }

            for (Entity entity : entities) {
                ((IEntity) entity).addGlowingTicket(seconds * 20, color);
            }

            sendFeedback("commands.cglow.success", entities.size());

            return entities.size();
        }
    }

}
