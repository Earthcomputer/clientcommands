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
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CDimensionArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WaypointCommand {

    public static final Map<String, Map<String, Pair<BlockPos, ResourceKey<Level>>>> waypoints = new HashMap<>();

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
                        Map<String, Pair<BlockPos, ResourceKey<Level>>> worldWaypoints = waypoints.get(getWorldIdentifier(ctx.getSource()));
                        return SharedSuggestionProvider.suggest(worldWaypoints != null ? worldWaypoints.keySet() : Collections.emptySet(), builder);
                    })
                    .executes(ctx -> remove(ctx.getSource(), getString(ctx, "name")))))
            .then(literal("edit")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> {
                        Map<String, Pair<BlockPos, ResourceKey<Level>>> worldWaypoints = waypoints.get(getWorldIdentifier(ctx.getSource()));
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

    private static String getWorldIdentifier(FabricClientCommandSource source) {
        String worldIdentifier;
        if (source.getClient().hasSingleplayerServer()) {
            // the level id remains the same even after the level is renamed
            worldIdentifier = source.getClient().getSingleplayerServer().storageSource.getLevelId();
        } else {
            worldIdentifier = source.getClient().getConnection().getConnection().getRemoteAddress().toString();
        }
        return worldIdentifier;
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos) throws CommandSyntaxException {
        return add(source, name, pos, source.getWorld().dimension());
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos, ResourceKey<Level> dimension) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source);

        Map<String, Pair<BlockPos, ResourceKey<Level>>> worldWaypoints = waypoints.computeIfAbsent(worldIdentifier, key -> new HashMap<>());

        if (worldWaypoints.putIfAbsent(name, Pair.of(pos, dimension)) != null) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Component.translatable("commands.cwaypoint.add.success", name, pos.toShortString(), dimension.location()));
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        String worldIdentifier = getWorldIdentifier(source);

        Map<String, Pair<BlockPos, ResourceKey<Level>>> worldWaypoints = waypoints.get(worldIdentifier);

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
        String worldIdentifier = getWorldIdentifier(source);

        Map<String, Pair<BlockPos, ResourceKey<Level>>> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.computeIfPresent(name, (key, value) -> Pair.of(pos, dimension)) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        saveFile();
        source.sendFeedback(Component.translatable("commands.cwaypoint.edit.success", name, pos.toShortString(), dimension.location()));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        return list(source, false);
    }

    private static int list(FabricClientCommandSource source, boolean current) {
        if (current) {
            String worldIdentifier = getWorldIdentifier(source);

            Map<String, Pair<BlockPos, ResourceKey<Level>>> worldWaypoints = waypoints.get(worldIdentifier);

            if (worldWaypoints.isEmpty()) {
                source.sendFeedback(Component.translatable("commands.cwaypoint.list.empty"));
                return Command.SINGLE_SUCCESS;
            }

            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Component.translatable("commands.cwaypoint.list", name, waypoint.getLeft().toShortString(), waypoint.getRight().location())));
            return Command.SINGLE_SUCCESS;
        }

        if (waypoints.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cwaypoint.list.empty"));
            return Command.SINGLE_SUCCESS;
        }

        waypoints.forEach((worldIdentifier, worldWaypoints) -> {
            if (worldWaypoints.isEmpty()) {
                return;
            }

            source.sendFeedback(Component.literal(worldIdentifier).append(":"));
            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Component.translatable("commands.cwaypoint.list", name, waypoint.getLeft().toShortString(), waypoint.getRight().location())));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            CompoundTag compoundTag = new CompoundTag();
            waypoints.forEach((worldIdentifier, worldWaypoints) -> compoundTag.put(worldIdentifier, worldWaypoints.entrySet().stream()
                .collect(CompoundTag::new, (result, entry) -> {
                    CompoundTag waypoint = new CompoundTag();
                    Tag pos = NbtUtils.writeBlockPos(entry.getValue().getLeft());
                    waypoint.put("pos", pos);
                    String dimension = entry.getValue().getRight().location().toString();
                    waypoint.putString("Dimension", dimension);
                    result.put(entry.getKey(), waypoint);
                }, CompoundTag::merge)));
            rootTag.put("Waypoints", compoundTag);
            Path newFile = Files.createTempFile(ClientCommands.configDir, "waypoints", ".dat");
            NbtIo.write(rootTag, newFile);
            Path backupFile = ClientCommands.configDir.resolve("waypoints.dat_old");
            Path currentFile = ClientCommands.configDir.resolve("waypoints.dat");
            Util.safeReplaceFile(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        waypoints.clear();
        CompoundTag rootTag = NbtIo.read(ClientCommands.configDir.resolve("waypoints.dat"));
        if (rootTag == null) {
            return;
        }
        // TODO: update-sensitive: apply custom data fixes when it becomes necessary
        CompoundTag compoundTag = rootTag.getCompound("Waypoints");
        compoundTag.getAllKeys().forEach(worldIdentifier -> {
            CompoundTag worldWaypoints = compoundTag.getCompound(worldIdentifier);
            waypoints.put(worldIdentifier, worldWaypoints.getAllKeys().stream()
                .collect(Collectors.toMap(Function.identity(), name -> {
                    CompoundTag waypoint = worldWaypoints.getCompound(name);
                    BlockPos pos = NbtUtils.readBlockPos(waypoint, "pos").orElseThrow();
                    ResourceKey<Level> dimension = Level.RESOURCE_KEY_CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, waypoint.get("Dimension"))).resultOrPartial(LOGGER::error).orElseThrow();
                    return Pair.of(pos, dimension);
                })));
        });
    }
}
