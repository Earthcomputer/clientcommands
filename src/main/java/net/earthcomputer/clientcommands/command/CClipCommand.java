package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.sendFeedback;
import static net.minecraft.command.argument.BlockPosArgumentType.blockPos;
import static net.minecraft.command.argument.BlockPosArgumentType.getBlockPos;
import static net.minecraft.command.argument.RotationArgumentType.getRotation;
import static net.minecraft.command.argument.RotationArgumentType.rotation;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
public class CClipCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cclip");

        dispatcher.register(literal("cclip")
                .then(argument("blocks" , doubleArg())
                    .executes(ctx -> Clip(ctx.getSource(), getDouble(ctx, "blocks")))));
    }



    private static int Clip(ServerCommandSource source, double blocks) throws CommandSyntaxException {
        PlayerEntity player = MinecraftClient.getInstance().player;

        Vec3d forward = Vec3d.fromPolar(0, player.yaw).normalize();
        player.updatePosition(player.getX() + forward.x * blocks, player.getY()+0.1, player.getZ() + forward.z * blocks);

        Text feedback = new TranslatableText("commands.cclip.success", blocks);
        sendFeedback(feedback);

        return 0;
    }

}
