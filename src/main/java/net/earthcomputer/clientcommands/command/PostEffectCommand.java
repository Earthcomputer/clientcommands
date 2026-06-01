package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import static net.earthcomputer.clientcommands.command.arguments.PostEffectArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class PostEffectCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cposteffect")
            .then(argument("posteffect", postEffect())
                .executes(ctx -> applyPostEffect(ctx.getSource(), getPostEffect(ctx, "posteffect"))))
            .then(literal("reset")
                .executes(ctx -> applyPostEffect(ctx.getSource(), null))));
    }

    private static int applyPostEffect(FabricClientCommandSource source, @Nullable Identifier postEffect) {
        if (postEffect == null) {
            source.getClient().gameRenderer.clearPostEffect();
            source.sendFeedback(Component.translatable("commands.cposteffect.reset.success"));
            return Command.SINGLE_SUCCESS;
        }
        source.getClient().gameRenderer.setPostEffect(postEffect);
        source.sendFeedback(Component.translatable("commands.cposteffect.apply.success", postEffect));
        return Command.SINGLE_SUCCESS;
    }
}
