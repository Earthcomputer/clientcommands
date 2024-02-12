package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.MappingsHelper;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.Packet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MojmapPacketClassArgumentType implements ArgumentType<Class<Packet<?>>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("ClientboundPlayerChatPacket", "ClientboundSystemChatMessage", "ServerboundContainerSlotStateChangedPacket");

    public static final String MOJMAP_PACKET_PREFIX = "net/minecraft/network/protocol/game/";

    private static final Set<String> mojmapPackets = MappingsHelper.mojmapClasses().stream()
        .map(MappingTreeView.ElementMappingView::getSrcName)
        .filter(name -> name.startsWith(MOJMAP_PACKET_PREFIX) && name.endsWith("Packet"))
        .map(name -> name.substring(MOJMAP_PACKET_PREFIX.length()))
        .collect(Collectors.toSet());

    public static MojmapPacketClassArgumentType packet() {
        return new MojmapPacketClassArgumentType();
    }

    @SuppressWarnings("unchecked")
    public static Class<Packet<?>> getPacket(final CommandContext<FabricClientCommandSource> context, final String name) {
        return (Class<Packet<?>>) context.getArgument(name, Class.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Packet<?>> parse(StringReader reader) throws CommandSyntaxException {
        String packet = MOJMAP_PACKET_PREFIX + reader.readString();
        Optional<String> mojmapPacketName = MappingsHelper.mojmapToNamedOrIntermediary_class(packet);
        if (mojmapPacketName.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
        }
        String packetClass = mojmapPacketName.get().replace('/', '.');
        try {
            return (Class<Packet<?>>) Class.forName(packetClass);
        } catch (ReflectiveOperationException e) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(mojmapPackets, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
