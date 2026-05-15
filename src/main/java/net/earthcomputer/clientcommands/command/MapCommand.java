package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Vector2i;
import org.joml.Vector4i;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class MapCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType NO_HELD_MAP_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cmap.noHeldMap"));
    private static final SimpleCommandExceptionType FAILED_SAVE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cmap.failedSave"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cmap")
            .then(literal("export")
                .executes(ctx -> exportMap(ctx.getSource(), 1))
                .then(argument("scale", integer(1, 64))
                    .executes(ctx -> exportMap(ctx.getSource(), getInteger(ctx, "scale"))))));
    }

    private static int exportMap(FabricClientCommandSource source, int scale) throws CommandSyntaxException {
        MapItemSavedData data = fromHand(source.getPlayer());
        if (data != null) {
            return exportMap(source, new MapInfo[][] { { new MapInfo(data, 0) } }, scale, false);
        }

        MapInfo[][] worldData = fromWorld(source.getClient().crosshairPickEntity, source.getClient().level, source.getPlayer().getDirection());
        if (worldData != null) {
            return exportMap(source, worldData, scale, true);
        }

        throw NO_HELD_MAP_EXCEPTION.create();
    }

    private static MapItemSavedData fromHand(LivingEntity entity) {
        ItemStack item = entity.getMainHandItem();
        if (item.has(DataComponents.MAP_ID)) {
            return MapItem.getSavedData(item, entity.level());
        }

        item = entity.getOffhandItem();
        if (item.has(DataComponents.MAP_ID)) {
            return MapItem.getSavedData(item, entity.level());
        }

        return null;
    }

    private static MapInfo[][] fromWorld(Entity entity, ClientLevel level, Direction facing) {
        if (!(entity instanceof ItemFrame frame)) {
            return null;
        }

        ItemStack item = frame.getItem();
        Direction direction = frame.getDirection();
        if (!item.has(DataComponents.MAP_ID)) {
            return null;
        }

        // determine axis directions
        Direction xAxis = switch (direction) {
            case NORTH, SOUTH, EAST, WEST -> Direction.DOWN;
            case UP -> facing.getOpposite();
            case DOWN -> facing;
        };

        Direction yAxis = switch (direction) {
            case NORTH -> Direction.WEST;
            case EAST -> Direction.NORTH;
            case SOUTH -> Direction.EAST;
            case WEST -> Direction.SOUTH;
            case UP -> Direction.from2DDataValue((xAxis.get2DDataValue() + 3) % 4);
            case DOWN -> Direction.from2DDataValue((xAxis.get2DDataValue() + 1) % 4);
        };

        // collect item frames
        Map<BlockPos, ItemFrame> frames = StreamSupport.stream(level.entitiesForRendering().spliterator(), true)
            .filter(e -> e instanceof ItemFrame itemFrame && itemFrame.getDirection() == direction)
            .map(ItemFrame.class::cast)
            .collect(Collectors.toMap(ItemFrame::getPos, Function.identity(), (a, b) -> {
                boolean aHas = a.getItem().has(DataComponents.MAP_ID);
                boolean bHas = b.getItem().has(DataComponents.MAP_ID);
                if (aHas && bHas) {
                    LOGGER.warn("More than one map item frame found at {}.", a.getPos());
                }
                if (aHas) {
                    return a;
                }
                return b;
            }));

        // dfs
        BlockPos initialPos = frame.getPos();

        Map<Vector2i, MapInfo> positionsAndData = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> toVisit = new ArrayDeque<>();
        toVisit.addLast(frame.getPos());

        while (!toVisit.isEmpty()) {
            BlockPos pos = toVisit.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            ItemFrame frameAtPos = frames.get(pos);
            if (frameAtPos == null) {
                continue;
            }

            MapItemSavedData savedData = MapItem.getSavedData(frameAtPos.getItem(), level);

            // compute position
            BlockPos relPos = pos.subtract(initialPos);
            Vector2i position = new Vector2i(
                relPos.get(xAxis.getAxis()) * xAxis.getAxisDirection().getStep(),
                relPos.get(yAxis.getAxis()) * yAxis.getAxisDirection().getStep());

            // offset rotation to match with map orientation
            int rotationOffset = switch (xAxis) {
                case WEST -> 3;
                case EAST -> 1;
                case SOUTH -> direction == Direction.UP ? 0 : 2;
                case NORTH, UP -> direction == Direction.UP ? 2 : 0;
                case DOWN -> 0;
            };

            if (savedData != null) {
                positionsAndData.put(position, new MapInfo(savedData, frameAtPos.getRotation() + rotationOffset));
            }

            // add adjacent to search
            List<BlockPos> adjacent = switch (direction.getAxis()) {
                case X -> List.of(pos.north(), pos.south(), pos.above(), pos.below());
                case Y -> List.of(pos.north(), pos.south(), pos.east(), pos.west());
                case Z -> List.of(pos.east(), pos.west(), pos.above(), pos.below());
            };

            toVisit.addAll(adjacent);
        }

        // determine bounds
        // x = minX, y = minY, z = maxX, w = maxY
        Vector4i bounds = positionsAndData.keySet().stream().reduce(new Vector4i(0),
            (bound, value) -> new Vector4i(
                Math.min(bound.x, value.x),
                Math.min(bound.y, value.y),
                Math.max(bound.z, value.x),
                Math.max(bound.w, value.y)),
            (a, b) -> new Vector4i(
                Math.min(a.x, b.x),
                Math.min(a.y, b.y),
                Math.max(a.z, b.z),
                Math.max(a.w, b.w)));

        int width = Math.abs(bounds.z - bounds.x) + 1;
        int height = Math.abs(bounds.w - bounds.y) + 1;

        // create 2d array
        MapInfo[][] data = new MapInfo[width][height];
        for (Map.Entry<Vector2i, MapInfo> entry : positionsAndData.entrySet()) {
            data[entry.getKey().x - bounds.x][entry.getKey().y - bounds.y] = entry.getValue();
        }

        return data;
    }

    // current impl will break horribly if MapItem.IMAGE_WIDTH != MapItem.IMAGE_HEIGHT
    private static int exportMap(FabricClientCommandSource source, MapInfo[][] data, int scale, boolean world) throws CommandSyntaxException {
        int height = data.length * MapItem.IMAGE_HEIGHT;
        int width = Arrays.stream(data).mapToInt(a -> a.length).max().orElseThrow() * MapItem.IMAGE_WIDTH;

        // create image
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, true);
        for (int y = 0; y < data.length; y++) {
            for (int x = 0; x < data[y].length; x++) {
                MapInfo savedData = data[y][x];
                if (savedData == null) {
                    continue;
                }

                drawMapWithOffsets(image, x * MapItem.IMAGE_WIDTH * scale, y * MapItem.IMAGE_HEIGHT * scale, scale, savedData);
            }
        }

        // save to screenshot dir as if it was a screenshot
        File screenshotDir = new File(source.getClient().gameDirectory, Screenshot.SCREENSHOT_DIR);
        if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
            throw FAILED_SAVE_EXCEPTION.create();
        }

        File imageFile = Screenshot.getFile(screenshotDir);
        try {
            image.writeToFile(imageFile);
        } catch (IOException e) {
            throw FAILED_SAVE_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable(world ? "commands.cmap.export.success.world" : "commands.cmap.export.success.hand", Component.literal(imageFile.getName())
            .withStyle(ChatFormatting.UNDERLINE)
            .withStyle(s -> s.withClickEvent(new ClickEvent.OpenFile(imageFile.getAbsoluteFile())))));

        return Command.SINGLE_SUCCESS;
    }

    private static void drawMapWithOffsets(NativeImage image, int offsetX, int offsetY, int scale, MapInfo info) {
        for (int y = 0; y < info.getHeight(); y++) {
            for (int x = 0; x < info.getWidth(); x++) {
                int color = MapColor.getColorFromPackedId(info.getMapColor(x, y));

                for (int i = 0; i < scale; i++) {
                    for (int j = 0; j < scale; j++) {
                        image.setPixel((x * scale + i) + offsetX, (y * scale + j) + offsetY, color);
                    }
                }
            }
        }
    }

    private record MapInfo(MapItemSavedData data, int rotation) {

        @SuppressWarnings({"UnnecessaryLocalVariable", "SuspiciousNameCombination"})
        public byte getMapColor(int x, int y) {
            switch (rotation % 4) {
                case 0 -> {
                    return data.colors[x + y * MapItem.IMAGE_WIDTH];
                }
                // 90 clockwise
                case 1 -> {
                    int newX = y;
                    int newY = MapItem.IMAGE_WIDTH - 1 - x;
                    return data.colors[newX + newY * MapItem.IMAGE_WIDTH];
                }
                // 180
                case 2 -> {
                    int newX = MapItem.IMAGE_WIDTH - 1 - x;
                    int newY = MapItem.IMAGE_WIDTH - 1 - y;
                    return data.colors[newX + newY * MapItem.IMAGE_WIDTH];
                }
                // 270
                case 3 -> {
                    int newX = MapItem.IMAGE_WIDTH - 1 - y;
                    int newY = x;
                    return data.colors[newX + newY * MapItem.IMAGE_WIDTH];
                }
                default -> throw new IllegalStateException("unreachable");
            }
        }

        @SuppressWarnings("SuspiciousNameCombination")
        public int getWidth() {
            if (rotation % 2 == 0) {
                return MapItem.IMAGE_WIDTH;
            } else {
                return MapItem.IMAGE_HEIGHT;
            }
        }

        @SuppressWarnings("SuspiciousNameCombination")
        public int getHeight() {
            if (rotation % 2 == 0) {
                return MapItem.IMAGE_HEIGHT;
            } else {
                return MapItem.IMAGE_WIDTH;
            }
        }
    }
}
