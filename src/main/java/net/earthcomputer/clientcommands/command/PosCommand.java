package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import dev.xpple.clientarguments.arguments.CDimensionArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.*;
import static dev.xpple.clientarguments.arguments.CDimensionArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

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
                                    ctx.getSource().getPlayer().getBlockPos(), getCDimensionArgument(ctx, "sourceDimension"), getCDimensionArgument(ctx, "targetDimension")))))
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            ctx.getSource().getPlayer().getBlockPos(), null, getCDimensionArgument(ctx, "targetDimension")))))
            .then(literal("from").then(argument("sourceDimension", dimension())
                    .then(argument("pos", blockPos())
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    getCBlockPos(ctx, "pos"), getCDimensionArgument(ctx, "sourceDimension"), null)))
                    .then(literal("to").then(argument("targetDimension", dimension())
                            .then(argument("pos", blockPos())
                                    .executes(ctx -> convertCoords(ctx.getSource(),
                                            getCBlockPos(ctx, "pos"), getCDimensionArgument(ctx, "sourceDimension"), getCDimensionArgument(ctx, "targetDimension"))))
                            .executes(ctx -> convertCoords(ctx.getSource(),
                                    ctx.getSource().getPlayer().getBlockPos(), getCDimensionArgument(ctx, "sourceDimension"), getCDimensionArgument(ctx, "targetDimension")))))
                    .executes(ctx -> convertCoords(ctx.getSource(),
                            ctx.getSource().getPlayer().getBlockPos(), getCDimensionArgument(ctx, "sourceDimension"), null))))
            .executes(ctx -> convertCoords(ctx.getSource(),
                    ctx.getSource().getPlayer().getBlockPos(), null, null)));
    }

    private static int convertCoords(FabricClientCommandSource source, BlockPos pos,
                                     CDimensionArgumentType.DimensionArgument sourceDim,
                                     CDimensionArgumentType.DimensionArgument targetDim) {
        RegistryKey<World> sourceWorld;
        RegistryKey<World> targetWorld;
        if (sourceDim == null && targetDim == null) {
            // If neither argument is given, set current dimension as source and "opposite" as target
            sourceWorld = source.getPlayer().world.getRegistryKey();
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
        BlockPos targetPos = new BlockPos(Math.floor(pos.getX() * scaleFactor), pos.getY(), Math.floor(pos.getZ() * scaleFactor));
        String sourceWorldName = getWorldName(sourceWorld);
        String targetWorldName = getWorldName(targetWorld);

        source.sendFeedback(getCoordsTextComponent(pos)
                .append(Text.translatable("commands.cpos.coords.left", sourceWorldName, targetWorldName))
                .append(getLookCoordsTextComponent(targetPos))
                .append(Text.translatable("commands.cpos.coords.right", sourceWorldName, targetWorldName)));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Return the "opposite" world; that is the Nether when given anything else than the Nether,
     * and the Overworld, if given the Nether.
     */
    private static RegistryKey<World> getOppositeWorld(RegistryKey<World> world) {
        if (world == World.NETHER) {
            return World.OVERWORLD;
        } else {
            return World.NETHER;
        }
    }

    /**
     * Return the scale factor of the given world.
     */
    private static int getCoordinateScale(RegistryKey<World> world) {
        // This is supposed to mimick the value from DimensionType.coordinateScale(),
        // which unfortunately is not available to us at this time, so we hard-code
        // the known values of Vanilla.
        if (world == World.NETHER) {
            return 8;
        } else {
            return 1;
        }
    }

    /**
     * Return a translated string describing the given world, or the built-in ID
     * if that fails.
     */
    private static String getWorldName(RegistryKey<World> world) {
        String worldNameKey = "commands.cpos.world." + world.getValue().getPath();
        if (I18n.hasTranslation(worldNameKey)) {
            return I18n.translate(worldNameKey);
        } else {
            return world.getValue().getPath();
        }
    }

    private static MutableText getCoordsTextComponent(BlockPos pos) {
        return Text.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ());
    }
}
