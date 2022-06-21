package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xpple.clientarguments.arguments.CEntityArgumentType;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import dev.xpple.clientarguments.arguments.CEntitySelectorReader;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * An argument type that works for entities in render distance as well as all players in the player list
 */
public class EntityUUIDArgumentType implements ArgumentType<EntityUUIDArgumentType.EntityUUIDArgument> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "dd12be42-52a9-4a91-a8a1-11c01849e498", "@e");

    public static EntityUUIDArgumentType entityUuid() {
        return new EntityUUIDArgumentType();
    }

    public static UUID getEntityUuid(CommandContext<FabricClientCommandSource> context, String name) throws CommandSyntaxException {
        return context.getArgument(name, EntityUUIDArgument.class).getUUID(context.getSource());
    }

    @Override
    public EntityUUIDArgument parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            CEntitySelectorReader selectorReader = new CEntitySelectorReader(reader);
            CEntitySelector selector = selectorReader.read();
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
        if (context.getSource() instanceof CommandSource commandSource) {
            StringReader stringReader = new StringReader(builder.getInput());
            stringReader.setCursor(builder.getStart());
            CEntitySelectorReader entitySelectorReader = new CEntitySelectorReader(stringReader, true);

            try {
                entitySelectorReader.read();
            } catch (CommandSyntaxException ignored) {
            }

            return entitySelectorReader.listSuggestions(builder, (builderx) -> {
                Collection<String> collection = commandSource.getPlayerNames();
                Iterable<String> iterable = Iterables.concat(collection, commandSource.getEntitySuggestions());
                CommandSource.suggestMatching(iterable, builderx);
            });
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    sealed interface EntityUUIDArgument {
        UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException;
    }

    private record NameBacked(String name) implements EntityUUIDArgument {
        @Override
        public UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException {
            PlayerListEntry entry = source.getClient().getNetworkHandler().getPlayerListEntry(name);
            if (entry == null) {
                throw CEntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create();
            }
            return entry.getProfile().getId();
        }
    }

    private record UuidBacked(UUID uuid) implements EntityUUIDArgument {
        @Override
        public UUID getUUID(FabricClientCommandSource source) {
            return uuid;
        }
    }

    private record SelectorBacked(CEntitySelector selector) implements EntityUUIDArgument {
        @Override
        public UUID getUUID(FabricClientCommandSource source) throws CommandSyntaxException {
            return selector.getEntity(source).getUuid();
        }
    }
}
