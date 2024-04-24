package net.earthcomputer.clientcommands.command;

import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.ReflectionUtils;
import net.earthcomputer.clientcommands.UnsafeUtils;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.earthcomputer.clientcommands.command.arguments.PacketTypeArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ListenCommand {

    private static volatile boolean isEnabled = true;

    public static void disable() {
        isEnabled = false;
    }

    private static final SimpleCommandExceptionType COMMAND_DISABLED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.clisten.commandDisabled"));
    private static final SimpleCommandExceptionType ALREADY_LISTENING_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.clisten.add.failed"));
    private static final SimpleCommandExceptionType NOT_LISTENING_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.clisten.remove.failed"));

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<ResourceLocation> packets = new HashSet<>();

    private static PacketCallback callback;

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

    private static int add(FabricClientCommandSource source, ResourceLocation packetType) throws CommandSyntaxException {
        checkEnabled();
        if (!packets.add(packetType)) {
            throw ALREADY_LISTENING_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable("commands.clisten.add.success"));

        if (callback == null) {
            callback = (packet, side) -> {
                String packetData;
                Component packetDataPreview;
                if (Configs.packetDumpMethod == Configs.PacketDumpMethod.BYTE_BUF) {
                    StringWriter writer = new StringWriter();
                    try {
                        PacketDumper.dumpPacket(packet, new JsonWriter(writer));
                    } catch (IOException e) {
                        LOGGER.error("Could not dump packet", e);
                        return;
                    }
                    packetData = writer.toString();
                    packetDataPreview = Component.literal(packetData.replace("\u00a7", "\\u00a7"));
                } else {
                    try {
                        packetDataPreview = serialize(packet, new ReferenceOpenHashSet<>(), 0);
                        packetData = packetDataPreview.getString();
                    } catch (StackOverflowError e) {
                        LOGGER.error("Could not serialize packet into a Component", e);
                        return;
                    }
                }

                String packetClassName = packet.getClass().getName().replace('.', '/');
                String mojmapPacketName = Objects.requireNonNullElse(MappingsHelper.namedOrIntermediaryToMojmap_class(packetClassName), packetClassName);
                mojmapPacketName = mojmapPacketName.substring(mojmapPacketName.lastIndexOf('/') + 1);

                MutableComponent packetComponent = Component.literal(mojmapPacketName).withStyle(s -> s
                    .withUnderlined(true)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, packetDataPreview))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, packetData)));

                switch (side) {
                    case SERVERBOUND -> source.sendFeedback(Component.translatable("commands.clisten.sentPacket", packetComponent));
                    case CLIENTBOUND -> source.sendFeedback(Component.translatable("commands.clisten.receivedPacket", packetComponent));
                    case C2C_OUTBOUND -> source.sendFeedback(Component.translatable("commands.clisten.sentC2CPacket", packetComponent));
                    case C2C_INBOUND -> source.sendFeedback(Component.translatable("commands.clisten.receivedC2CPacket", packetComponent));
                }
            };
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int remove(FabricClientCommandSource source, ResourceLocation packetType) throws CommandSyntaxException {
        checkEnabled();
        if (!packets.remove(packetType)) {
            throw NOT_LISTENING_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable("commands.clisten.remove.success"));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) throws CommandSyntaxException {
        checkEnabled();
        int amount = packets.size();
        if (amount == 0) {
            source.sendFeedback(Component.translatable("commands.clisten.list.none"));
        } else {
            source.sendFeedback(Component.translatable("commands.clisten.list"));
            for (ResourceLocation packetType : packets) {
                source.sendFeedback(Component.literal(packetType.toString()));
            }
        }

        return amount;
    }

    private static int clear(FabricClientCommandSource source) throws CommandSyntaxException {
        checkEnabled();
        int amount = packets.size();
        packets.clear();
        source.sendFeedback(Component.translatable("commands.clisten.clear"));
        return amount;
    }

    private static void checkEnabled() throws CommandSyntaxException {
        if (!isEnabled) {
            throw COMMAND_DISABLED_EXCEPTION.create();
        }
    }

    private static Component serialize(Object object, Set<Object> seen, int depth) {
        try {
            if (depth <= Configs.maximumPacketFieldDepth && seen.add(object)) {
                return serializeInner(object, seen, depth);
            }
            return Component.empty();
        } finally {
            seen.remove(object);
        }
    }

    private static Component serializeInner(Object object, Set<Object> seen, int depth) {
        return switch (object) {
            case null -> Component.literal("null");
            case Component component -> component;
            case String string -> Component.literal(string);
            case Number number -> Component.literal(number.toString());
            case Boolean bool -> Component.literal(bool.toString());
            case Optional<?> optional -> optional.isPresent() ? serialize(optional.get(), seen, depth + 1) : Component.literal("empty");
            case Date date -> Component.translationArg(date);
            case Instant instant -> Component.translationArg(Date.from(instant));
            case UUID uuid -> Component.translationArg(uuid);
            case ChunkPos chunkPos -> Component.translationArg(chunkPos);
            case ResourceLocation resourceLocation -> Component.translationArg(resourceLocation);
            case Message message -> Component.translationArg(message);
            case Collection<?> collection -> {
                MutableComponent component = Component.literal("[");
                component.append(collection.stream().map(e -> serialize(e, seen, depth + 1).copy()).reduce((l, r) -> l.append(", ").append(r)).orElse(Component.empty()));
                yield component.append("]");
            }
            case Map<?, ?> map -> {
                MutableComponent component = Component.literal("{");
                component.append(map.entrySet().stream().map(e -> serialize(e.getKey(), seen, depth + 1).copy().append("=").append(serialize(e.getValue(), seen, depth + 1))).reduce((l, r) -> l.append(", ").append(r)).orElse(Component.empty()));
                yield component.append("}");
            }
            case Registry<?> registry -> Component.translationArg(registry.key().location());
            case ResourceKey<?> resourceKey -> {
                MutableComponent component = Component.literal("{");
                component.append("registry=").append(serialize(resourceKey.registry(), seen, depth + 1)).append(", ");
                component.append("location=").append(serialize(resourceKey.location(), seen, depth + 1));
                yield component.append("}");
            }
            case Holder<?> holder -> {
                MutableComponent component = Component.literal("{");
                component.append("kind=").append(serialize(holder.kind().name(), seen, depth + 1)).append(", ");
                component.append("value=").append(serialize(holder.value(), seen, depth + 1));
                yield component.append("}");
            }
            default -> {
                if (object.getClass().isArray()) {
                    MutableComponent component = Component.literal("[");
                    int lengthMinusOne = Array.getLength(object) - 1;
                    if (lengthMinusOne < 0) {
                        yield component.append("]");
                    }
                    for (int i = 0; i < lengthMinusOne; i++) {
                        component.append(serialize(Array.get(object, i), seen, depth + 1)).append(", ");
                    }
                    yield component.append(serialize(Array.get(object, lengthMinusOne), seen, depth + 1)).append("]");
                }

                String className = object.getClass().getName().replace(".", "/");
                String mojmapClassName = Objects.requireNonNullElse(MappingsHelper.namedOrIntermediaryToMojmap_class(className), className);
                mojmapClassName = mojmapClassName.substring(mojmapClassName.lastIndexOf('/') + 1);

                MutableComponent component = Component.literal(mojmapClassName + '{');
                component.append(ReflectionUtils.getAllFields(object.getClass())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .map(field -> {
                        String fieldName = field.getName();
                        String mojmapFieldName = Objects.requireNonNullElse(MappingsHelper.namedOrIntermediaryToMojmap_field(className, fieldName), fieldName);
                        try {
                            field.setAccessible(true);
                            return Component.literal(mojmapFieldName + '=').append(serialize(field.get(object), seen, depth + 1));
                        } catch (InaccessibleObjectException | ReflectiveOperationException e) {
                            try {
                                MethodHandles.Lookup implLookup = UnsafeUtils.getImplLookup();
                                if (implLookup == null) {
                                    return Component.literal(mojmapFieldName + '=').append(Component.translatable("commands.clisten.packetError").withStyle(ChatFormatting.DARK_RED));
                                }
                                VarHandle varHandle = implLookup.findVarHandle(object.getClass(), fieldName, field.getType());
                                return Component.literal(mojmapFieldName + '=').append(serialize(varHandle.get(object), seen, depth + 1));
                            } catch (ReflectiveOperationException ex) {
                                return Component.literal(mojmapFieldName + '=').append(Component.translatable("commands.clisten.packetError").withStyle(ChatFormatting.DARK_RED));
                            }
                        }
                    })
                    .reduce((l, r) -> l.append(", ").append(r))
                    .orElse(Component.empty()));
                yield component.append("}");
            }
        };
    }

    public enum PacketFlow {
        SERVERBOUND,
        CLIENTBOUND,
        C2C_OUTBOUND,
        C2C_INBOUND;
    }

    public static void onPacket(Packet<?> packet, PacketFlow side) {
        if (!packets.contains(packet.type().id())) {
            return;
        }
        callback.apply(packet, side);
    }

    @FunctionalInterface
    private interface PacketCallback {
        void apply(Packet<?> packet, PacketFlow side);
    }
}
