package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class PlayerInfoCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralCommandNode<FabricClientCommandSource> cplayerinfo = dispatcher.register(literal("cplayerinfo"));
        dispatcher.register(literal("cplayerinfo")
                .executes(ctx -> warn(ctx.getSource())));
    }

    private static int warn(FabricClientCommandSource source) {
        source.sendError(Text.literal("This command currently has no purpose."));
        return Command.SINGLE_SUCCESS;
    }
}
