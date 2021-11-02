package net.earthcomputer.clientcommands.command;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.argument.BlockStateArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.block.BlockState;
import net.minecraft.server.command.*;
import net.minecraft.state.property.Property;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;

public class BlockStateCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cblockstate");

        dispatcher.register(literal("cblockstate")
            .then(argument("block", blockState())
                .executes(ctx -> showBlockState(ctx.getSource(), getBlockState(ctx, "block").getBlockState()))));
    }

    private static int showBlockState(ServerCommandSource source, BlockState state) {
        Text name = state.getBlock().getName();

        Identifier id = Registry.BLOCK.getId(state.getBlock());
        String idStr = id == null ? "Unregistered" : id.toString();

        sendFeedback(new TranslatableText("commands.cblockstate.header", name, idStr, state.getProperties().size()));

        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            sendFeedback(getPropertyLine(entry));
        }

        return 0;
    }

    private static Text getPropertyLine(Map.Entry<Property<?>, Comparable<?>> entry) {
        Property<?> property = entry.getKey();
        Comparable<?> value = entry.getValue();

        MutableText valueText = new LiteralText(Util.getValueAsString(property, value));
        if (Boolean.TRUE.equals(value)) {
            valueText.formatted(Formatting.GREEN);
        } else if (Boolean.FALSE.equals(value)) {
            valueText.formatted(Formatting.RED);
        }

        return new LiteralText("- " + property.getName() + ": ").append(valueText);
    }

}
