package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class FindCommand {

    private static final int FLAG_KEEP_SEARCHING = 1;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cfind");

        var cfind = dispatcher.register(literal("cfind"));
        dispatcher.register(literal("cfind")
            .then(literal("--keep-searching")
                .redirect(cfind, ctx -> withFlags(ctx.getSource(), FLAG_KEEP_SEARCHING, true)))
            .then(argument("filter", entities())
                .executes(ctx -> listEntities(ctx.getSource(), getEntitySelector(ctx, "filter")))));
    }

    private static int listEntities(ServerCommandSource source, ClientEntitySelector selector) {
        boolean keepSearching = getFlag(source, FLAG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cfind", new FindTask(selector));

            sendFeedback(new TranslatableText("commands.cfind.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return 0;
        } else {
            List<Entity> entities = selector.getEntities(source);

            if (entities.isEmpty()) {
                sendError(new TranslatableText("commands.cfind.noMatch"));
                return 0;
            }

            sendFeedback(new TranslatableText("commands.cfind.success", entities.size()).formatted(Formatting.BOLD));
            for (Entity entity : entities) {
                sendEntityFoundMessage(source, entity);
            }

            return entities.size();
        }
    }

    private static void sendEntityFoundMessage(ServerCommandSource source, Entity entity) {
        double distance = Math.sqrt(entity.squaredDistanceTo(source.getPosition()));
        sendFeedback(new TranslatableText("commands.cfind.found.left", entity.getName(), distance)
                .append(getLookCoordsTextComponent(entity.getBlockPos()))
                .append(new TranslatableText("commands.cfind.found.right", entity.getName(), distance)));
    }

    private static class FindTask extends LongTask {
        private final ClientEntitySelector selector;
        private final Set<UUID> foundEntities = new HashSet<>();

        private FindTask(ClientEntitySelector selector) {
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
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            assert player != null;
            FakeCommandSource source = new FakeCommandSource(player);
            for (Entity entity : selector.getEntities(source)) {
                if (foundEntities.add(entity.getUuid())) {
                    sendEntityFoundMessage(source, entity);
                }
            }
            scheduleDelay();
        }
    }

}
