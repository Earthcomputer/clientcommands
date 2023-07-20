package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import net.earthcomputer.clientcommands.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.*;
import static dev.xpple.clientarguments.arguments.CDimensionArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WaypointCommand {

    public static final Map<String, Map<String, Pair<BlockPos, RegistryKey<World>>>> waypoints = new HashMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwaypoint.saveFailed"));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(name -> Text.translatable("commands.cwaypoint.alreadyExists", name));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(name -> Text.translatable("commands.cwaypoint.notFound", name));

    static {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.error("Could not load waypoints file, hence /cwaypoint will not work!", e);
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwaypoint")
            .then(literal("add")
                .then(argument("name", word())
                    .then(argument("pos", blockPos())
                        .executes(ctx -> add(ctx.getSource(), getString(ctx, "name"), getCBlockPos(ctx, "pos")))
                        .then(argument("dimension", dimension())
                            .executes(ctx -> add(ctx.getSource(), getString(ctx, "name"), getCBlockPos(ctx, "pos"), getCDimensionArgument(ctx, "dimension").getRegistryKey()))))))
            .then(literal("remove")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> {
                        Map<String, Pair<BlockPos, RegistryKey<World>>> worldWaypoints = waypoints.get(getWorldIdentifier(ctx.getSource()));
                        return CommandSource.suggestMatching(worldWaypoints != null ? worldWaypoints.keySet() : Collections.emptySet(), builder);
                    })
                    .executes(ctx -> remove(ctx.getSource(), getString(ctx, "name")))))
            .then(literal("edit")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> {
                        Map<String, Pair<BlockPos, RegistryKey<World>>> worldWaypoints = waypoints.get(getWorldIdentifier(ctx.getSource()));
                        return CommandSource.suggestMatching(worldWaypoints != null ? worldWaypoints.keySet() : Collections.emptySet(), builder);
                    })
                    .then(argument("pos", blockPos())
                        .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name"), getCBlockPos(ctx, "pos")))
                        .then(argument("dimension", dimension())
                            .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name"), getCBlockPos(ctx, "pos"), getCDimensionArgument(ctx, "dimension").getRegistryKey()))))))
            .then(literal("list")
                .executes(ctx -> list(ctx.getSource()))
                .then(argument("current", bool())
                    .executes(ctx -> list(ctx.getSource(), getBool(ctx, "current"))))));
    }

    private static String getWorldIdentifier(FabricClientCommandSource source) {
        String worldIdentifier;
        if (source.getClient().isIntegratedServerRunning()) {
            worldIdentifier = source.getClient().getServer().getSaveProperties().getLevelName();
        } else {
            worldIdentifier = source.getClient().getNetworkHandler().getConnection().getAddress().toString();
        }
        return worldIdentifier;
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos) throws CommandSyntaxException {
        return add(source, name, pos, source.getWorld().getRegistryKey());
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos, RegistryKey<World> dimension) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source);

        Map<String, Pair<BlockPos, RegistryKey<World>>> worldWaypoints = waypoints.computeIfAbsent(worldIdentifier, key -> new HashMap<>());

        if (worldWaypoints.putIfAbsent(name, new Pair<>(pos, dimension)) != null) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Text.translatable("commands.cwaypoint.add.success", name, pos.toShortString(), dimension.getValue()));
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source);

        Map<String, Pair<BlockPos, RegistryKey<World>>> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.remove(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Text.translatable("commands.cwaypoint.remove.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int edit(FabricClientCommandSource source, String name, BlockPos pos) throws CommandSyntaxException {
        return edit(source, name, pos, source.getWorld().getRegistryKey());
    }

    private static int edit(FabricClientCommandSource source, String name, BlockPos pos, RegistryKey<World> dimension) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source);

        Map<String, Pair<BlockPos, RegistryKey<World>>> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.computeIfPresent(name, (key, value) -> new Pair<>(pos, dimension)) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Text.translatable("commands.cwaypoint.edit.success", name, pos.toShortString(), dimension.getValue()));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        return list(source, false);
    }

    private static int list(FabricClientCommandSource source, boolean current) {
        if (current) {
            String worldIdentifier = getWorldIdentifier(source);

            Map<String, Pair<BlockPos, RegistryKey<World>>> worldWaypoints = waypoints.get(worldIdentifier);

            if (worldWaypoints.isEmpty()) {
                source.sendFeedback(Text.translatable("commands.cwaypoint.list.empty"));
                return Command.SINGLE_SUCCESS;
            }

            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Text.translatable("commands.cwaypoint.list", name, waypoint.getLeft().toShortString(), waypoint.getRight().getValue())));
            return Command.SINGLE_SUCCESS;
        }

        if (waypoints.isEmpty()) {
            source.sendFeedback(Text.translatable("commands.cwaypoint.list.empty"));
            return Command.SINGLE_SUCCESS;
        }

        waypoints.forEach((worldIdentifier, worldWaypoints) -> {
            if (worldWaypoints.isEmpty()) {
                return;
            }

            source.sendFeedback(Text.literal(worldIdentifier).append(":"));
            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Text.translatable("commands.cwaypoint.list", name, waypoint.getLeft().toShortString(), waypoint.getRight().getValue())));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            NbtCompound rootTag = new NbtCompound();
            waypoints.forEach((worldIdentifier, worldWaypoints) -> rootTag.put(worldIdentifier, worldWaypoints.entrySet().stream()
                .collect(NbtCompound::new, (result, entry) -> {
                    NbtCompound waypoint = new NbtCompound();
                    NbtCompound pos = NbtHelper.fromBlockPos(entry.getValue().getLeft());
                    String dimension = entry.getValue().getRight().getValue().toString();
                    waypoint.put("Pos", pos);
                    waypoint.putString("Dimension", dimension);
                    result.put(entry.getKey(), waypoint);
                }, NbtCompound::copyFrom)));
            File newFile = File.createTempFile("waypoints", ".dat", ClientCommands.configDir.toFile());
            NbtIo.write(rootTag, newFile);
            File backupFile = new File(ClientCommands.configDir.toFile(), "waypoints.dat_old");
            File currentFile = new File(ClientCommands.configDir.toFile(), "waypoints.dat");
            Util.backupAndReplace(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        waypoints.clear();
        NbtCompound rootTag = NbtIo.read(new File(ClientCommands.configDir.toFile(), "waypoints.dat"));
        if (rootTag == null) {
            return;
        }
        rootTag.getKeys().forEach(worldIdentifier -> {
            NbtCompound worldWaypoints = rootTag.getCompound(worldIdentifier);
            waypoints.put(worldIdentifier, worldWaypoints.getKeys().stream()
                .collect(Collectors.toMap(Function.identity(), name -> {
                    NbtCompound waypoint = worldWaypoints.getCompound(name);
                    BlockPos pos = NbtHelper.toBlockPos(waypoint.getCompound("Pos"));
                    RegistryKey<World> dimension = World.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, waypoint.get("Dimension"))).resultOrPartial(LOGGER::error).orElseThrow();
                    return new Pair<>(pos, dimension);
                })));
        });
    }
}
