package net.earthcomputer.clientcommands.command;

import static com.mojang.brigadier.arguments.FloatArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.IdentifierArgumentType.*;
import static net.minecraft.command.argument.Vec3ArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class CPlaySoundCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cplaysound");

        var builder = argument("sound", identifier())
            .suggests(SuggestionProviders.AVAILABLE_SOUNDS);

        for (SoundCategory category : SoundCategory.values()) {
            builder.then(buildArguments(category));
        }

        dispatcher.register(literal("cplaysound").then(builder));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildArguments(SoundCategory category) {
        return literal(category.getName())
            .executes(ctx -> playSound(ctx.getSource(), getIdentifier(ctx, "sound"), category, MinecraftClient.getInstance().player.getPos(), 1, 1))
            .then(argument("pos", vec3())
                .executes(ctx -> playSound(ctx.getSource(), getIdentifier(ctx, "sound"), category, getVec3(ctx, "pos"), 1, 1))
                .then(argument("volume", floatArg(0))
                    .executes(ctx -> playSound(ctx.getSource(), getIdentifier(ctx, "sound"), category, getVec3(ctx, "pos"), getFloat(ctx, "volume"), 1))
                    .then(argument("pitch", floatArg(0, 2)))
                        .executes(ctx -> playSound(ctx.getSource(), getIdentifier(ctx, "sound"), category, getVec3(ctx, "pos"), getFloat(ctx, "volume"), getFloat(ctx, "pitch")))));
    }

    private static int playSound(ServerCommandSource source, Identifier sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        SoundInstance soundInstance = new PositionedSoundInstance(sound, category, volume, pitch, false, 0, SoundInstance.AttenuationType.LINEAR, pos.getX(), pos.getY(), pos.getZ(), false);
        MinecraftClient.getInstance().getSoundManager().play(soundInstance);

        sendFeedback(new TranslatableText("commands.cplaysound.success", sound));

        return 0;
    }

}
