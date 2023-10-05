package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FindCommand {

    private static final Argument<Boolean> ARG_KEEP_SEARCHING = Argument.ofFlag("keep-searching");

    private static final SimpleCommandExceptionType NO_MATCH_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cfind.noMatch"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cfind = dispatcher.register(literal("cfind"));
        dispatcher.register(literal("cfind")
            .then(literal(ARG_KEEP_SEARCHING.getFlag())
                .redirect(cfind, ctx -> withArg(ctx.getSource(), ARG_KEEP_SEARCHING, true)))
            .then(argument("filter", entities())
                .executes(ctx -> listEntities(ctx.getSource(), ctx.getArgument("filter", CEntitySelector.class)))));
    }

    private static int listEntities(FabricClientCommandSource source, CEntitySelector selector) throws CommandSyntaxException {
        boolean keepSearching = getArg(source, ARG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cfind", new FindTask(source, selector));

            source.sendFeedback(Text.translatable("commands.cfind.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return Command.SINGLE_SUCCESS;
        } else {
            List<? extends Entity> entities = selector.getEntities(source);

            if (entities.isEmpty()) {
                throw NO_MATCH_EXCEPTION.create();
            }

            source.sendFeedback(Text.translatable("commands.cfind.success", entities.size()).formatted(Formatting.BOLD));
            for (Entity entity : entities) {
                sendEntityFoundMessage(source, entity);
            }

            return entities.size();
        }
    }

    private static void sendEntityFoundMessage(FabricClientCommandSource source, Entity entity) {
        String distance = "%.2f".formatted(Math.sqrt(entity.squaredDistanceTo(source.getPosition())));
        source.sendFeedback(Text.translatable("commands.cfind.found.left", entity.getName(), distance)
                .append(getLookCoordsTextComponent(entity.getBlockPos()))
                .append(Text.translatable("commands.cfind.found.right", entity.getName(), distance)));
    }

    private static class FindTask extends LongTask {
        private final FabricClientCommandSource source;
        private final CEntitySelector selector;
        private final Set<UUID> foundEntities = new HashSet<>();

        private FindTask(FabricClientCommandSource source, CEntitySelector selector) {
            this.source = source;
            this.selector = selector;
        }

        @Override
        public void initialize() {
        }

        @Override
        public boolean condition() {
            return MinecraftClient.getInstance().player != null;
        }

        @Override
        public void increment() {
        }

        @Override
        public void body() {
            try {
                for (Entity entity : selector.getEntities(this.source)) {
                    if (foundEntities.add(entity.getUuid())) {
                        sendEntityFoundMessage(source, entity);
                    }
                }
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }

            scheduleDelay();
        }
    }

}
