package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Streams;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ListArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.SimpleBlockPredicateArgumentType.*;
import static net.minecraft.command.argument.BlockPosArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class AreaStatsCommand {

    private static final SimpleCommandExceptionType NOT_LOADED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.careastats.notLoaded"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("careastats");

        LiteralCommandNode<ServerCommandSource> careastats = dispatcher.register(literal("careastats"));
        dispatcher.register(literal("careastats")
                .then(argument("pos1", blockPos())
                        .then(argument("pos2", blockPos())
                                .then(argument("predicates", list(blockPredicate(), 1))
                                        .executes(ctx -> areaStats(ctx.getSource(), getBlockPos(ctx, "pos1"), getBlockPos(ctx, "pos2"), getList(ctx, "predicates"))))
                                .executes(ctx -> areaStats(ctx.getSource(), getBlockPos(ctx, "pos1"), getBlockPos(ctx, "pos2"), block -> !block.getDefaultState().isAir())))));
    }

    private static int areaStats(ServerCommandSource source, BlockPos pos1, BlockPos pos2, Predicate<Block> blockPredicate) throws CommandSyntaxException {
        return areaStats(source, pos1, pos2, Collections.singletonList(blockPredicate));
    }

    private static int areaStats(ServerCommandSource source, BlockPos pos1, BlockPos pos2, List<Predicate<Block>> blockPredicates) throws CommandSyntaxException {
        final ClientWorld world = MinecraftClient.getInstance().world;
        final ChunkManager chunkManager = world.getChunkManager();
        if (!chunkManager.isChunkLoaded(pos1.getX() >> 4, pos1.getZ() >> 4) || !chunkManager.isChunkLoaded(pos2.getX() >> 4, pos2.getZ() >> 4)) {
            throw NOT_LOADED_EXCEPTION.create();
        }

        final Predicate<Block> blockPredicate = or(blockPredicates);

        final long startTime = System.nanoTime();

        final WorldChunk chunk1 = world.getWorldChunk(pos1);
        final WorldChunk chunk2 = world.getWorldChunk(pos2);

        final int minX, maxX, minZ, maxZ, minY, maxY;
        if (pos1.getX() <= pos2.getX()) {
            minX = pos1.getX();
            maxX = pos2.getX();
        } else {
            minX = pos2.getX();
            maxX = pos1.getX();
        }
        if (pos1.getZ() <= pos2.getZ()) {
            minZ = pos1.getZ();
            maxZ = pos2.getZ();
        } else {
            minZ = pos2.getZ();
            maxZ = pos1.getZ();
        }
        if (pos1.getY() <= pos2.getY()) {
            minY = pos1.getY();
            maxY = pos2.getY();
        } else {
            minY = pos2.getY();
            maxY = pos1.getY();
        }

        final BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        int blocks = 0;
        int chunks;

        if (chunk1.getPos().equals(chunk2.getPos())) {
            chunks = 1;
            blocks += loop(minX, maxX, minZ, maxZ, minY, maxY, blockPredicate, chunk1, mutablePos);
        } else if (chunk1.getPos().x == chunk2.getPos().x) {
            chunks = 2;
            final WorldChunk minMinChunk = world.getChunk(minX >> 4, minZ >> 4);
            blocks += loop(minX, maxX, minZ, (minZ >> 4) * 16 + 15, minY, maxY, blockPredicate, minMinChunk, mutablePos);

            final WorldChunk minMaxChunk = world.getChunk(minX >> 4, maxZ >> 4);
            blocks += loop(minX, maxX, (maxZ >> 4) * 16, maxZ, minY, maxY, blockPredicate, minMaxChunk, mutablePos);

            for (int chunkZ = minMinChunk.getPos().z + 1; chunkZ < minMaxChunk.getPos().z; chunkZ++) {
                chunks++;
                final WorldChunk chunk = world.getChunk(minMinChunk.getPos().x, chunkZ);
                blocks += loop(minX, maxX, 16 * chunkZ, 16 * chunkZ + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
        } else if (chunk1.getPos().z == chunk2.getPos().z) {
            chunks = 2;
            final WorldChunk minMinChunk = world.getChunk(minX >> 4, minZ >> 4);
            blocks += loop(minX, (minX >> 4) * 16 + 15, minZ, maxZ, minY, maxY, blockPredicate, minMinChunk, mutablePos);

            final WorldChunk maxMinChunk = world.getChunk(maxX >> 4, minZ >> 4);
            blocks += loop((maxX >> 4) * 16, maxX, minZ, maxZ, minY, maxY, blockPredicate, maxMinChunk, mutablePos);

            for (int chunkX = minMinChunk.getPos().x + 1; chunkX < maxMinChunk.getPos().x; chunkX++) {
                chunks++;
                final WorldChunk chunk = world.getChunk(chunkX, minMinChunk.getPos().z);
                blocks += loop(16 * chunkX, 16 * chunkX + 15, minZ, maxZ, minY, maxY, blockPredicate, chunk, mutablePos);
            }
        } else {
            chunks = 4;
            final WorldChunk minMinChunk, minMaxChunk, maxMinChunk, maxMaxChunk;
            minMinChunk = world.getChunk(minX >> 4, minZ >> 4);
            minMaxChunk = world.getChunk(minX >> 4, maxZ >> 4);
            maxMinChunk = world.getChunk(maxX >> 4, minZ >> 4);
            maxMaxChunk = world.getChunk(maxX >> 4, maxZ >> 4);

            blocks += loop(minX, (minX >> 4) * 16 + 15, minZ, (minZ >> 4) * 16 + 15, minY, maxY, blockPredicate, minMinChunk, mutablePos);

            blocks += loop(minX, (minX >> 4) * 16 + 15, (maxZ >> 4) * 16, maxZ, minY, maxY, blockPredicate, minMaxChunk, mutablePos);

            blocks += loop((maxX >> 4) * 16, maxX, minZ, (minZ >> 4) * 16 + 15, minY, maxY, blockPredicate, maxMinChunk, mutablePos);

            blocks += loop((maxX >> 4) * 16, maxX, (maxZ >> 4) * 16, maxZ, minY, maxY, blockPredicate, maxMaxChunk, mutablePos);

            for (int minMinMaxMin = minMinChunk.getPos().x + 1; minMinMaxMin < maxMinChunk.getPos().x; minMinMaxMin++) {
                chunks++;
                final WorldChunk chunk = world.getChunk(minMinMaxMin, minMinChunk.getPos().z);
                blocks += loop(16 * minMinMaxMin, 16 * minMinMaxMin + 15, minZ, (minZ >> 4) * 16 + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int minMinMinMax = minMinChunk.getPos().z + 1; minMinMinMax < minMaxChunk.getPos().z; minMinMinMax++) {
                chunks++;
                final WorldChunk chunk = world.getChunk(minMinChunk.getPos().x, minMinMinMax);
                blocks += loop(minX, (minX >> 4) * 16 + 15, 16 * minMinMinMax, 16 * minMinMinMax + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int minMaxMaxMax = minMaxChunk.getPos().x + 1; minMaxMaxMax < maxMaxChunk.getPos().x; minMaxMaxMax++) {
                chunks++;
                final WorldChunk chunk = world.getChunk(minMaxMaxMax, minMaxChunk.getPos().z);
                blocks += loop(16 * minMaxMaxMax, 16 * minMaxMaxMax + 15, (maxZ >> 4) * 16, maxZ, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int maxMinMaxMax = maxMinChunk.getPos().z + 1; maxMinMaxMax < maxMaxChunk.getPos().z; maxMinMaxMax++) {
                chunks++;
                final WorldChunk chunk = world.getChunk(maxMinChunk.getPos().x, maxMinMaxMax);
                blocks += loop((maxX >> 4) * 16, maxX, 16 * maxMinMaxMax, 16 * maxMinMaxMax + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int chunkX = minMinChunk.getPos().x + 1; chunkX < maxMinChunk.getPos().x; chunkX++) {
                for (int chunkZ = minMinChunk.getPos().z + 1; chunkZ < minMaxChunk.getPos().z; chunkZ++) {
                    chunks++;
                    final WorldChunk chunk = world.getChunk(chunkX, chunkZ);
                    blocks += loop(16 * chunkX, 16 * chunkX + 15, 16 * chunkZ, 16 * chunkZ + 15, minY, maxY, blockPredicate, chunk, mutablePos);
                }
            }
        }

        final long entities = Streams.stream(world.getEntities())
                .filter(entity ->
                        entity.getX() >= minX && entity.getX() <= maxX &&
                        entity.getZ() >= minZ && entity.getZ() <= maxZ &&
                        entity.getY() >= minY && entity.getY() <= maxY)
                .count();

        Box box = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, box, box, 0xFFFF0000, 60 * 20);

        long endTime = System.nanoTime();

        sendFeedback("commands.careastats.output.chunksScanned", chunks, endTime - startTime, (endTime - startTime) / 1000000);
        sendFeedback("commands.careastats.output.blocksMatched", blocks, (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
        sendFeedback("commands.careastats.output.entitiesFound", entities);

        return blocks;
    }

    private static int loop(int start1, int end1, int start2, int end2, int start3, int end3, Predicate<Block> predicate, WorldChunk chunk, BlockPos.Mutable mutablePos) {
        int counter = 0;
        for (int x = start1; x <= end1; x++) {
            for (int z = start2; z <= end2; z++) {
                for (int y = start3; y <= end3; y++) {
                    if (predicate.test(chunk.getBlockState(mutablePos.set(x, y, z)).getBlock())) {
                        counter++;
                    }
                }
            }
        }
        return counter;
    }

    private static Predicate<Block> or(List<Predicate<Block>> predicates) {
        return predicates.stream().reduce(t -> false, Predicate::or);
    }
}
