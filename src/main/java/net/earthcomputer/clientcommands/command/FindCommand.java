package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CEntityArgument.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FindCommand {

    private static final Flag<Boolean> FLAG_KEEP_SEARCHING = Flag.ofFlag("keep-searching").build();

    private static final SimpleCommandExceptionType NO_MATCH_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cfind.noMatch"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cfind = dispatcher.register(literal("cfind")
            .then(argument("filter", entities())
                .executes(ctx -> listEntities(ctx.getSource(), ctx.getArgument("filter", CEntitySelector.class)))));
        FLAG_KEEP_SEARCHING.addToCommand(dispatcher, cfind, ctx -> true);
    }

    private static int listEntities(FabricClientCommandSource source, CEntitySelector selector) throws CommandSyntaxException {
        boolean keepSearching = getFlag(source, FLAG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cfind", new FindTask(source, selector));

            source.sendFeedback(Component.translatable("commands.cfind.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return Command.SINGLE_SUCCESS;
        } else {
            List<? extends Entity> entities = selector.findEntities(source);

            if (entities.isEmpty()) {
                throw NO_MATCH_EXCEPTION.create();
            }

            source.sendFeedback(Component.translatable("commands.cfind.success", entities.size()).withStyle(ChatFormatting.BOLD));
            for (Entity entity : entities) {
                sendEntityFoundMessage(source, entity);
            }

            return entities.size();
        }
    }

    private static void sendEntityFoundMessage(FabricClientCommandSource source, Entity entity) {
        String distance = "%.2f".formatted(Math.sqrt(entity.distanceToSqr(source.getPosition())));
        source.sendFeedback(Component.translatable(
            "commands.cfind.found",
            entity.getName(),
            getLookCoordsTextComponent(entity.blockPosition()),
            distance
        ));
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
            return Minecraft.getInstance().player != null;
        }

        @Override
        public void increment() {
        }

        @Override
        public void body() {
            try {
                for (Entity entity : selector.findEntities(this.source)) {
                    if (foundEntities.add(entity.getUUID())) {
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
