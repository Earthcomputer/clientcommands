package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CDimensionArgument.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class PosCommand {
    // Syntax: "/cpos [to|from <dimension>] [from|to <dimension>] [<pos>]"
    // "/cpos to x to y", or "/cpos from x from y" are invalid
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cpos")
            .then(argument("pos", blockPos())
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            getBlockPos(ctx, "pos"), null, null)))
            .then(literal("to").then(argument("targetDimension", dimension())
                    .then(argument("pos", blockPos())
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    getBlockPos(ctx, "pos"), null, getDimension(ctx, "targetDimension"))))
                    .then(literal("from").then(argument("sourceDimension", dimension())
                            .then(argument("pos", blockPos())
                                    .executes(ctx -> convertCoords(ctx.getSource(),
                                            getBlockPos(ctx, "pos"), getDimension(ctx, "sourceDimension"), getDimension(ctx, "targetDimension"))))
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    ctx.getSource().getPlayer().blockPosition(), getDimension(ctx, "sourceDimension"), getDimension(ctx, "targetDimension")))))
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            ctx.getSource().getPlayer().blockPosition(), null, getDimension(ctx, "targetDimension")))))
            .then(literal("from").then(argument("sourceDimension", dimension())
                    .then(argument("pos", blockPos())
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    getBlockPos(ctx, "pos"), getDimension(ctx, "sourceDimension"), null)))
                    .then(literal("to").then(argument("targetDimension", dimension())
                            .then(argument("pos", blockPos())
                                    .executes(ctx -> convertCoords(ctx.getSource(),
                                            getBlockPos(ctx, "pos"), getDimension(ctx, "sourceDimension"), getDimension(ctx, "targetDimension"))))
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    ctx.getSource().getPlayer().blockPosition(), getDimension(ctx, "sourceDimension"), getDimension(ctx, "targetDimension")))))
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            ctx.getSource().getPlayer().blockPosition(), getDimension(ctx, "sourceDimension"), null))))
            .executes(ctx -> convertCoords(ctx.getSource(),
                    ctx.getSource().getPlayer().blockPosition(), null, null)));
    }

    private static int convertCoords(FabricClientCommandSource source, BlockPos pos,
                                     @Nullable ResourceKey<Level> sourceDim,
                                     @Nullable ResourceKey<Level> targetDim) {
        ResourceKey<Level> sourceLevel;
        ResourceKey<Level> targetLevel;
        if (sourceDim == null && targetDim == null) {
            // If neither argument is given, set current dimension as source and "opposite" as target
            sourceLevel = source.getPlayer().level().dimension();
            targetLevel = getOppositeLevel(sourceLevel);
        } else if (targetDim == null) {
            // If only source dimension is given, set the target to "opposite"
            sourceLevel = sourceDim;
            targetLevel = getOppositeLevel(sourceLevel);
        } else if (sourceDim == null) {
            // If only target dimension is given, set the source to "opposite"
            targetLevel = targetDim;
            sourceLevel = getOppositeLevel(targetLevel);
        } else {
            // Both dimensions are specified by the user
            sourceLevel = sourceDim;
            targetLevel = targetDim;
        }

        double scaleFactor = (double) getCoordinateScale(sourceLevel) / getCoordinateScale(targetLevel);
        BlockPos targetPos = BlockPos.containing(pos.getX() * scaleFactor, pos.getY(), pos.getZ() * scaleFactor);
        String sourceWorldName = getLevelName(sourceLevel);
        String targetWorldName = getLevelName(targetLevel);

        source.sendFeedback(Component.translatable(
            "commands.cpos.coords",
            getCoordsTextComponent(pos),
            sourceWorldName,
            getLookCoordsTextComponent(targetPos),
            targetWorldName)
        );

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Return the "opposite" level; that is the Nether when given anything else than the Nether,
     * and the Overworld, if given the Nether.
     */
    private static ResourceKey<Level> getOppositeLevel(ResourceKey<Level> level) {
        if (level == Level.NETHER) {
            return Level.OVERWORLD;
        } else {
            return Level.NETHER;
        }
    }

    /**
     * Return the scale factor of the given level.
     */
    private static int getCoordinateScale(ResourceKey<Level> level) {
        // This is supposed to mimick the value from DimensionType.coordinateScale(),
        // which unfortunately is not available to us at this time, so we hard-code
        // the known values of Vanilla.
        if (level == Level.NETHER) {
            return 8;
        } else {
            return 1;
        }
    }

    /**
     * Return a translated string describing the given level, or the built-in ID
     * if that fails.
     */
    private static String getLevelName(ResourceKey<Level> level) {
        String levelNameKey = "commands.cpos.level." + level.location().getPath();
        if (I18n.exists(levelNameKey)) {
            return I18n.get(levelNameKey);
        } else {
            return level.location().getPath();
        }
    }

    private static MutableComponent getCoordsTextComponent(BlockPos pos) {
        return Component.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ());
    }
}
