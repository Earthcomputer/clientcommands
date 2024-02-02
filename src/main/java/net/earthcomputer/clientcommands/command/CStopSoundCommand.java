package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xpple.clientarguments.arguments.CSuggestionProviders;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import static dev.xpple.clientarguments.arguments.CIdentifierArgumentType.getCIdentifier;
import static dev.xpple.clientarguments.arguments.CIdentifierArgumentType.identifier;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CStopSoundCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var builder = literal("cstopsound");

        for (SoundSource category : SoundSource.values()) {
            builder.then(buildArguments(category, category.getName()));
        }
        builder.then(buildArguments(null, "*"));

        dispatcher.register(builder);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildArguments(SoundSource category, String literal) {
        return literal(literal)
            .executes(ctx -> stopSound(ctx.getSource(), category, null))
            .then(argument("sound", identifier())
                .suggests(CSuggestionProviders.AVAILABLE_SOUNDS)
                .executes(ctx -> stopSound(ctx.getSource(), category, getCIdentifier(ctx, "sound"))));
    }

    private static int stopSound(FabricClientCommandSource source, SoundSource category, ResourceLocation sound) {
        source.getClient().getSoundManager().stop(sound, category);

        if (category == null && sound == null) {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.sourceless.any"));
        } else if (category == null) {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.sourceless.sound", sound));
        } else if (sound == null) {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.source.any", category.getName()));
        } else {
            source.sendFeedback(Component.translatable("commands.cstopsound.success.source.sound", sound, category.getName()));
        }
        return Command.SINGLE_SUCCESS;
    }

}
