package net.earthcomputer.clientcommands.command;

import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.earthcomputer.clientcommands.*;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.*;

import static net.earthcomputer.clientcommands.command.arguments.MojmapPacketClassArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ListenCommand {

    private static final SimpleCommandExceptionType ALREADY_LISTENING_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.clisten.add.failed"));
    private static final SimpleCommandExceptionType NOT_LISTENING_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.clisten.remove.failed"));

    private static final Set<Class<Packet<?>>> packets = new HashSet<>();

    private static PacketCallback callback;

    private static final ThreadLocal<ReferenceSet<Object>> SEEN = ThreadLocal.withInitial(ReferenceOpenHashSet::new);

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("clisten")
            .then(literal("add")
                .then(argument("packet", packet())
                    .executes(ctx -> add(ctx.getSource(), getPacket(ctx, "packet")))))
            .then(literal("remove")
                .then(argument("packet", packet())
                    .executes(ctx -> remove(ctx.getSource(), getPacket(ctx, "packet")))))
            .then(literal("list")
                .executes(ctx -> list(ctx.getSource())))
            .then(literal("clear")
                .executes(ctx -> clear(ctx.getSource()))));
    }

    private static int add(FabricClientCommandSource source, Class<Packet<?>> packetClass) throws CommandSyntaxException {
        if (!packets.add(packetClass)) {
            throw ALREADY_LISTENING_EXCEPTION.create();
        }

        source.sendFeedback(Text.translatable("commands.clisten.add.success"));

        if (callback == null) {
            callback = (packet, side) -> {
                String packetClassName = packet.getClass().getName().replace('.', '/');
                Optional<String> mojmapPacketName = MappingsHelper.namedOrIntermediaryToMojmap_class(packetClassName);

                String packetData;
                Text packetDataPreview;
                if (Configs.packetDumpMethod == Configs.PacketDumpMethod.BYTE_BUF) {
                    StringWriter writer = new StringWriter();
                    try {
                        PacketDumper.dumpPacket(packet, new JsonWriter(writer));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    packetData = writer.toString();
                    packetDataPreview = Text.literal(packetData.replace("\u00a7", "\\u00a7"));
                } else {
                    try {
                        packetDataPreview = serialize(packet);
                        packetData = packetDataPreview.getString();
                    } catch (StackOverflowError e) {
                        e.printStackTrace();
                        return;
                    }
                }

                MutableText packetText = Text.literal(mojmapPacketName.orElseThrow().substring(MOJMAP_PACKET_PREFIX.length())).styled(s -> s
                    .withUnderline(true)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, packetDataPreview))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, packetData)));

                switch (side) {
                    case CLIENTBOUND -> source.sendFeedback(Text.translatable("commands.clisten.receivedPacket", packetText));
                    case SERVERBOUND -> source.sendFeedback(Text.translatable("commands.clisten.sentPacket", packetText));
                }
            };
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int remove(FabricClientCommandSource source, Class<Packet<?>> packetClass) throws CommandSyntaxException {
        if (!packets.remove(packetClass)) {
            throw NOT_LISTENING_EXCEPTION.create();
        }

        source.sendFeedback(Text.translatable("commands.clisten.remove.success"));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        int amount = packets.size();
        if (amount == 0) {
            source.sendFeedback(Text.translatable("commands.clisten.list.none"));
        } else {
            source.sendFeedback(Text.translatable("commands.clisten.list"));
            packets.forEach(packetClass -> {
                String packetClassName = packetClass.getName().replace('.', '/');
                Optional<String> mojmapName = MappingsHelper.namedOrIntermediaryToMojmap_class(packetClassName);
                source.sendFeedback(Text.literal(mojmapName.orElseThrow().substring(MOJMAP_PACKET_PREFIX.length())));
            });
        }

        return amount;
    }

    private static int clear(FabricClientCommandSource source) {
        int amount = packets.size();
        packets.clear();
        source.sendFeedback(Text.translatable("commands.clisten.clear"));
        return amount;
    }

    private static Text serialize(Object object) {
        try {
            if (SEEN.get().add(object)) {
                return serializeInner(object);
            }
            return Text.empty();
        } finally {
            SEEN.get().remove(object);
            if (SEEN.get().isEmpty()) {
                SEEN.remove();
            }
        }
    }

    private static Text serializeInner(Object object) {
        if (object == null) {
            return Text.literal("null");
        }
        if (object instanceof Text text) {
            return text;
        }
        if (object instanceof String string) {
            return Text.literal(string);
        }
        if (object instanceof Number || object instanceof Boolean) {
            return Text.literal(object.toString());
        }
        if (object instanceof Optional<?> optional) {
            return optional.isPresent() ? serialize(optional.get()) : Text.literal("empty");
        }
        if (object instanceof Date date) {
            return Text.of(date);
        }
        if (object instanceof Instant instant) {
            return Text.of(Date.from(instant));
        }
        if (object instanceof UUID uuid) {
            return Text.of(uuid);
        }
        if (object instanceof ChunkPos chunkPos) {
            return Text.of(chunkPos);
        }
        if (object instanceof Identifier identifier) {
            return Text.of(identifier);
        }
        if (object instanceof Message message) {
            return Text.of(message);
        }
        if (object.getClass().isArray()) {
            MutableText text = Text.literal("[");
            int lengthMinusOne = Array.getLength(object) - 1;
            if (lengthMinusOne < 0) {
                return text.append("]");
            }
            for (int i = 0; i < lengthMinusOne; i++) {
                text.append(serialize(Array.get(object, i))).append(", ");
            }
            return text.append(serialize(Array.get(object, lengthMinusOne))).append("]");
        }
        if (object instanceof Collection<?> collection) {
            MutableText text = Text.literal("[");
            text.append(collection.stream().map(e -> serialize(e).copy()).reduce((l, r) -> l.append(", ").append(r)).orElse(Text.empty()));
            return text.append("]");
        }
        if (object instanceof Map<?, ?> map) {
            MutableText text = Text.literal("{");
            text.append(map.entrySet().stream().map(e -> serialize(e.getKey()).copy().append("=").append(serialize(e.getValue()))).reduce((l, r) -> l.append(", ").append(r)).orElse(Text.empty()));
            return text.append("}");
        }
        if (object instanceof Registry<?> registry) {
            return Text.of(registry.getKey().getValue());
        }
        if (object instanceof RegistryKey<?> registryKey) {
            MutableText text = Text.literal("{");
            text.append("registry=").append(serialize(registryKey.getRegistry())).append(", ");
            text.append("value=").append(serialize(registryKey.getValue()));
            return text.append("}");
        }
        if (object instanceof RegistryEntry<?> registryEntry) {
            MutableText text = Text.literal("{");
            text.append("key=").append(serialize(registryEntry.getKey())).append(", ");
            text.append("value=").append(serialize(registryEntry.value()));
            return text.append("}");
        }

        String className = object.getClass().getName().replace(".", "/");
        String mojmapClassName = MappingsHelper.namedOrIntermediaryToMojmap_class(className).orElse(className);
        mojmapClassName = mojmapClassName.substring(mojmapClassName.lastIndexOf('/') + 1);

        MutableText text = Text.literal(mojmapClassName + '{');
        text.append(ReflectionUtils.getAllFields(object.getClass())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(field -> {
                String fieldName = field.getName();
                Optional<String> mojmapFieldName = MappingsHelper.namedOrIntermediaryToMojmap_field(className, fieldName);
                try {
                    field.setAccessible(true);
                    return Text.literal(mojmapFieldName.orElse(fieldName) + '=').append(serialize(field.get(object)));
                } catch (InaccessibleObjectException | ReflectiveOperationException e) {
                    try {
                        VarHandle varHandle = UnsafeUtils.getImplLookup().findVarHandle(object.getClass(), fieldName, field.getType());
                        return Text.literal(mojmapFieldName.orElse(fieldName) + '=').append(serialize(varHandle.get(object)));
                    } catch (ReflectiveOperationException ex) {
                        return Text.literal(mojmapFieldName.orElse(fieldName) + '=').append(Text.translatable("commands.clisten.packetError").formatted(Formatting.DARK_RED));
                    }
                }
            })
            .reduce((l, r) -> l.append(", ").append(r))
            .orElse(Text.empty()));
        return text.append("}");
    }

    public static void onPacket(Packet<?> packet, NetworkSide side) {
        if (!packets.contains(packet.getClass())) {
            return;
        }
        callback.apply(packet, side);
    }

    @FunctionalInterface
    private interface PacketCallback {
        void apply(Packet<?> packet, NetworkSide side);
    }
}
