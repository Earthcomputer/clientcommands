package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xpple.clientarguments.arguments.CEntityArgument;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import dev.xpple.clientarguments.arguments.CEntitySelectorParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * An argument type that works for entities in render distance as well as all players in the player list
 */
public class EntityUUIDArgument implements ArgumentType<EntityUUIDArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "dd12be42-52a9-4a91-a8a1-11c01849e498", "@e");

    public static net.earthcomputer.clientcommands.command.arguments.EntityUUIDArgument entityUuid() {
        return new net.earthcomputer.clientcommands.command.arguments.EntityUUIDArgument();
    }

    public static UUID getEntityUuid(CommandContext<FabricClientCommandSource> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, Result.class).getUUID(context.getSource());
    }

    @Override
    public Result parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            CEntitySelectorParser selectorParser = new CEntitySelectorParser(reader, true);
            CEntitySelector selector = selectorParser.parse();
            return new SelectorBacked(selector);
        }
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        String argument = reader.getString().substring(start, reader.getCursor());
        try {
            UUID uuid = UUID.fromString(argument);
            return new UuidBacked(uuid);
        } catch (IllegalArgumentException ignore) {
        }
        return new NameBacked(argument);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider commandSource) {
            StringReader stringReader = new StringReader(builder.getInput());
            stringReader.setCursor(builder.getStart());
            CEntitySelectorParser entitySelectorParser = new CEntitySelectorParser(stringReader, true);

            try {
                entitySelectorParser.parse();
            } catch (CommandSyntaxException ignored) {
            }

            return entitySelectorParser.fillSuggestions(builder, builderx -> {
                Collection<String> collection = commandSource.getOnlinePlayerNames();
                Iterable<String> iterable = Iterables.concat(collection, commandSource.getSelectedEntities());
                SharedSuggestionProvider.suggest(iterable, builderx);
            });
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    sealed interface Result {
        UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException;
    }

    private record NameBacked(String name) implements Result {
        @Override
        public UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException {
            PlayerInfo entry = source.getClient().getConnection().getPlayerInfo(name);
            if (entry == null) {
                throw CEntityArgument.ENTITY_NOT_FOUND_EXCEPTION.create();
            }
            return entry.getProfile().getId();
        }
    }

    private record UuidBacked(UUID uuid) implements Result {
        @Override
        public UUID getUUID(FabricClientCommandSource source) {
            return uuid;
        }
    }

    private record SelectorBacked(CEntitySelector selector) implements Result {
        @Override
        public UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException {
            return selector.findSingleEntity(source).getUUID();
        }
    }
}
