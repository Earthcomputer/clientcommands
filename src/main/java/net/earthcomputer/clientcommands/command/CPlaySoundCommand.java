package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.xpple.clientarguments.arguments.CSuggestionProviders;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import static com.mojang.brigadier.arguments.FloatArgumentType.*;
import static dev.xpple.clientarguments.arguments.CIdentifierArgumentType.*;
import static dev.xpple.clientarguments.arguments.CVec3ArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class CPlaySoundCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var builder = argument("sound", identifier())
            .suggests(CSuggestionProviders.AVAILABLE_SOUNDS);

        for (SoundCategory category : SoundCategory.values()) {
            builder.then(buildArguments(category));
        }

        dispatcher.register(literal("cplaysound").then(builder));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildArguments(SoundCategory category) {
        return literal(category.getName())
            .executes(ctx -> playSound(ctx.getSource(), getCIdentifier(ctx, "sound"), category, ctx.getSource().getPlayer().getPos(), 1, 1))
            .then(argument("pos", vec3())
                .executes(ctx -> playSound(ctx.getSource(), getCIdentifier(ctx, "sound"), category, getCVec3(ctx, "pos"), 1, 1))
                .then(argument("volume", floatArg(0))
                    .executes(ctx -> playSound(ctx.getSource(), getCIdentifier(ctx, "sound"), category, getCVec3(ctx, "pos"), getFloat(ctx, "volume"), 1))
                    .then(argument("pitch", floatArg(0, 2)))
                        .executes(ctx -> playSound(ctx.getSource(), getCIdentifier(ctx, "sound"), category, getCVec3(ctx, "pos"), getFloat(ctx, "volume"), getFloat(ctx, "pitch")))));
    }

    private static int playSound(FabricClientCommandSource source, Identifier sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        SoundInstance soundInstance = new PositionedSoundInstance(sound, category, volume, pitch, false, 0, SoundInstance.AttenuationType.LINEAR, pos.getX(), pos.getY(), pos.getZ(), false);
        source.getClient().getSoundManager().play(soundInstance);

        source.sendFeedback(new TranslatableText("commands.cplaysound.success", sound));

        return 0;
    }

}
