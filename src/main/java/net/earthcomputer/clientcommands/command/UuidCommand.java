package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.text.*;

import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class UuidCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cuuid")
            .then(argument("entity", entity())
                .executes(ctx -> getUuid(ctx.getSource(), getCEntity(ctx, "entity")))));
    }

    private static int getUuid(FabricClientCommandSource source, Entity entity) {
        String uuid = entity.getUuidAsString();
        Text uuidText = new LiteralText(uuid).styled(style -> style
            .withUnderline(true)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslatableText("chat.copy.click")))
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid))
        );

        source.sendFeedback(new TranslatableText("commands.cuuid.success", entity.getDisplayName(), uuidText));

        return 0;
    }

}
