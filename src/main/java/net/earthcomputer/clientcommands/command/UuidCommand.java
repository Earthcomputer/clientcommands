package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.text.*;

import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class UuidCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cuuid")
            .then(argument("entity", entity())
                .executes(ctx -> getUuid(ctx.getSource(), getCEntity(ctx, "entity")))));
    }

    private static int getUuid(FabricClientCommandSource source, Entity entity) {
        String uuid = entity.getUuidAsString();
        Text uuidText = Text.literal(uuid).styled(style -> style
            .withUnderline(true)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.copy.click")))
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid))
        );

        source.sendFeedback(Text.translatable("commands.cuuid.success", entity.getDisplayName(), uuidText));

        return 0;
    }

}
