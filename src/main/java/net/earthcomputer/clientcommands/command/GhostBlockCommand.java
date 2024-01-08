package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockPredicateArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockStateArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class GhostBlockCommand {

    private static final SimpleCommandExceptionType SET_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.setblock.failed"));
    private static final SimpleCommandExceptionType FILL_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.fill.failed"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("cghostblock")
            .then(literal("set")
                .then(argument("pos", blockPos())
                    .then(argument("block", blockState(registryAccess))
                        .executes(ctx -> setGhostBlock(ctx.getSource(), getCBlockPos(ctx, "pos"), getCBlockState(ctx, "block").getBlockState())))))
            .then(literal("fill")
                .then(argument("from", blockPos())
                    .then(argument("to", blockPos())
                        .then(argument("block", blockState(registryAccess))
                            .executes(ctx -> fillGhostBlocks(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getCBlockState(ctx, "block").getBlockState(), pos -> true))
                            .then(literal("replace")
                                .then(argument("filter", blockPredicate(registryAccess))
                                    .executes(ctx -> fillGhostBlocks(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getCBlockState(ctx, "block").getBlockState(), getCBlockPredicate(ctx, "filter"))))))))));
    }

    private static int setGhostBlock(FabricClientCommandSource source, BlockPos pos, BlockState state) throws CommandSyntaxException {
        ClientWorld world = source.getWorld();
        assert world != null;

        checkLoaded(world, pos);

        boolean result = world.setBlockState(pos, state, 18);
        if (result) {
            source.sendFeedback(Text.translatable("commands.cghostblock.set.success"));
            return Command.SINGLE_SUCCESS;
        } else {
            throw SET_FAILED_EXCEPTION.create();
        }
    }

    private static int fillGhostBlocks(FabricClientCommandSource source, BlockPos from, BlockPos to, BlockState state, Predicate<CachedBlockPosition> filter) throws CommandSyntaxException {
        ClientWorld world = source.getWorld();
        assert world != null;

        checkLoaded(world, from);
        checkLoaded(world, to);

        BlockBox range = BlockBox.create(from, to);
        int successCount = 0;
        for (BlockPos pos : BlockPos.iterate(range.getMinX(), range.getMinY(), range.getMinZ(), range.getMaxX(), range.getMaxY(), range.getMaxZ())) {
            if (filter.test(new CachedBlockPosition(world, pos, true))) {
                if (world.setBlockState(pos, state, 18)) {
                    successCount++;
                }
            }
        }

        if (successCount == 0) {
            throw FILL_FAILED_EXCEPTION.create();
        }

        source.sendFeedback(Text.translatable("commands.cghostblock.fill.success", successCount));

        return successCount;
    }

    private static void checkLoaded(ClientWorld world, BlockPos pos) throws CommandSyntaxException {
        if (!world.isChunkLoaded(pos)) {
            throw UNLOADED_EXCEPTION.create();
        } else if (!world.isInBuildLimit(pos)) {
            throw OUT_OF_WORLD_EXCEPTION.create();
        }
    }

}
