package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.Vec3d;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.sendFeedback;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VClipCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cvclip");

        dispatcher.register(literal("cvclip")
                .then(argument("blocks" , doubleArg())
                    .executes(ctx -> Clip(ctx.getSource(), getDouble(ctx, "blocks")))));
    }



    private static int Clip(ServerCommandSource source, double blocks) throws CommandSyntaxException {
        PlayerEntity player = MinecraftClient.getInstance().player;

        player.updatePosition(player.getX(), player.getY() + blocks, player.getZ());

        Text feedback = new TranslatableText("commands.cvclip.success", blocks);
        sendFeedback(feedback);

        return 0;
    }

}
