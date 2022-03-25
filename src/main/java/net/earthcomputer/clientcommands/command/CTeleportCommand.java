package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.text.TranslatableText;

import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class CTeleportCommand {

    private static final SimpleCommandExceptionType NOT_SPECTATOR_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ctp.notSpectator"));

    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctp")
                .then(argument("entity", entity())
                        .executes(ctx -> teleport(ctx.getSource(), getCEntity(ctx, "entity").getUuid()))));
    }

    private static int teleport(FabricClientCommandSource source, UUID uuid) throws CommandSyntaxException {
        if (!client.player.isSpectator()) {
            throw NOT_SPECTATOR_EXCEPTION.create();
        }
        client.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(uuid));
        sendFeedback(new TranslatableText("commands.ctp.success", uuid.toString()));
        return 0;
    }
}
