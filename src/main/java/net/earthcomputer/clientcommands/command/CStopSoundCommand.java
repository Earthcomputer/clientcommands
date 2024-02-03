package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xpple.clientarguments.arguments.CSuggestionProviders;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import static dev.xpple.clientarguments.arguments.CIdentifierArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CStopSoundCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var builder = literal("cstopsound");

        for (SoundSource source : SoundSource.values()) {
            builder.then(buildArguments(source, source.getName()));
        }
        builder.then(buildArguments(null, "*"));

        dispatcher.register(builder);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildArguments(SoundSource source, String literal) {
        return literal(literal)
            .executes(ctx -> stopSound(ctx.getSource(), source, null))
            .then(argument("sound", identifier())
                .suggests(CSuggestionProviders.AVAILABLE_SOUNDS)
                .executes(ctx -> stopSound(ctx.getSource(), source, getCIdentifier(ctx, "sound"))));
    }

    private static int stopSound(FabricClientCommandSource source, SoundSource soundSource, ResourceLocation sound) {
        source.getClient().getSoundManager().stop(sound, soundSource);

        if (soundSource == null && sound == null) {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.sourceless.any"));
        } else if (soundSource == null) {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.sourceless.sound", sound));
        } else if (sound == null) {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.source.any", soundSource.getName()));
        } else {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.source.sound", sound, soundSource.getName()));
        }
        return Command.SINGLE_SUCCESS;
    }

}
