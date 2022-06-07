package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xpple.clientarguments.arguments.CSuggestionProviders;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static dev.xpple.clientarguments.arguments.CIdentifierArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CStopSoundCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var builder = literal("cstopsound");

        for (SoundCategory category : SoundCategory.values()) {
            builder.then(buildArguments(category, category.getName()));
        }
        builder.then(buildArguments(null, "*"));

        dispatcher.register(builder);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildArguments(SoundCategory category, String literal) {
        return literal(literal)
            .executes(ctx -> stopSound(ctx.getSource(), category, null))
            .then(argument("sound", identifier())
                .suggests(CSuggestionProviders.AVAILABLE_SOUNDS)
                .executes(ctx -> stopSound(ctx.getSource(), category, getCIdentifier(ctx, "sound"))));
    }

    private static int stopSound(FabricClientCommandSource source, SoundCategory category, Identifier sound) {
        source.getClient().getSoundManager().stopSounds(sound, category);

        if (category == null && sound == null) {
            source.sendFeedback(Text.translatable("commands.cstopsound.success.sourceless.any"));
        } else if (category == null) {
            source.sendFeedback(Text.translatable("commands.cstopsound.success.sourceless.sound", sound));
        } else if (sound == null) {
            source.sendFeedback(Text.translatable("commands.cstopsound.success.source.any", category.getName()));
        } else {
            source.sendFeedback(Text.translatable("commands.cstopsound.success.source.sound", sound, category.getName()));
        }
        return 0;
    }

}
