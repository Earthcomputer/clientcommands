package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.blockPos;
import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.getCBlockPos;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.getLookCoordsTextComponent;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NetherCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cnether")
            .then(argument("pos", blockPos())
                    .executes(ctx -> convertCoords(ctx.getSource(), getCBlockPos(ctx, "pos"))))
            .executes(ctx -> convertCoords(ctx.getSource(),
                    ctx.getSource().getPlayer().getBlockPos())));
    }

    private static int convertCoords(FabricClientCommandSource source, BlockPos pos) {
        BlockPos targetPos;
        String sourceWorld;
        String targetWorld;

        if (source.getPlayer().world.getRegistryKey() == World.NETHER) {
            targetPos = new BlockPos(pos.getX()*8, pos.getY(), pos.getZ()*8);
            sourceWorld = I18n.translate("commands.cnether.world.nether");
            targetWorld = I18n.translate("commands.cnether.world.overworld");
        } else {
            targetPos = new BlockPos(pos.getX()/8, pos.getY(), pos.getZ()/8);
            sourceWorld = I18n.translate("commands.cnether.world.overworld");
            targetWorld = I18n.translate("commands.cnether.world.nether");
        }

        source.sendFeedback(getCoordsTextComponent(pos)
                .append(Text.translatable("commands.cnether.coords.left", sourceWorld, targetWorld))
                .append(getLookCoordsTextComponent(targetPos))
                .append(Text.translatable("commands.cnether.coords.right", sourceWorld, targetWorld)));

        return Command.SINGLE_SUCCESS;
    }

    private static MutableText getCoordsTextComponent(BlockPos pos) {
        return Text.translatable("commands.client.blockpos", pos.getX(), pos.getY(), pos.getZ());
    }
}
