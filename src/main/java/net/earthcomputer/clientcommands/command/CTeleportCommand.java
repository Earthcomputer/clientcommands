package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import java.util.UUID;
import java.util.stream.StreamSupport;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.*;
import static net.minecraft.command.CommandSource.*;
import static net.minecraft.command.argument.UuidArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class CTeleportCommand {

    private static final SimpleCommandExceptionType NOT_SPECTATOR_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ctp.notSpectator"));

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ctp");

        dispatcher.register(literal("ctp")
                .then(argument("uuid", uuid())
                        .suggests((context, builder) -> suggestMatching(client.world.getPlayers().stream().map(Entity::getUuidAsString), builder))
                        .suggests((context, builder) -> suggestMatching(StreamSupport.stream(client.world.getEntities().spliterator(), false).map(Entity::getUuidAsString), builder))
                        .executes(ctx -> teleport(ctx.getSource(), getUuid(ctx, "uuid"))))
                .then(argument("player", entity())
                        .executes(ctx -> teleport(ctx.getSource(), getEntity(ctx, "player").getUuid()))));
    }

    private static int teleport(ServerCommandSource source, UUID uuid) throws CommandSyntaxException {
        if (!client.player.isSpectator()) {
            throw NOT_SPECTATOR_EXCEPTION.create();
        }
        client.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(uuid));
        sendFeedback(new TranslatableText("commands.ctp.success", uuid.toString()));
        return 0;
    }
}
