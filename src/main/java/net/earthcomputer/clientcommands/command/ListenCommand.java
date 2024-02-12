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
import net.earthcomputer.clientcommands.features.MappingsHelper;
import net.earthcomputer.clientcommands.features.PacketDumper;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

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

    private static final SimpleCommandExceptionType ALREADY_LISTENING_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.clisten.add.failed"));
    private static final SimpleCommandExceptionType NOT_LISTENING_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.clisten.remove.failed"));

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

        source.sendFeedback(Component.translatable("commands.clisten.add.success"));

        if (callback == null) {
            callback = (packet, side) -> {
                String packetClassName = packet.getClass().getName().replace('.', '/');
                Optional<String> mojmapPacketName = MappingsHelper.namedOrIntermediaryToMojmap_class(packetClassName);

                String packetData;
                Component packetDataPreview;
                if (Configs.packetDumpMethod == Configs.PacketDumpMethod.BYTE_BUF) {
                    StringWriter writer = new StringWriter();
                    try {
                        PacketDumper.dumpPacket(packet, new JsonWriter(writer));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    packetData = writer.toString();
                    packetDataPreview = Component.literal(packetData.replace("\u00a7", "\\u00a7"));
                } else {
                    try {
                        packetDataPreview = serialize(packet);
                        packetData = packetDataPreview.getString();
                    } catch (StackOverflowError e) {
                        e.printStackTrace();
                        return;
                    }
                }

                MutableComponent packetComponent = Component.literal(mojmapPacketName.orElseThrow().substring(MOJMAP_PACKET_PREFIX.length())).withStyle(s -> s
                    .withUnderlined(true)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, packetDataPreview))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, packetData)));

                switch (side) {
                    case CLIENTBOUND -> source.sendFeedback(Component.translatable("commands.clisten.receivedPacket", packetComponent));
                    case SERVERBOUND -> source.sendFeedback(Component.translatable("commands.clisten.sentPacket", packetComponent));
                }
            };
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int remove(FabricClientCommandSource source, Class<Packet<?>> packetClass) throws CommandSyntaxException {
        if (!packets.remove(packetClass)) {
            throw NOT_LISTENING_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable("commands.clisten.remove.success"));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        int amount = packets.size();
        if (amount == 0) {
            source.sendFeedback(Component.translatable("commands.clisten.list.none"));
        } else {
            source.sendFeedback(Component.translatable("commands.clisten.list"));
            packets.forEach(packetClass -> {
                String packetClassName = packetClass.getName().replace('.', '/');
                Optional<String> mojmapName = MappingsHelper.namedOrIntermediaryToMojmap_class(packetClassName);
                source.sendFeedback(Component.literal(mojmapName.orElseThrow().substring(MOJMAP_PACKET_PREFIX.length())));
            });
        }

        return amount;
    }

    private static int clear(FabricClientCommandSource source) {
        int amount = packets.size();
        packets.clear();
        source.sendFeedback(Component.translatable("commands.clisten.clear"));
        return amount;
    }

    private static Component serialize(Object object) {
        try {
            if (SEEN.get().add(object)) {
                return serializeInner(object);
            }
            return Component.empty();
        } finally {
            SEEN.get().remove(object);
            if (SEEN.get().isEmpty()) {
                SEEN.remove();
            }
        }
    }

    private static Component serializeInner(Object object) {
        if (object == null) {
            return Component.literal("null");
        }
        if (object instanceof Component component) {
            return component;
        }
        if (object instanceof String string) {
            return Component.literal(string);
        }
        if (object instanceof Number || object instanceof Boolean) {
            return Component.literal(object.toString());
        }
        if (object instanceof Optional<?> optional) {
            return optional.isPresent() ? serialize(optional.get()) : Component.literal("empty");
        }
        if (object instanceof Date date) {
            return Component.translationArg(date);
        }
        if (object instanceof Instant instant) {
            return Component.translationArg(Date.from(instant));
        }
        if (object instanceof UUID uuid) {
            return Component.translationArg(uuid);
        }
        if (object instanceof ChunkPos chunkPos) {
            return Component.translationArg(chunkPos);
        }
        if (object instanceof ResourceLocation resourceLocation) {
            return Component.translationArg(resourceLocation);
        }
        if (object instanceof Message message) {
            return Component.translationArg(message);
        }
        if (object.getClass().isArray()) {
            MutableComponent component = Component.literal("[");
            int lengthMinusOne = Array.getLength(object) - 1;
            if (lengthMinusOne < 0) {
                return component.append("]");
            }
            for (int i = 0; i < lengthMinusOne; i++) {
                component.append(serialize(Array.get(object, i))).append(", ");
            }
            return component.append(serialize(Array.get(object, lengthMinusOne))).append("]");
        }
        if (object instanceof Collection<?> collection) {
            MutableComponent component = Component.literal("[");
            component.append(collection.stream().map(e -> serialize(e).copy()).reduce((l, r) -> l.append(", ").append(r)).orElse(Component.empty()));
            return component.append("]");
        }
        if (object instanceof Map<?, ?> map) {
            MutableComponent component = Component.literal("{");
            component.append(map.entrySet().stream().map(e -> serialize(e.getKey()).copy().append("=").append(serialize(e.getValue()))).reduce((l, r) -> l.append(", ").append(r)).orElse(Component.empty()));
            return component.append("}");
        }
        if (object instanceof Registry<?> registry) {
            return Component.translationArg(registry.key().location());
        }
        if (object instanceof ResourceKey<?> resourceKey) {
            MutableComponent component = Component.literal("{");
            component.append("registry=").append(serialize(resourceKey.registry())).append(", ");
            component.append("location=").append(serialize(resourceKey.location()));
            return component.append("}");
        }
        if (object instanceof Holder<?> holder) {
            MutableComponent component = Component.literal("{");
            component.append("kind=").append(serialize(holder.kind().name())).append(", ");
            component.append("value=").append(serialize(holder.value()));
            return component.append("}");
        }

        String className = object.getClass().getName().replace(".", "/");
        String mojmapClassName = MappingsHelper.namedOrIntermediaryToMojmap_class(className).orElse(className);
        mojmapClassName = mojmapClassName.substring(mojmapClassName.lastIndexOf('/') + 1);

        MutableComponent component = Component.literal(mojmapClassName + '{');
        component.append(ReflectionUtils.getAllFields(object.getClass())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(field -> {
                String fieldName = field.getName();
                Optional<String> mojmapFieldName = MappingsHelper.namedOrIntermediaryToMojmap_field(className, fieldName);
                try {
                    field.setAccessible(true);
                    return Component.literal(mojmapFieldName.orElse(fieldName) + '=').append(serialize(field.get(object)));
                } catch (InaccessibleObjectException | ReflectiveOperationException e) {
                    try {
                        VarHandle varHandle = UnsafeUtils.getImplLookup().findVarHandle(object.getClass(), fieldName, field.getType());
                        return Component.literal(mojmapFieldName.orElse(fieldName) + '=').append(serialize(varHandle.get(object)));
                    } catch (ReflectiveOperationException ex) {
                        return Component.literal(mojmapFieldName.orElse(fieldName) + '=').append(Component.translatable("commands.clisten.packetError").withStyle(ChatFormatting.DARK_RED));
                    }
                }
            })
            .reduce((l, r) -> l.append(", ").append(r))
            .orElse(Component.empty()));
        return component.append("}");
    }

    public static void onPacket(Packet<?> packet, PacketFlow side) {
        if (!packets.contains(packet.getClass())) {
            return;
        }
        callback.apply(packet, side);
    }

    @FunctionalInterface
    private interface PacketCallback {
        void apply(Packet<?> packet, PacketFlow side);
    }
}
