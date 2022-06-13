package net.earthcomputer.clientcommands.command;

import static dev.xpple.clientarguments.arguments.CBlockStateArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.state.property.Property;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;

public class BlockStateCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("cblockstate")
            .then(argument("block", blockState(registryAccess))
                .executes(ctx -> showBlockState(ctx.getSource(), getCBlockState(ctx, "block").getBlockState()))));
    }

    private static int showBlockState(FabricClientCommandSource source, BlockState state) {
        Text name = state.getBlock().getName();

        Identifier id = Registry.BLOCK.getId(state.getBlock());
        String idStr = id == null ? "Unregistered" : id.toString();

        source.sendFeedback(Text.translatable("commands.cblockstate.header", name, idStr, state.getProperties().size()));

        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            source.sendFeedback(getPropertyLine(entry));
        }

        return 0;
    }

    private static Text getPropertyLine(Map.Entry<Property<?>, Comparable<?>> entry) {
        Property<?> property = entry.getKey();
        Comparable<?> value = entry.getValue();

        MutableText valueText = Text.literal(Util.getValueAsString(property, value));
        if (value == Boolean.TRUE) {
            valueText.formatted(Formatting.GREEN);
        } else if (value == Boolean.FALSE) {
            valueText.formatted(Formatting.RED);
        }

        return Text.literal("- " + property.getName() + ": ").append(valueText);
    }

}
