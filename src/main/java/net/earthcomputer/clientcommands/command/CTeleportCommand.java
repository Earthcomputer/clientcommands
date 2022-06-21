package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.text.Text;

import java.util.UUID;

import static net.earthcomputer.clientcommands.command.arguments.EntityUUIDArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTeleportCommand {
    private static final SimpleCommandExceptionType NOT_SPECTATOR_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.ctp.notSpectator"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctp")
                .then(argument("entity", entityUuid())
                        .executes(ctx -> teleport(ctx.getSource(), getEntityUuid(ctx, "entity")))));
    }

    private static int teleport(FabricClientCommandSource source, UUID uuid) throws CommandSyntaxException {
        if (!source.getPlayer().isSpectator()) {
            throw NOT_SPECTATOR_EXCEPTION.create();
        }

        ClientPlayNetworkHandler networkHandler = source.getClient().getNetworkHandler();
        assert networkHandler != null;

        networkHandler.sendPacket(new SpectatorTeleportC2SPacket(uuid));

        PlayerListEntry playerListEntry = networkHandler.getPlayerListEntry(uuid);
        String name;
        if (playerListEntry != null) {
            name = playerListEntry.getProfile().getName();
        } else {
            name = uuid.toString();
        }

        source.sendFeedback(Text.translatable("commands.ctp.success", name));
        return Command.SINGLE_SUCCESS;
    }
}
