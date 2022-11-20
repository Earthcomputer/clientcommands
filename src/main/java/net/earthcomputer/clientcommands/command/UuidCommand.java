package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

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
        Text uuidText = Text.literal(uuid).styled(style -> style
            .withUnderline(true)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.copy.click")))
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid))
        );

        ClientPlayNetworkHandler networkHandler = source.getClient().getNetworkHandler();
        assert networkHandler != null;

        PlayerListEntry player = networkHandler.getPlayerListEntry(entity);
        if (player == null) {
            source.sendFeedback(Text.translatable("commands.cuuid.success.nameless", uuidText));
            return Command.SINGLE_SUCCESS;
        }
        String name = player.getProfile().getName();
        source.sendFeedback(Text.translatable("commands.cuuid.success", name, uuidText));
        return Command.SINGLE_SUCCESS;
    }
}
