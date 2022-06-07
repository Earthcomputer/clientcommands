package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.text.Text;

import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CTeleportCommand {

    private static final SimpleCommandExceptionType NOT_SPECTATOR_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.ctp.notSpectator"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctp")
                .then(argument("entity", entity())
                        .executes(ctx -> teleport(ctx.getSource(), getCEntity(ctx, "entity").getUuid()))));
    }

    private static int teleport(FabricClientCommandSource source, UUID uuid) throws CommandSyntaxException {
        if (!source.getPlayer().isSpectator()) {
            throw NOT_SPECTATOR_EXCEPTION.create();
        }
        source.getClient().getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(uuid));
        source.sendFeedback(Text.translatable("commands.ctp.success", uuid.toString()));
        return 0;
    }
}
