package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.earthcomputer.clientcommands.mixin.SimpleOptionAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class GammaCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cgamma")
            .then(argument("gamma", doubleArg())
                .executes(ctx -> setGamma(ctx.getSource(), getDouble(ctx, "gamma")))));
    }

    private static int setGamma(FabricClientCommandSource source, double gamma) {
        ((SimpleOptionAccessor) (Object) source.getClient().options.getGamma()).forceSetValue(gamma);

        Text feedback = Text.translatable("commands.cgamma.success", gamma);
        source.sendFeedback(feedback);

        return Command.SINGLE_SUCCESS;
    }

}
