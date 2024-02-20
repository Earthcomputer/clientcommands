package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.features.MappingsHelper;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.Optionull;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MojmapPacketClassArgumentType implements ArgumentType<Class<? extends Packet<?>>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("ClientboundPlayerChatPacket", "ClientboundSystemChatMessage", "ServerboundContainerSlotStateChangedPacket");

    private static final DynamicCommandExceptionType UNKNOWN_PACKET_EXCEPTION = new DynamicCommandExceptionType(packet -> Component.translatable("commands.clisten.unknownPacket", packet));

    private static final Map<String, Class<? extends Packet<?>>> mojmapPackets = Arrays.stream(ConnectionProtocol.values())
        .flatMap(connectionProtocol -> connectionProtocol.flows.values().stream())
        .flatMap(codecData -> codecData.packetSet.classToId.keySet().stream())
        .map(clazz -> Optionull.map(MappingsHelper.namedOrIntermediaryToMojmap_class(clazz.getName().replace('.', '/')),
            packet -> Map.entry(packet.substring(packet.lastIndexOf('/') + 1), clazz)))
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    public static MojmapPacketClassArgumentType packet() {
        return new MojmapPacketClassArgumentType();
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Packet<?>> getPacket(final CommandContext<FabricClientCommandSource> context, final String name) {
        return (Class<? extends Packet<?>>) context.getArgument(name, Class.class);
    }

    @Override
    public Class<? extends Packet<?>> parse(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        while (reader.canRead() && (StringReader.isAllowedInUnquotedString(reader.peek()) || reader.peek() == '$' )) {
            reader.skip();
        }
        String packet = reader.getString().substring(start, reader.getCursor());
        Class<? extends Packet<?>> packetClass = mojmapPackets.get(packet);
        if (packetClass == null) {
            throw UNKNOWN_PACKET_EXCEPTION.create(packet);
        }
        return packetClass;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(mojmapPackets.keySet(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
