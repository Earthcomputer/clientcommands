package net.earthcomputer.clientcommands.command;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.IdentifierArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

public class CStopSoundCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cstopsound");

        var builder = literal("cstopsound");

        for (SoundCategory category : SoundCategory.values()) {
            builder.then(buildArguments(category, category.getName()));
        }
        builder.then(buildArguments(null, "*"));

        dispatcher.register(builder);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildArguments(SoundCategory category, String literal) {
        return literal(literal)
            .executes(ctx -> stopSound(ctx.getSource(), category, null))
            .then(argument("sound", identifier())
                .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                .executes(ctx -> stopSound(ctx.getSource(), category, getIdentifier(ctx, "sound"))));
    }

    private static int stopSound(ServerCommandSource source, SoundCategory category, Identifier sound) {
        MinecraftClient.getInstance().getSoundManager().stopSounds(sound, category);

        if (category == null && sound == null) {
            sendFeedback(new TranslatableText("commands.cstopsound.success.sourceless.any"));
        } else if (category == null) {
            sendFeedback(new TranslatableText("commands.cstopsound.success.sourceless.sound", sound));
        } else if (sound == null) {
            sendFeedback(new TranslatableText("commands.cstopsound.success.source.any", category.getName()));
        } else {
            sendFeedback(new TranslatableText("commands.cstopsound.success.source.sound", sound, category.getName()));
        }
        return 0;
    }

}
