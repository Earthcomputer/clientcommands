package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;

import java.util.UUID;

import static net.earthcomputer.clientcommands.command.arguments.EntityUUIDArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTeleportCommand {
    private static final SimpleCommandExceptionType NOT_SPECTATOR_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ctp.notSpectator"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctp")
                .then(argument("entity", entityUuid())
                        .executes(ctx -> teleport(ctx.getSource(), getEntityUuid(ctx, "entity")))));
    }

    private static int teleport(FabricClientCommandSource source, UUID uuid) throws CommandSyntaxException {
        if (!source.getPlayer().isSpectator()) {
            throw NOT_SPECTATOR_EXCEPTION.create();
        }

        ClientPacketListener packetListener = source.getClient().getConnection();
        assert packetListener != null;

        packetListener.send(new ServerboundTeleportToEntityPacket(uuid));

        PlayerInfo playerInfo = packetListener.getPlayerInfo(uuid);
        String name;
        if (playerInfo != null) {
            name = playerInfo.getProfile().getName();
        } else {
            name = uuid.toString();
        }

        source.sendFeedback(Component.translatable("commands.ctp.success", name));
        return Command.SINGLE_SUCCESS;
    }
}
