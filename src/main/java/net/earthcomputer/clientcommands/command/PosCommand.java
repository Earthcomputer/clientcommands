package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import dev.xpple.clientarguments.arguments.CDimensionArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.blockPos;
import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.getCBlockPos;
import static dev.xpple.clientarguments.arguments.CDimensionArgumentType.dimension;
import static dev.xpple.clientarguments.arguments.CDimensionArgumentType.getCDimensionArgument;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.getLookCoordsTextComponent;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class PosCommand {
    // Syntax: "/cpos [to|from <dimension>] [from|to <dimension>] [<pos>]"
    // "/cpos to x to y", or "/cpos from x from y" are invalid
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cpos")
            .then(argument("pos", blockPos())
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            getCBlockPos(ctx, "pos"), null, null)))
            .then(literal("to").then(argument("targetDimension", dimension())
                    .then(argument("pos", blockPos())
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    getCBlockPos(ctx, "pos"), null, getCDimensionArgument(ctx, "targetDimension"))))
                    .then(literal("from").then(argument("sourceDimension", dimension())
                            .then(argument("pos", blockPos())
                                    .executes(ctx -> convertCoords(ctx.getSource(),
                                            getCBlockPos(ctx, "pos"), getCDimensionArgument(ctx, "sourceDimension"), getCDimensionArgument(ctx, "targetDimension"))))
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    ctx.getSource().getPlayer().blockPosition(), getCDimensionArgument(ctx, "sourceDimension"), getCDimensionArgument(ctx, "targetDimension")))))
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            ctx.getSource().getPlayer().blockPosition(), null, getCDimensionArgument(ctx, "targetDimension")))))
            .then(literal("from").then(argument("sourceDimension", dimension())
                    .then(argument("pos", blockPos())
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    getCBlockPos(ctx, "pos"), getCDimensionArgument(ctx, "sourceDimension"), null)))
                    .then(literal("to").then(argument("targetDimension", dimension())
                            .then(argument("pos", blockPos())
                                    .executes(ctx -> convertCoords(ctx.getSource(),
                                            getCBlockPos(ctx, "pos"), getCDimensionArgument(ctx, "sourceDimension"), getCDimensionArgument(ctx, "targetDimension"))))
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    ctx.getSource().getPlayer().blockPosition(), getCDimensionArgument(ctx, "sourceDimension"), getCDimensionArgument(ctx, "targetDimension")))))
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            ctx.getSource().getPlayer().blockPosition(), getCDimensionArgument(ctx, "sourceDimension"), null))))
            .executes(ctx -> convertCoords(ctx.getSource(),
                    ctx.getSource().getPlayer().blockPosition(), null, null)));
    }

    private static int convertCoords(FabricClientCommandSource source, BlockPos pos,
                                     CDimensionArgumentType.DimensionArgument sourceDim,
                                     CDimensionArgumentType.DimensionArgument targetDim) {
        ResourceKey<Level> sourceWorld;
        ResourceKey<Level> targetWorld;
        if (sourceDim == null && targetDim == null) {
            // If neither argument is given, set current dimension as source and "opposite" as target
            sourceWorld = source.getPlayer().level().dimension();
            targetWorld = getOppositeWorld(sourceWorld);
        } else if (targetDim == null) {
            // If only source dimension is given, set the target to "opposite"
            sourceWorld = sourceDim.getRegistryKey();
            targetWorld = getOppositeWorld(sourceWorld);
        } else if (sourceDim == null) {
            // If only target dimension is given, set the source to "opposite"
            targetWorld = targetDim.getRegistryKey();
            sourceWorld = getOppositeWorld(targetWorld);
        } else {
            // Both dimensions are specified by the user
            sourceWorld = sourceDim.getRegistryKey();
            targetWorld = targetDim.getRegistryKey();
        }

        double scaleFactor = (double) getCoordinateScale(sourceWorld) / getCoordinateScale(targetWorld);
        BlockPos targetPos = BlockPos.containing(pos.getX() * scaleFactor, pos.getY(), pos.getZ() * scaleFactor);
        String sourceWorldName = getWorldName(sourceWorld);
        String targetWorldName = getWorldName(targetWorld);

        source.sendFeedback(getCoordsTextComponent(pos)
                .append(Component.translatable("commands.cpos.coords.left", sourceWorldName, targetWorldName))
                .append(getLookCoordsTextComponent(targetPos))
                .append(Component.translatable("commands.cpos.coords.right", sourceWorldName, targetWorldName)));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Return the "opposite" world; that is the Nether when given anything else than the Nether,
     * and the Overworld, if given the Nether.
     */
    private static ResourceKey<Level> getOppositeWorld(ResourceKey<Level> world) {
        if (world == Level.NETHER) {
            return Level.OVERWORLD;
        } else {
            return Level.NETHER;
        }
    }

    /**
     * Return the scale factor of the given world.
     */
    private static int getCoordinateScale(ResourceKey<Level> world) {
        // This is supposed to mimick the value from DimensionType.coordinateScale(),
        // which unfortunately is not available to us at this time, so we hard-code
        // the known values of Vanilla.
        if (world == Level.NETHER) {
            return 8;
        } else {
            return 1;
        }
    }

    /**
     * Return a translated string describing the given world, or the built-in ID
     * if that fails.
     */
    private static String getWorldName(ResourceKey<Level> world) {
        String worldNameKey = "commands.cpos.world." + world.location().getPath();
        if (I18n.exists(worldNameKey)) {
            return I18n.get(worldNameKey);
        } else {
            return world.location().getPath();
        }
    }

    private static MutableComponent getCoordsTextComponent(BlockPos pos) {
        return Component.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ());
    }
}
