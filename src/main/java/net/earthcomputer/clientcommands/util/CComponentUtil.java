package net.earthcomputer.clientcommands.util;

import com.demonwav.mcdev.annotations.Translatable;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import dev.xpple.clientarguments.arguments.CEntitySelectorParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.LocalCoordinates;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.SelectorPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.data.BlockDataSource;
import net.minecraft.network.chat.contents.data.DataSource;
import net.minecraft.network.chat.contents.data.EntityDataSource;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class CComponentUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Callback> callbacks = new ConcurrentHashMap<>();

    private CComponentUtil() {
    }

    public static Component getLookCoordsTextComponent(BlockPos pos) {
        return getCommandTextComponent(Component.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ()),
            String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getLookCoordsTextComponent(MutableComponent component, BlockPos pos) {
        return getCommandTextComponent(component, String.format("/clook block %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getGlowCoordsTextComponent(BlockPos pos) {
        return getCommandTextComponent(Component.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ()),
            String.format("/cglow block %d %d %d 10", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getGlowButtonTextComponent(BlockPos pos) {
        return getCommandTextComponent(Component.translatable("commands.client.glow"), String.format("/cglow block %d %d %d 60", pos.getX(), pos.getY(), pos.getZ()));
    }

    public static Component getGlowButtonTextComponent(Entity entity) {
        return getCommandTextComponent(Component.translatable("commands.client.glow"), "/cglow entities " + entity.getStringUUID());
    }

    public static Component getCommandTextComponent(@Translatable String translationKey, String command) {
        return getCommandTextComponent(Component.translatable(translationKey), command);
    }

    public static Component getCommandTextComponent(MutableComponent component, String command) {
        return component.withStyle(style -> style.applyFormat(ChatFormatting.UNDERLINE)
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    public static ClickEvent callbackClickEvent(Runnable runnable) {
        return callbackClickEvent(runnable, 60_000_000_000L); // 1 minute timeout
    }

    public static ClickEvent callbackClickEvent(Runnable callback, long timeoutNanos) {
        UUID callbackId = UUID.randomUUID();
        callbacks.put(callbackId, new Callback(callback, System.nanoTime() + timeoutNanos));
        return new ClickEvent.RunCommand("/ccallback " + callbackId);
    }

    public static boolean runCallback(UUID callbackId) {
        Callback callback = callbacks.get(callbackId);
        if (callback == null) {
            return false;
        }
        callback.callback.run();
        return true;
    }

    public static MutableComponent updateForEntity(
        FabricClientCommandSource source,
        Component component,
        @Nullable Entity entity,
        int recursionDepth
    ) throws CommandSyntaxException {
        if (recursionDepth > 100) {
            return component.copy();
        }

        MutableComponent result = resolveContents(source, component.getContents(), entity, recursionDepth + 1);

        for (Component sibling : component.getSiblings()) {
            result.append(updateForEntity(source, sibling, entity, recursionDepth + 1));
        }

        return result.withStyle(resolveStyle(source, component.getStyle(), entity, recursionDepth));
    }

    private static MutableComponent resolveContents(
        FabricClientCommandSource source,
        ComponentContents contents,
        @Nullable Entity entity,
        int recursionDepth
    ) throws CommandSyntaxException {
        return switch (contents) {
            case NbtContents nbt -> {
                if (nbt.compiledNbtPath == null) {
                    yield Component.empty();
                }

                Stream<Tag> tags = getData(source, nbt.getDataSource()).flatMap(tag -> {
                    try {
                        return nbt.compiledNbtPath.get(tag).stream();
                    } catch (CommandSyntaxException var3) {
                        return Stream.empty();
                    }
                });
                Component separator = nbt.getSeparator().isPresent() ? updateForEntity(source, nbt.getSeparator().get(), entity, recursionDepth) : ComponentUtils.DEFAULT_NO_STYLE_SEPARATOR;
                if (nbt.isInterpreting()) {
                    RegistryOps<Tag> ops = source.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    yield tags.flatMap(tag -> {
                        try {
                            Component nestedComponent = ComponentSerialization.CODEC.parse(ops, tag).getOrThrow();
                            return Stream.of(updateForEntity(source, nestedComponent, entity, recursionDepth));
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse component: {}", tag, e);
                            return Stream.of();
                        }
                    }).reduce((a, b) -> a.append(separator).append(b)).orElseGet(Component::empty);
                } else {
                    yield tags.map(tag -> tag instanceof StringTag(String str) ? str : tag.toString())
                        .map(Component::literal)
                        .reduce((a, b) -> a.append(separator).append(b))
                        .orElseGet(Component::empty);
                }
            }
            case SelectorContents(SelectorPattern(String pattern, EntitySelector ignored), Optional<Component> separator) -> {
                if (separator.isPresent()) {
                    separator = Optional.of(updateForEntity(source, separator.get(), entity, recursionDepth));
                }
                CEntitySelector selector = new CEntitySelectorParser(new StringReader(pattern), true).parse();
                yield ComponentUtils.formatList(selector.findEntities(source), separator, Entity::getDisplayName);
            }
            case TranslatableContents translatable -> {
                Object[] newArgs = new Object[translatable.getArgs().length];

                for (int i = 0; i < newArgs.length; i++) {
                    Object arg = translatable.getArgument(i);
                    if (arg instanceof Component componentArg) {
                        newArgs[i] = updateForEntity(source, componentArg, entity, recursionDepth);
                    } else {
                        newArgs[i] = arg;
                    }
                }

                yield MutableComponent.create(new TranslatableContents(translatable.getKey(), translatable.getFallback(), newArgs));
            }
            default -> MutableComponent.create(contents);
        };
    }

    private static Style resolveStyle(
        FabricClientCommandSource source,
        Style style,
        @Nullable Entity entity,
        int recursionDepth
    ) throws CommandSyntaxException {
        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent instanceof HoverEvent.ShowText(Component textToShow)) {
            HoverEvent newHoverEvent = new HoverEvent.ShowText(updateForEntity(source, textToShow, entity, recursionDepth + 1));
            return style.withHoverEvent(newHoverEvent);
        } else {
            return style;
        }
    }

    private static Stream<CompoundTag> getData(FabricClientCommandSource source, DataSource data) throws CommandSyntaxException {
        return switch (data) {
            case BlockDataSource(String ignored, Coordinates compiledPos) -> {
                if (compiledPos == null) {
                    yield Stream.empty();
                }
                BlockPos pos = getBlockPos(source, compiledPos);
                BlockEntity be = source.getWorld().getBlockEntity(pos);
                yield be == null ? Stream.empty() : Stream.of(be.saveWithFullMetadata(source.registryAccess()));
            }
            case EntityDataSource(String selectorPattern, EntitySelector ignored) -> {
                CEntitySelector selector = new CEntitySelectorParser(new StringReader(selectorPattern), true).parse();
                yield selector.findEntities(source).stream().map(NbtPredicate::getEntityTagToCompare);
            }
            default -> Stream.empty();
        };
    }

    private static BlockPos getBlockPos(FabricClientCommandSource source, Coordinates pos) {
        Vec3 sourcePos = source.getPosition();
        return switch (pos) {
            case WorldCoordinates worldCoords -> BlockPos.containing(worldCoords.x.get(sourcePos.x), worldCoords.y.get(sourcePos.y), worldCoords.z.get(sourcePos.z));
            case LocalCoordinates localCoords -> {
                Vec2 rotation = source.getRotation();
                float yawX = Mth.cos((rotation.y + 90) * Mth.DEG_TO_RAD);
                float yawZ = Mth.sin((rotation.y + 90) * Mth.DEG_TO_RAD);
                float forwardsHPitch = Mth.cos(-rotation.x * Mth.DEG_TO_RAD);
                float forwardsVPitch = Mth.sin(-rotation.x * Mth.DEG_TO_RAD);
                float upHPitch = Mth.cos((-rotation.x + 90) * Mth.DEG_TO_RAD);
                float upVPitch = Mth.sin((-rotation.x + 90) * Mth.DEG_TO_RAD);
                Vec3 forwardsVec = new Vec3(yawX * forwardsHPitch, forwardsVPitch, yawZ * forwardsHPitch);
                Vec3 upVec = new Vec3(yawX * upHPitch, upVPitch, yawZ * upHPitch);
                Vec3 leftVec = forwardsVec.cross(upVec).scale(-1.0);
                double dx = forwardsVec.x * localCoords.forwards + upVec.x * localCoords.up + leftVec.x * localCoords.left;
                double dy = forwardsVec.y * localCoords.forwards + upVec.y * localCoords.up + leftVec.y * localCoords.left;
                double dz = forwardsVec.z * localCoords.forwards + upVec.z * localCoords.up + leftVec.z * localCoords.left;
                yield BlockPos.containing(sourcePos.x + dx, sourcePos.y + dy, sourcePos.z + dz);
            }
            default -> BlockPos.ZERO;
        };
    }

    private record Callback(Runnable callback, long timeout) {
        static {
            Thread.ofVirtual().name("Clientcommands callback cleanup").start(() -> {
                while (true) {
                    try {
                        // This isn't really "busy-waiting" since the wait time is so long
                        //noinspection BusyWait
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    long now = System.nanoTime();
                    callbacks.values().removeIf(callback -> now - callback.timeout >= 0);
                }
            });
        }
    }
}
