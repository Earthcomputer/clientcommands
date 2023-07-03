package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FindBlockCommand {

    public static final int MAX_RADIUS = 16 * 8;

    private static final SimpleCommandExceptionType NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cfindblock.notFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("cfindblock")
            .then(argument("block", blockPredicate(registryAccess))
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

    public static int findBlock(FabricClientCommandSource source, ClientBlockPredicate block, int radius, RadiusType radiusType) throws CommandSyntaxException {
        List<BlockPos> candidates;
        if (radiusType == RadiusType.TAXICAB) {
            candidates = findBlockCandidatesInTaxicabArea(source, block, radius);
        } else {
            candidates = findBlockCandidatesInSquareArea(source, block, radius, radiusType);
        }

        BlockPos origin = BlockPos.ofFloored(source.getPosition());
        BlockPos closestBlock = candidates.stream()
                .filter(pos -> radiusType.distanceFunc.applyAsDouble(pos.subtract(origin)) <= radius)
                .min(Comparator.comparingDouble(pos -> radiusType.distanceFunc.applyAsDouble(pos.subtract(origin))))
                .orElse(null);

        if (closestBlock == null) {
            throw NOT_FOUND_EXCEPTION.create();
        } else {
            String foundRadius = "%.2f".formatted(radiusType.distanceFunc.applyAsDouble(closestBlock.subtract(origin)));
            source.sendFeedback(Text.translatable("commands.cfindblock.success.left", foundRadius)
                    .append(getLookCoordsTextComponent(closestBlock))
                    .append(" ")
                    .append(getGlowCoordsTextComponent(Text.translatable("commands.cfindblock.success.glow"), closestBlock))
                    .append(Text.translatable("commands.cfindblock.success.right", foundRadius)));
            return Command.SINGLE_SUCCESS;
        }
    }

    private static List<BlockPos> findBlockCandidatesInSquareArea(FabricClientCommandSource source, ClientBlockPredicate blockMatcher, int radius, RadiusType radiusType) {
        World world = source.getWorld();
        BlockPos senderPos = BlockPos.ofFloored(source.getPosition());
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
                    if (searchChunkForBlockCandidates(source, chunk, senderPos.getY(), blockMatcher, blockCandidates)) {
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

    private static List<BlockPos> findBlockCandidatesInTaxicabArea(FabricClientCommandSource source, ClientBlockPredicate blockMatcher, int radius) {
        World world = source.getWorld();
        BlockPos senderPos = BlockPos.ofFloored(source.getPosition());
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
                    if (searchChunkForBlockCandidates(source, chunk, senderPos.getY(), blockMatcher, blockCandidates)) {
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

    private static boolean searchChunkForBlockCandidates(FabricClientCommandSource source, Chunk chunk, int senderY, ClientBlockPredicate blockMatcher, List<BlockPos> blockCandidates) {
        ClientWorld world = source.getWorld();

        int bottomY = world.getBottomY();
        int topY = world.getTopY();

        boolean found = false;
        @SuppressWarnings("removal")
        int maxY = chunk.getHighestNonEmptySectionYOffset() + 15;

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        // search every column for the block
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // search the column nearest to the sender first, and stop if we find the block
                int maxDy = Math.max(senderY - bottomY, maxY - senderY);
                for (int dy = 0; dy <= maxDy; dy = dy > 0 ? -dy : -dy + 1) {
                    if (senderY + dy < bottomY || senderY + dy > topY) {
                        continue;
                    }
                    int worldX = (chunk.getPos().x << 4) + x;
                    int worldZ = (chunk.getPos().z << 4) + z;
                    if (blockMatcher.test(world, mutablePos.set(worldX, senderY + dy, worldZ))) {
                        blockCandidates.add(mutablePos.toImmutable());
                        found = true;
                        break;
                    }
                }
            }
        }

        return found;
    }

    public enum RadiusType {
        CARTESIAN(pos -> Math.sqrt(pos.getSquaredDistance(BlockPos.ORIGIN))),
        RECTANGULAR(pos -> Math.max(Math.max(Math.abs(pos.getX()), Math.abs(pos.getY())), Math.abs(pos.getZ()))),
        TAXICAB(pos -> pos.getManhattanDistance(BlockPos.ORIGIN));

        final ToDoubleFunction<BlockPos> distanceFunc;
        RadiusType(ToDoubleFunction<BlockPos> distanceFunc) {
            this.distanceFunc = distanceFunc;
        }
    }

}
