package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class FindBlockCommand {

    private static final int MAX_RADIUS = 16 * 8;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cfindblock");

        dispatcher.register(literal("cfindblock")
            .then(argument("block", blockPredicate())
                .executes(ctx -> findBlock(ctx.getSource(), getBlockPredicate(ctx, "block"), MAX_RADIUS, RadiusType.CARTESIAN))
                .then(argument("radius", integer(0, MAX_RADIUS))
                    .executes(ctx -> findBlock(ctx.getSource(), getBlockPredicate(ctx, "block"), getInteger(ctx, "radius"), RadiusType.CARTESIAN))
                    .then(literal("cartesian")
                        .executes(ctx -> findBlock(ctx.getSource(), getBlockPredicate(ctx, "block"), getInteger(ctx, "radius"), RadiusType.CARTESIAN)))
                    .then(literal("rectangular")
                        .executes(ctx -> findBlock(ctx.getSource(), getBlockPredicate(ctx, "block"), getInteger(ctx, "radius"), RadiusType.RECTANGULAR)))
                    .then(literal("taxicab")
                        .executes(ctx -> findBlock(ctx.getSource(), getBlockPredicate(ctx, "block"), getInteger(ctx, "radius"), RadiusType.TAXICAB))))));
    }

    private static int findBlock(ServerCommandSource source, Predicate<CachedBlockPosition> block, int radius, RadiusType radiusType) {
        List<BlockPos> candidates;
        if (radiusType == RadiusType.TAXICAB) {
            candidates = findBlockCandidatesInTaxicabArea(source, block, radius);
        } else {
            candidates = findBlockCandidatesInSquareArea(source, block, radius, radiusType);
        }

        BlockPos origin = new BlockPos(source.getPosition());
        BlockPos closestBlock = candidates.stream()
                .filter(pos -> radiusType.distanceFunc.applyAsDouble(pos.subtract(origin)) <= radius)
                .min(Comparator.comparingDouble(pos -> radiusType.distanceFunc.applyAsDouble(pos.subtract(origin))))
                .orElse(null);

        if (closestBlock == null) {
            sendError(new TranslatableText("commands.cfindblock.notFound"));
            return 0;
        } else {
            double foundRadius = radiusType.distanceFunc.applyAsDouble(closestBlock.subtract(origin));
            sendFeedback(new TranslatableText("commands.cfindblock.success.left", foundRadius)
                    .append(getCoordsTextComponent(closestBlock))
                    .append(new TranslatableText("commands.cfindblock.success.right", foundRadius)));
            return 1;
        }
    }

    private static List<BlockPos> findBlockCandidatesInSquareArea(ServerCommandSource source, Predicate<CachedBlockPosition> blockMatcher, int radius, RadiusType radiusType) {
        World world = MinecraftClient.getInstance().world;
        BlockPos senderPos = new BlockPos(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(senderPos);

        List<BlockPos> blockCandidates = new ArrayList<>();

        // search in each chunk with an increasing radius, until we increase the radius
        // past an already found block
        int chunkRadius = (radius >> 4) + 1;
        for (int r = 0; r < chunkRadius; r++) {
            for (int chunkX = chunkPos.x - r; chunkX <= chunkPos.x + r; chunkX++) {
                for (int chunkZ = chunkPos.z - r; chunkZ <= chunkPos.z
                        + r; chunkZ += chunkX == chunkPos.x - r || chunkX == chunkPos.x + r ? 1 : r + r) {
                    Chunk chunk = world.getChunk(chunkX, chunkZ);
                    if (searchChunkForBlockCandidates(chunk, senderPos.getY(), blockMatcher, blockCandidates)) {
                        // update new, potentially shortened, radius
                        int dx = chunkPos.x - chunkX;
                        int dz = chunkPos.z - chunkZ;
                        int newChunkRadius;
                        if (radiusType == RadiusType.CARTESIAN) {
                            newChunkRadius = MathHelper.ceil(MathHelper.sqrt(dx * dx + dz * dz) + MathHelper.SQUARE_ROOT_OF_TWO);
                        } else {
                            newChunkRadius = Math.max(Math.abs(chunkPos.x - chunkX), Math.abs(chunkPos.z - chunkZ)) + 1;
                        }
                        if (newChunkRadius < chunkRadius) {
                            chunkRadius = newChunkRadius;
                        }
                    }
                }
            }
        }

        return blockCandidates;
    }

    private static List<BlockPos> findBlockCandidatesInTaxicabArea(ServerCommandSource source, Predicate<CachedBlockPosition> blockMatcher, int radius) {
        World world = MinecraftClient.getInstance().world;
        BlockPos senderPos = new BlockPos(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(senderPos);

        List<BlockPos> blockCandidates = new ArrayList<>();

        // search in each chunk with an increasing radius, until we increase the radius
        // past an already found block
        int chunkRadius = (radius >> 4) + 1;
        for (int r = 0; r < chunkRadius; r++) {
            for (int chunkX = chunkPos.x - r; chunkX <= chunkPos.x + r; chunkX++) {
                int chunkZ = chunkPos.z - (r - Math.abs(chunkPos.x - chunkX));
                for (int i = 0; i < 2; i++) {
                    Chunk chunk = world.getChunk(chunkX, chunkZ);
                    if (searchChunkForBlockCandidates(chunk, senderPos.getY(), blockMatcher, blockCandidates)) {
                        // update new, potentially shortened, radius
                        int newChunkRadius = Math.abs(chunkPos.x - chunkX) + Math.abs(chunkPos.z - chunkZ) + 1;
                        if (newChunkRadius < chunkRadius) {
                            chunkRadius = newChunkRadius;
                        }
                    }

                    chunkZ = chunkPos.z + (r - Math.abs(chunkPos.x - chunkX));
                }
            }
        }

        return blockCandidates;
    }

    private static boolean searchChunkForBlockCandidates(Chunk chunk, int senderY, Predicate<CachedBlockPosition> blockMatcher,
                                                  List<BlockPos> blockCandidates) {
        boolean found = false;
        int maxY = chunk.getHighestNonEmptySectionYOffset() + 15;

        // search every column for the block
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // search the column nearest to the sender first, and stop if we find the block
                int maxDy = Math.max(senderY, maxY - senderY);
                for (int dy = 0; dy <= maxDy; dy = dy > 0 ? -dy : -dy + 1) {
                    if (senderY + dy < 0 || senderY + dy >= 256) {
                        continue;
                    }
                    int worldX = (chunk.getPos().x << 4) + x;
                    int worldZ = (chunk.getPos().z << 4) + z;
                    if (blockMatcher.test(new CachedBlockPosition(MinecraftClient.getInstance().world, new BlockPos(worldX, senderY + dy, worldZ), false))) {
                        blockCandidates.add(new BlockPos(worldX, senderY + dy, worldZ));
                        found = true;
                        break;
                    }
                }
            }
        }

        return found;
    }

    private static enum RadiusType {
        CARTESIAN(pos -> Math.sqrt(pos.getSquaredDistance(BlockPos.ORIGIN))),
        RECTANGULAR(pos -> Math.max(Math.max(Math.abs(pos.getX()), Math.abs(pos.getY())), Math.abs(pos.getZ()))),
        TAXICAB(pos -> pos.getManhattanDistance(BlockPos.ORIGIN));

        final ToDoubleFunction<BlockPos> distanceFunc;
        RadiusType(ToDoubleFunction<BlockPos> distanceFunc) {
            this.distanceFunc = distanceFunc;
        }
    }

}
