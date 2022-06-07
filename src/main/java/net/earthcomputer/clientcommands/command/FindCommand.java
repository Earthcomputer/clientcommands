package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class FindCommand {

    private static final int FLAG_KEEP_SEARCHING = 1;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cfind = dispatcher.register(literal("cfind"));
        dispatcher.register(literal("cfind")
            .then(literal("--keep-searching")
                .redirect(cfind, ctx -> withFlags(ctx.getSource(), FLAG_KEEP_SEARCHING, true)))
            .then(argument("filter", entities())
                .executes(ctx -> listEntities(ctx.getSource(), ctx.getArgument("filter", CEntitySelector.class)))));
    }

    private static int listEntities(FabricClientCommandSource source, CEntitySelector selector) throws CommandSyntaxException {
        boolean keepSearching = getFlag(source, FLAG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cfind", new FindTask(source, selector));

            source.sendFeedback(new TranslatableText("commands.cfind.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return 0;
        } else {
            List<? extends Entity> entities = selector.getEntities(source);

            if (entities.isEmpty()) {
                source.sendError(new TranslatableText("commands.cfind.noMatch"));
                return 0;
            }

            source.sendFeedback(new TranslatableText("commands.cfind.success", entities.size()).formatted(Formatting.BOLD));
            for (Entity entity : entities) {
                sendEntityFoundMessage(source, entity);
            }

            return entities.size();
        }
    }

    private static void sendEntityFoundMessage(FabricClientCommandSource source, Entity entity) {
        double distance = Math.sqrt(entity.squaredDistanceTo(source.getPosition()));
        source.sendFeedback(new TranslatableText("commands.cfind.found.left", entity.getName(), distance)
                .append(getLookCoordsTextComponent(entity.getBlockPos()))
                .append(new TranslatableText("commands.cfind.found.right", entity.getName(), distance)));
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
