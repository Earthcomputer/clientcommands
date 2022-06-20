package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTeleportCommand {

    private static final SimpleCommandExceptionType NOT_SPECTATOR_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.ctp.notSpectator"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctp")
                .then(argument("entity", gameProfile())
                        .executes(ctx -> teleport(ctx.getSource(), getCProfileArgument(ctx, "entity")))));
    }

    private static int teleport(FabricClientCommandSource source, Collection<GameProfile> gameProfiles) throws CommandSyntaxException {
        if (!source.getPlayer().isSpectator()) {
            throw NOT_SPECTATOR_EXCEPTION.create();
        }
        if (gameProfiles.isEmpty()) {
            throw EntityArgumentType.PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        if (gameProfiles.size() != 1) {
            throw EntityArgumentType.TOO_MANY_PLAYERS_EXCEPTION.create();
        }
        UUID uuid = gameProfiles.iterator().next().getId();

        source.getClient().getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(uuid));
        source.sendFeedback(Text.translatable("commands.ctp.success", uuid.toString()));
        return Command.SINGLE_SUCCESS;
    }
}
