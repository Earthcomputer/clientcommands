package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.VisibleForTesting;
import org.joml.Vector2d;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CDimensionArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WaypointCommand {

    private static final Map<String, Map<String, WaypointLocation>> waypoints = new HashMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cwaypoint.saveFailed"));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(name -> Component.translatable("commands.cwaypoint.alreadyExists", name));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(name -> Component.translatable("commands.cwaypoint.notFound", name));

    static {
        try {
            loadFile();
        } catch (Exception e) {
            LOGGER.error("Could not load waypoints file, hence /cwaypoint will not work!", e);
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwaypoint")
            .then(literal("add")
                .then(argument("name", word())
                    .then(argument("pos", blockPos())
                        .executes(ctx -> add(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos")))
                        .then(argument("dimension", dimension())
                            .executes(ctx -> add(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos"), getDimension(ctx, "dimension")))))))
            .then(literal("remove")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> {
                        Map<String, WaypointLocation> worldWaypoints = waypoints.get(getWorldIdentifier(ctx.getSource().getClient()));
                        return SharedSuggestionProvider.suggest(worldWaypoints != null ? worldWaypoints.keySet() : Collections.emptySet(), builder);
                    })
                    .executes(ctx -> remove(ctx.getSource(), getString(ctx, "name")))))
            .then(literal("edit")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> {
                        Map<String, WaypointLocation> worldWaypoints = waypoints.get(getWorldIdentifier(ctx.getSource().getClient()));
                        return SharedSuggestionProvider.suggest(worldWaypoints != null ? worldWaypoints.keySet() : Collections.emptySet(), builder);
                    })
                    .then(argument("pos", blockPos())
                        .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos")))
                        .then(argument("dimension", dimension())
                            .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos"), getDimension(ctx, "dimension")))))))
            .then(literal("list")
                .executes(ctx -> list(ctx.getSource()))
                .then(argument("current", bool())
                    .executes(ctx -> list(ctx.getSource(), getBool(ctx, "current"))))));
    }

    private static String getWorldIdentifier(Minecraft minecraft) {
        String worldIdentifier;
        if (minecraft.hasSingleplayerServer()) {
            // the level id remains the same even after the level is renamed
            worldIdentifier = minecraft.getSingleplayerServer().storageSource.getLevelId();
        } else {
            worldIdentifier = minecraft.getConnection().getConnection().getRemoteAddress().toString();
        }
        return worldIdentifier;
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos) throws CommandSyntaxException {
        return add(source, name, pos, source.getWorld().dimension());
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos, ResourceKey<Level> dimension) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source.getClient());

        Map<String, WaypointLocation> worldWaypoints = waypoints.computeIfAbsent(worldIdentifier, key -> new HashMap<>());

        if (worldWaypoints.putIfAbsent(name, new WaypointLocation(dimension, pos)) != null) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Component.translatable("commands.cwaypoint.add.success", name, formatCoordinates(pos), dimension.location()));
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source.getClient());

        Map<String, WaypointLocation> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.remove(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Component.translatable("commands.cwaypoint.remove.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int edit(FabricClientCommandSource source, String name, BlockPos pos) throws CommandSyntaxException {
        return edit(source, name, pos, source.getWorld().dimension());
    }

    private static int edit(FabricClientCommandSource source, String name, BlockPos pos, ResourceKey<Level> dimension) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source.getClient());

        Map<String, WaypointLocation> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.computeIfPresent(name, (key, value) -> new WaypointLocation(dimension, pos)) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Component.translatable("commands.cwaypoint.edit.success", name, formatCoordinates(pos), dimension.location()));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        return list(source, false);
    }

    private static int list(FabricClientCommandSource source, boolean current) {
        if (current) {
            String worldIdentifier = getWorldIdentifier(source.getClient());

            Map<String, WaypointLocation> worldWaypoints = waypoints.get(worldIdentifier);

            if (worldWaypoints == null || worldWaypoints.isEmpty()) {
                source.sendFeedback(Component.translatable("commands.cwaypoint.list.empty"));
                return 0;
            }

            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Component.translatable("commands.cwaypoint.list", name, formatCoordinates(waypoint.location()), waypoint.dimension().location())));
            return worldWaypoints.size();
        }

        if (waypoints.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cwaypoint.list.empty"));
            return 0;
        }

        int[] count = {0};
        waypoints.forEach((worldIdentifier, worldWaypoints) -> {
            if (worldWaypoints.isEmpty()) {
                return;
            }

            count[0] += worldWaypoints.size();

            source.sendFeedback(Component.literal(worldIdentifier).append(":"));
            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Component.translatable("commands.cwaypoint.list", name, formatCoordinates(waypoint.location()), waypoint.dimension().location())));
        });
        return count[0];
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            CompoundTag compoundTag = new CompoundTag();
            waypoints.forEach((worldIdentifier, worldWaypoints) -> compoundTag.put(worldIdentifier, worldWaypoints.entrySet().stream()
                .collect(CompoundTag::new, (result, entry) -> {
                    CompoundTag waypoint = new CompoundTag();
                    Tag pos = NbtUtils.writeBlockPos(entry.getValue().location());
                    waypoint.put("pos", pos);
                    String dimension = entry.getValue().dimension().location().toString();
                    waypoint.putString("Dimension", dimension);
                    result.put(entry.getKey(), waypoint);
                }, CompoundTag::merge)));
            rootTag.put("Waypoints", compoundTag);
            Path newFile = Files.createTempFile(ClientCommands.CONFIG_DIR, "waypoints", ".dat");
            NbtIo.write(rootTag, newFile);
            Path backupFile = ClientCommands.CONFIG_DIR.resolve("waypoints.dat_old");
            Path currentFile = ClientCommands.CONFIG_DIR.resolve("waypoints.dat");
            Util.safeReplaceFile(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws Exception {
        waypoints.clear();
        CompoundTag rootTag = NbtIo.read(ClientCommands.CONFIG_DIR.resolve("waypoints.dat"));
        if (rootTag == null) {
            return;
        }
        waypoints.putAll(deserializeWaypoints(rootTag));
    }

    @VisibleForTesting
    public static Map<String, Map<String, WaypointLocation>> deserializeWaypoints(CompoundTag rootTag) {
        Map<String, Map<String, WaypointLocation>> waypoints = new HashMap<>();

        CompoundTag compoundTag = rootTag.getCompound("Waypoints");
        compoundTag.getAllKeys().forEach(worldIdentifier -> {
            CompoundTag worldWaypoints = compoundTag.getCompound(worldIdentifier);
            waypoints.put(worldIdentifier, worldWaypoints.getAllKeys().stream()
                .collect(Collectors.toMap(Function.identity(), name -> {
                    CompoundTag waypoint = worldWaypoints.getCompound(name);
                    BlockPos pos = NbtUtils.readBlockPos(waypoint, "pos").orElseThrow();
                    ResourceKey<Level> dimension = Level.RESOURCE_KEY_CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, waypoint.get("Dimension"))).resultOrPartial(LOGGER::error).orElseThrow();
                    return new WaypointLocation(dimension, pos);
                })));
        });

        return waypoints;
    }

    private static Component formatCoordinates(BlockPos waypoint) {
        return ComponentUtils.wrapInSquareBrackets(Component.literal(waypoint.toShortString())).withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, waypoint.getX() + " " + waypoint.getY() + " " + waypoint.getZ()))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
        );
    }

    public static void registerEvents() {
        HudRenderCallback.EVENT.register(WaypointCommand::renderWaypointLabels);
        WorldRenderEvents.AFTER_ENTITIES.register(WaypointCommand::renderWaypointBoxes);
    }

    private static void renderWaypointLabels(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        String worldIdentifier = getWorldIdentifier(Minecraft.getInstance());
        Map<String, WaypointLocation> waypoints = WaypointCommand.waypoints.get(worldIdentifier);
        if (waypoints == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer gameRenderer = minecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Entity cameraEntity = camera.getEntity();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        double verticalFovRad = Math.toRadians(gameRenderer.getFov(camera, partialTicks, false));
        Window window = minecraft.getWindow();
        double aspectRatio = (double) window.getGuiScaledWidth() / window.getGuiScaledHeight();
        double horizontalFovRad = 2 * Math.atan(Math.tan(verticalFovRad / 2) * aspectRatio);

        Vec3 viewVector3 = cameraEntity.getViewVector(1.0f);
        Vector2d viewVector = new Vector2d(viewVector3.x, viewVector3.z);
        Vector2d position = new Vector2d(cameraEntity.getEyePosition().x, cameraEntity.getEyePosition().z);

        List<WaypointLabelLocation> xPositions = new ArrayList<>();
        waypoints.forEach((waypointName, waypoint) -> {
            if (!waypoint.dimension().location().equals(minecraft.level.dimension().location())) {
                return;
            }

            double distanceSquared = waypoint.location().distToCenterSqr(cameraEntity.position());
            long distance = Math.round(Math.sqrt(distanceSquared));
            Component label = ComponentUtils.wrapInSquareBrackets(Component.literal(waypointName + ' ' + distance).withStyle(ChatFormatting.YELLOW));

            Vector2d waypointLocation = new Vector2d(waypoint.location().getX(), waypoint.location().getZ());
            double angleRad = viewVector.angle(waypointLocation.sub(position, new Vector2d()));
            boolean right = angleRad > 0;
            angleRad = Math.abs(angleRad);

            int x;
            if (angleRad > horizontalFovRad / 2) {
                int width = minecraft.font.width(label);
                x = right ? guiGraphics.guiWidth() - width / 2 : width / 2;
            } else {
                // V is the view vector
                // A is the leftmost visible direction
                // B is the rightmost visible direction
                // M is the intersection of the position -> waypoint line with AB
                double mv = Math.tan(angleRad) * GameRenderer.PROJECTION_Z_NEAR;
                double av = Math.tan(horizontalFovRad / 2) * GameRenderer.PROJECTION_Z_NEAR;
                double ab = 2 * av;
                double am = right ? mv + av : ab - (mv + av);
                double perc = am / ab;
                x = (int) (perc * guiGraphics.guiWidth());
            }
            xPositions.add(new WaypointLabelLocation(label, x));
        });

        xPositions.sort(Comparator.comparingInt(WaypointLabelLocation::location));

        List<List<WaypointLabelLocation>> positions = new ArrayList<>();
        positions.add(xPositions);

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointLabelLocation> waypointLabelLocations = positions.get(line);
            int i = 0;
            while (i < waypointLabelLocations.size() - 1) {
                WaypointLabelLocation left = waypointLabelLocations.get(i);
                WaypointLabelLocation right = waypointLabelLocations.get(i + 1);
                int leftX = left.location();
                int rightX = right.location();
                int leftWidth = minecraft.font.width(left.label());
                int rightWidth = minecraft.font.width(right.label());
                if (leftWidth / 2 + rightWidth / 2 > rightX - leftX) {
                    if (line + 1 == positions.size()) {
                        positions.add(new ArrayList<>());
                    }
                    List<WaypointLabelLocation> nextLevel = positions.get(line + 1);
                    WaypointLabelLocation removed = waypointLabelLocations.remove(i + 1);
                    nextLevel.add(removed);
                } else {
                    i++;
                }
            }
        }

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointLabelLocation> w = positions.get(line);
            for (WaypointLabelLocation waypoint : w) {
                guiGraphics.drawCenteredString(minecraft.font, waypoint.label(), waypoint.location(), 1 + line * minecraft.font.lineHeight, 0xFFFFFF);
            }
        }
    }

    private static void renderWaypointBoxes(WorldRenderContext context) {
        String worldIdentifier = getWorldIdentifier(Minecraft.getInstance());
        Map<String, WaypointLocation> waypoints = WaypointCommand.waypoints.get(worldIdentifier);
        if (waypoints == null) {
            return;
        }

        ClientChunkCache chunkSource = context.world().getChunkSource();
        waypoints.forEach((waypointName, waypoint) -> {
            if (!waypoint.dimension().location().equals(context.world().dimension().location())) {
                return;
            }

            BlockPos waypointLocation = waypoint.location();
            if (!chunkSource.hasChunk(waypointLocation.getX() >> 4, waypointLocation.getZ() >> 4)) {
                return;
            }

            Vec3 cameraPosition = context.camera().getPosition();
            float distance = (float) waypointLocation.distToCenterSqr(cameraPosition);
            distance = (float) Math.sqrt(distance) / 6;

            PoseStack stack = context.matrixStack();
            stack.pushPose();
            stack.translate(cameraPosition.scale(-1));

            AABB box = new AABB(waypointLocation);
            ShapeRenderer.renderLineBox(stack, context.consumers().getBuffer(RenderQueue.NO_DEPTH_LAYER), box, 1, 1, 1, 1);

            stack.translate(waypointLocation.getCenter().add(new Vec3(0, 1, 0)));
            stack.mulPose(context.camera().rotation());
            stack.scale(0.025f * distance, -0.025f * distance, 0.025f * distance);

            Font font = Minecraft.getInstance().font;
            int width = font.width(waypointName) / 2;
            int backgroundColour = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
            font.drawInBatch(waypointName, -width, 0, 0xFFFFFF, false, stack.last().pose(), context.consumers(), Font.DisplayMode.SEE_THROUGH, backgroundColour, LightTexture.FULL_SKY);

            stack.popPose();
        });
    }

    @VisibleForTesting
    public record WaypointLocation(ResourceKey<Level> dimension, BlockPos location) {
    }

    record WaypointLabelLocation(Component label, int location) {
    }
}
