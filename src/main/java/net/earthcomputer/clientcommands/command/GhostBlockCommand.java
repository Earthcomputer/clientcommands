package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.minecraft.command.argument.BlockPosArgumentType.*;
import static net.minecraft.command.argument.BlockStateArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class GhostBlockCommand {

    private static final SimpleCommandExceptionType SET_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.setblock.failed"));
    private static final SimpleCommandExceptionType FILL_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.fill.failed"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cghostblock");

        dispatcher.register(literal("cghostblock")
            .then(literal("set")
                .then(argument("pos", blockPos())
                    .then(argument("block", blockState())
                        .executes(ctx -> setGhostBlock(getBlockPos(ctx, "pos"), getBlockState(ctx, "block").getBlockState())))))
            .then(literal("fill")
                .then(argument("from", blockPos())
                    .then(argument("to", blockPos())
                        .then(argument("block", blockState())
                            .executes(ctx -> fillGhostBlocks(getBlockPos(ctx, "from"), getBlockPos(ctx, "to"), getBlockState(ctx, "block").getBlockState())))))));
    }

    private static int setGhostBlock(BlockPos pos, BlockState state) throws CommandSyntaxException {
        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;

        checkLoaded(world, pos);

        boolean result = world.setBlockState(pos, state, 18);
        if (result) {
            sendFeedback("commands.cghostblock.set.success");
            return 1;
        } else {
            throw SET_FAILED_EXCEPTION.create();
        }
    }

    private static int fillGhostBlocks(BlockPos from, BlockPos to, BlockState state) throws CommandSyntaxException {
        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;

        checkLoaded(world, from);
        checkLoaded(world, to);

        BlockBox range = BlockBox.create(from, to);
        int successCount = 0;
        for (BlockPos pos : BlockPos.iterate(range.getMinX(), range.getMinY(), range.getMinZ(), range.getMaxX(), range.getMaxY(), range.getMaxZ())) {
            if (world.setBlockState(pos, state, 18)) {
                successCount++;
            }
        }

        if (successCount == 0) {
            throw FILL_FAILED_EXCEPTION.create();
        }

        sendFeedback("commands.cghostblock.fill.success", successCount);

        return successCount;
    }

    private static void checkLoaded(ClientWorld world, BlockPos pos) throws CommandSyntaxException {
        if (!world.isChunkLoaded(pos)) {
            throw UNLOADED_EXCEPTION.create();
        } else if (!MinecraftClient.getInstance().world.isInBuildLimit(pos)) {
            throw OUT_OF_WORLD_EXCEPTION.create();
        }
    }

}
