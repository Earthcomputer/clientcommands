package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.util.List;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.ItemStackArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class TooltipCommand {

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    private static final int FLAG_ADVANCED = 1;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ctooltip");

        var ctooltip = dispatcher.register(literal("ctooltip"));
        dispatcher.register(literal("ctooltip")
            .then(literal("--advanced")
                .redirect(ctooltip, ctx -> withFlags(ctx.getSource(), FLAG_ADVANCED, true)))
            .then(literal("held")
                .executes(ctx -> showTooltip(ctx.getSource(), CLIENT.player.getMainHandStack(), "held")))
            .then(literal("stack")
                .then(argument("stack", itemStack())
                    .executes(ctx -> showTooltip(ctx.getSource(), getItemStackArgument(ctx, "stack").createStack(1, false), "stack")))));
    }

    private static int showTooltip(ServerCommandSource source, ItemStack stack, String type) {
        sendFeedback(new TranslatableText("commands.ctooltip.header." + type));

        TooltipContext context = getFlag(source, FLAG_ADVANCED) ? TooltipContext.Default.ADVANCED : TooltipContext.Default.NORMAL;

        List<Text> tooltip = stack.getTooltip(CLIENT.player, context);
        for (Text line : tooltip) {
            sendFeedback(line);
        }

        return tooltip.size();
    }
}
