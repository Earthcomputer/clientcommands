package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.mixin.OptionInstanceAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GammaCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cgamma")
            .then(argument("gamma", doubleArg())
                .executes(ctx -> setGamma(ctx.getSource(), getDouble(ctx, "gamma")))));
    }

    private static int setGamma(FabricClientCommandSource source, double gamma) {
        ((OptionInstanceAccessor) (Object) source.getClient().options.gamma()).forceSetValue(gamma);

        Component feedback = Component.translatable("commands.cgamma.success", gamma);
        source.sendFeedback(feedback);

        return Command.SINGLE_SUCCESS;
    }

}
