package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.UUID;

import static net.earthcomputer.clientcommands.command.arguments.EntityUUIDArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class UuidCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cuuid")
            .then(argument("entity", entityUuid())
                .executes(ctx -> getUuid(ctx.getSource(), getEntityUuid(ctx, "entity")))));
    }

    private static int getUuid(FabricClientCommandSource source, UUID entity) {
        String uuid = entity.toString();
        Component uuidText = Component.literal(uuid).withStyle(style -> style
            .withUnderlined(true)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid))
        );

        ClientPacketListener packetListener = source.getClient().getConnection();
        assert packetListener != null;

        PlayerInfo player = packetListener.getPlayerInfo(entity);
        if (player == null) {
            source.sendFeedback(Component.translatable("commands.cuuid.success.nameless", uuidText));
            return Command.SINGLE_SUCCESS;
        }
        String name = player.getProfile().getName();
        source.sendFeedback(Component.translatable("commands.cuuid.success", name, uuidText));
        return Command.SINGLE_SUCCESS;
    }
}
