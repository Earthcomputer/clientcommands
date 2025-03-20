package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.util.BuildInfo;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class BuildInfoCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cbuildinfo")
            .executes(ctx -> buildInfo(ctx.getSource())));
    }

    private static int buildInfo(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("commands.cbuildinfo.success", BuildInfo.VERSION, BuildInfo.BRANCH, BuildInfo.SHORT_COMMIT_HASH));
        return Command.SINGLE_SUCCESS;
    }
}
