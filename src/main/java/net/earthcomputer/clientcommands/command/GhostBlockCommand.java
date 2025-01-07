package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.Predicate;

import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CBlockPredicateArgument.*;
import static dev.xpple.clientarguments.arguments.CBlockStateArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class GhostBlockCommand {

    private static final SimpleCommandExceptionType SET_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));
    private static final SimpleCommandExceptionType FILL_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cghostblock")
            .then(literal("set")
                .then(argument("pos", blockPos())
                    .then(argument("block", blockState(context))
                        .executes(ctx -> setGhostBlock(ctx.getSource(), getBlockPos(ctx, "pos"), getBlockState(ctx, "block").getState())))))
            .then(literal("fill")
                .then(argument("from", blockPos())
                    .then(argument("to", blockPos())
                        .then(argument("block", blockState(context))
                            .executes(ctx -> fillGhostBlocks(ctx.getSource(), getBlockPos(ctx, "from"), getBlockPos(ctx, "to"), getBlockState(ctx, "block").getState(), pos -> true))
                            .then(literal("replace")
                                .then(argument("filter", blockPredicate(context))
                                    .executes(ctx -> fillGhostBlocks(ctx.getSource(), getBlockPos(ctx, "from"), getBlockPos(ctx, "to"), getBlockState(ctx, "block").getState(), getBlockPredicate(ctx, "filter"))))))))));
    }

    private static int setGhostBlock(FabricClientCommandSource source, BlockPos pos, BlockState state) throws CommandSyntaxException {
        ClientLevel level = source.getWorld();
        assert level != null;

        checkLoaded(level, pos);

        boolean result = level.setBlock(pos, state, 18);
        if (result) {
            source.sendFeedback(Component.translatable("commands.cghostblock.set.success"));
            return Command.SINGLE_SUCCESS;
        } else {
            throw SET_FAILED_EXCEPTION.create();
        }
    }

    private static int fillGhostBlocks(FabricClientCommandSource source, BlockPos from, BlockPos to, BlockState state, Predicate<BlockInWorld> filter) throws CommandSyntaxException {
        ClientLevel level = source.getWorld();
        assert level != null;

        checkLoaded(level, from);
        checkLoaded(level, to);

        BoundingBox range = BoundingBox.fromCorners(from, to);
        int successCount = 0;
        for (BlockPos pos : BlockPos.betweenClosed(range.minX(), range.minY(), range.minZ(), range.maxX(), range.maxY(), range.maxZ())) {
            if (filter.test(new BlockInWorld(level, pos, true))) {
                if (level.setBlock(pos, state, 18)) {
                    successCount++;
                }
            }
        }

        if (successCount == 0) {
            throw FILL_FAILED_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable("commands.cghostblock.fill.success", successCount));

        return successCount;
    }

    private static void checkLoaded(ClientLevel level, BlockPos pos) throws CommandSyntaxException {
        if (!level.hasChunkAt(pos)) {
            throw UNLOADED_EXCEPTION.create();
        } else if (!level.isInWorldBounds(pos)) {
            throw OUT_OF_WORLD_EXCEPTION.create();
        }
    }

}
