package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class MapCommand {
    private static final SimpleCommandExceptionType NO_HELD_MAP_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cmap.noHeld"));


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cmap")
            .then(literal("export")
                .executes(ctx -> exportMap(ctx.getSource(), 1))
                .then(argument("upscale", integer(1, 16))
                    .executes(ctx -> exportMap(ctx.getSource(), getInteger(ctx, "upscale")))
                )
            )
        );
    }

    private static MapInfo[][] getMaps(ClientPlayerEntity player, MinecraftClient client) throws CommandSyntaxException {
        MapInfo hand = fromHand(player);
        if (hand != null) {
            return new MapInfo[][]{{hand}};
        }
        MapInfo[][] mapInfo = fromWorld(player, client.targetedEntity);
        if (mapInfo == null) {
            throw NO_HELD_MAP_EXCEPTION.create();
        }
        return mapInfo;
    }


    private static int exportMap(FabricClientCommandSource source, int upscale) throws CommandSyntaxException {
        MapInfo[][] mapInfo = getMaps(source.getPlayer(), source.getClient());
        // calculate width and height
        int width = mapInfo[0].length * FilledMapItem.field_30907 * upscale;
        int height = mapInfo.length * FilledMapItem.field_30908 * upscale;

        try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, true)) {
            for (int i = 0; i < mapInfo.length; ++i) {
                for (int j = 0; j < mapInfo[i].length; ++j) {
                    MapInfo info = mapInfo[i][j];
                    if (info == null) {
                        continue;
                    }
                    drawMapOffset(image, info, j * FilledMapItem.field_30907 * upscale, i * FilledMapItem.field_30908 * upscale, upscale);
                }
            }
            File screenshotDir = new File(source.getClient().runDirectory, "screenshots");
            screenshotDir.mkdirs();
            File file = ScreenshotRecorder.getScreenshotFilename(screenshotDir);

            image.writeTo(file);

            source.sendFeedback(Text.translatable("commands.cmap.success", file.getName()).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return Command.SINGLE_SUCCESS;
    }

    private static void drawMapOffset(NativeImage image, MapInfo map, int xOff, int yOff, int upscale) {
        for (int i = 0; i < FilledMapItem.field_30907; ++i) {
            for (int j = 0; j < FilledMapItem.field_30908; ++j) {
                int color = MapColor.getRenderColor(map.getColor(i, j));
                for (int k = 0; k < upscale; k++) {
                    for (int l = 0; l < upscale; l++) {
                        image.setColor(i * upscale + k + xOff, j * upscale + l + yOff, color);
                    }
                }
            }
        }
    }

    private static MapInfo fromHand(ClientPlayerEntity player) throws CommandSyntaxException {
        ItemStack map;

        // detect if map in hand
        if (player.getMainHandStack().getItem() instanceof FilledMapItem) {
            map = player.getMainHandStack();
        } else if (player.getOffHandStack().getItem() instanceof FilledMapItem) {
            map = player.getOffHandStack();
        } else {
            return null;
        }

        return new MapInfo(fromItemStack(player.getWorld(), map), 0);
    }

    private static MapInfo[][] fromWorld(ClientPlayerEntity player, Entity target) {
        if (target instanceof ItemFrameEntity frame) {
            ItemStack map = frame.getHeldItemStack();
            if (map == null || !(map.getItem() instanceof FilledMapItem)) {
                return null;
            }
            Direction facing = frame.getHorizontalFacing();
            // find in world
            BlockPos pos = frame.getBlockPos();
            FramePos current = new FramePos(pos, facing);
            // get all loaded item frames
            Map<FramePos, ItemFrameEntity> entities = StreamSupport.stream(player.clientWorld.getEntities().spliterator(), true).filter(e -> e instanceof ItemFrameEntity && ((ItemFrameEntity) e).getMapId().isPresent()).map(e -> (ItemFrameEntity) e).collect(Collectors.toMap(e -> new FramePos(e.getBlockPos(), e.getHorizontalFacing()), Function.identity()));
            Set<ItemFrameEntity> connected = new HashSet<>();
            getConnectedItemFrames(connected, entities, current);
            // put them in proper traversal order
            Direction yAxis = switch (facing) {
                case NORTH, SOUTH, EAST, WEST -> Direction.DOWN;
                case UP -> player.getHorizontalFacing().getOpposite();
                case DOWN -> player.getHorizontalFacing();
            };
            Direction xAxis = switch (facing) {
                case NORTH -> Direction.WEST;
                case EAST -> Direction.NORTH;
                case SOUTH -> Direction.EAST;
                case WEST -> Direction.SOUTH;
                case UP -> Direction.fromHorizontal((yAxis.getHorizontal() + 3) % 4);
                case DOWN -> Direction.fromHorizontal((yAxis.getHorizontal() + 1) % 4);
            };
            Map<Vec2i, ItemFrameEntity> flattened = new HashMap<>();
            Iterator<ItemFrameEntity> iterator = connected.iterator();
            ItemFrameEntity origin = iterator.next();
            flattened.put(new Vec2i(0, 0), origin);
            BlockPos zero = origin.getBlockPos();
            while (iterator.hasNext()) {
                ItemFrameEntity next = iterator.next();
                // calculate offset in 2d from 0 0
                BlockPos nextPos = next.getBlockPos();
                Vec3i vec = nextPos.subtract(zero);
                int y = vec.getComponentAlongAxis(yAxis.getAxis()) * yAxis.getDirection().offset();
                int x = vec.getComponentAlongAxis(xAxis.getAxis()) * xAxis.getDirection().offset();
                // this should never be possible
                if (flattened.put(new Vec2i(x, y), next) != null) throw new IllegalStateException("Duplicate item frame at " + nextPos);
            }
            // find min and max
            Iterator<Vec2i> vec2iIterator = flattened.keySet().iterator();
            Vec2i first = vec2iIterator.next();
            int minX = first.x();
            int maxX = first.x();
            int minY = first.z();
            int maxY = first.z();
            while (vec2iIterator.hasNext()) {
                Vec2i next = vec2iIterator.next();
                minX = Math.min(minX, next.x());
                maxX = Math.max(maxX, next.x());
                minY = Math.min(minY, next.z());
                maxY = Math.max(maxY, next.z());
            }
            // create array
            MapInfo[][] mapInfo = new MapInfo[maxY - minY + 1][maxX - minX + 1];
            for (Map.Entry<Vec2i, ItemFrameEntity> entry : flattened.entrySet()) {
                Vec2i pos2 = entry.getKey();
                ItemFrameEntity entity = entry.getValue();
                int rotationOffset = 0;
                if (yAxis != Direction.DOWN) {
                    rotationOffset = switch (yAxis) {
                        case WEST -> 3;
                        case EAST -> 1;
                        case SOUTH -> facing == Direction.UP ? 0 : 2;
                        default -> facing == Direction.UP ? 2 : 0;
                    };
                }
                mapInfo[pos2.z() - minY][pos2.x() - minX] = new MapInfo(fromItemFrame(entry.getValue()), entity.getRotation() + rotationOffset);
            }
            return mapInfo;
        }
        return null;
    }

    private static void getConnectedItemFrames(Set<ItemFrameEntity> already, Map<FramePos, ItemFrameEntity> frames, FramePos current) {
        if (frames.containsKey(current)) {
            ItemFrameEntity frame = frames.get(current);
            if (!already.contains(frame)) {
                already.add(frame);
                BlockPos[] adjacent = switch (current.facing().getAxis()) {
                    case X -> new BlockPos[]{current.pos().north(), current.pos().south(), current.pos().up(), current.pos().down()};
                    case Y -> new BlockPos[]{current.pos().north(), current.pos().south(), current.pos().east(), current.pos().west()};
                    case Z -> new BlockPos[]{current.pos().east(), current.pos().west(), current.pos().up(), current.pos().down()};
                };
                for (BlockPos pos : adjacent) {
                    getConnectedItemFrames(already, frames, new FramePos(pos, current.facing()));
                }
            }
        }
    }

    private static MapState fromItemStack(World world, ItemStack map) throws CommandSyntaxException {
        Integer mapId = FilledMapItem.getMapId(map);
        MapState mapState = FilledMapItem.getMapState(mapId, world);
        if (mapState == null) {
            throw NO_HELD_MAP_EXCEPTION.create();
        }

        return mapState;
    }

    private static MapState fromItemFrame(ItemFrameEntity entity) {
        Integer mapId = entity.getMapId().orElseThrow();
        return FilledMapItem.getMapState(mapId, entity.getWorld());
    }

    private record MapInfo(MapState state, int rotation) {

        public int getColor(int x, int y) {
            // rotate x/y
            switch (rotation % 4) {
                case 0 -> {
                    return state.colors[x + y * FilledMapItem.field_30907];
                }
                case 1 -> {
                    // 90 clockwise
                    int newX = y;
                    int newY = FilledMapItem.field_30907 - x - 1;
                    return state.colors[newX + newY * FilledMapItem.field_30907];
                }
                case 2 -> {
                    // 180 clockwise
                    int newX = FilledMapItem.field_30907 - x - 1;
                    int newY = FilledMapItem.field_30908 - y - 1;
                    return state.colors[newX + newY * FilledMapItem.field_30907];
                }
                case 3 -> {
                    // 270 clockwise
                    int newX = FilledMapItem.field_30908 - y - 1;
                    int newY = x;
                    return state.colors[newX + newY * FilledMapItem.field_30907];
                }
                // this should never be possible
                default -> throw new IllegalStateException("Unexpected value: " + rotation);
            }
        }

        public int width() {
            if (rotation % 2 == 0) {
                return FilledMapItem.field_30907;
            } else {
                return FilledMapItem.field_30908;
            }
        }

        public int height() {
            if (rotation % 2 == 0) {
                return FilledMapItem.field_30908;
            } else {
                return FilledMapItem.field_30907;
            }
        }
    }

    private record FramePos(BlockPos pos, Direction facing) {

    }

    private record Vec2i(int x, int z) {

    }

}
