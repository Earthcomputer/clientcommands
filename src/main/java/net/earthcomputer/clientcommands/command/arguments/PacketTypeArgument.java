package net.earthcomputer.clientcommands.command.arguments;

import com.google.common.collect.Streams;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.netty.channel.ChannelPipeline;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PacketTypeArgument implements ArgumentType<ResourceLocation> {

    private static final Collection<String> EXAMPLES = Arrays.asList("add_entity", "minecraft:add_entity", "commands");

    private static final DynamicCommandExceptionType UNKNOWN_PACKET_EXCEPTION = new DynamicCommandExceptionType(packet -> Component.translatable("commands.clisten.unknownPacket", packet));

    public static PacketTypeArgument packet() {
        return new PacketTypeArgument();
    }

    public static ResourceLocation getPacket(final CommandContext<FabricClientCommandSource> context, final String name) {
        return context.getArgument(name, ResourceLocation.class);
    }

    @Override
    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        ResourceLocation packetId = ResourceLocation.read(reader);
        PacketTypes types = PacketTypes.get();
        if (types == null || (!types.clientbound.contains(packetId) && !types.serverbound.contains(packetId) && !types.c2cbound.contains(packetId))) {
            reader.setCursor(start);
            throw UNKNOWN_PACKET_EXCEPTION.createWithContext(reader, packetId);
        }
        return packetId;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        PacketTypes types = PacketTypes.get();
        if (types == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggestResource(Streams.concat(types.clientbound.stream(), types.serverbound.stream(), types.c2cbound.stream()), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private record PacketTypes(Set<ResourceLocation> clientbound, Set<ResourceLocation> serverbound, Set<ResourceLocation> c2cbound) {
        private static final Map<Object, Set<ResourceLocation>> packetTypesCache = new WeakHashMap<>();

        private static Set<ResourceLocation> getPacketTypes(ProtocolInfo<?> protocolInfo) {
            return ((IdDispatchCodec<?, ?, PacketType<?>>) protocolInfo.codec()).toId.keySet().stream()
                .map(PacketType::id)
                .collect(Collectors.toSet());
        }

        @Nullable
        private static PacketTypes get() {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return null;
            }
            ChannelPipeline pipeline = connection.getConnection().channel.pipeline();
            var decoder = (PacketDecoder<?>) pipeline.get("decoder");
            var clientbound = packetTypesCache.computeIfAbsent(decoder, k -> getPacketTypes(decoder.protocolInfo));
            var encoder = (PacketEncoder<?>) pipeline.get("encoder");
            var serverbound = packetTypesCache.computeIfAbsent(encoder, k -> getPacketTypes(encoder.protocolInfo));
            var c2cbound = packetTypesCache.computeIfAbsent("c2c", k -> getPacketTypes(C2CPacketHandler.C2C));
            return new PacketTypes(clientbound, serverbound, c2cbound);
        }
    }
}
