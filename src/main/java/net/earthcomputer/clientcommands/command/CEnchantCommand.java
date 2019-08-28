package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.command.CommandException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class CEnchantCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cenchant");

        dispatcher.register(literal("cenchant")
            .then(argument("itemAndEnchantmentsPredicate", itemAndEnchantmentsPredicate())
                .executes(ctx -> cenchant(ctx.getSource(), getItemAndEnchantmentsPredicate(ctx, "itemAndEnchantmentsPredicate")))));
    }

    private static int cenchant(ServerCommandSource source, ItemAndEnchantmentsPredicate itemAndEnchantmentsPredicate) throws CommandException {
        if (!TempRules.getEnchantingPrediction()) {
            Text text = new TranslatableText("commands.cenchant.needEnchantingPrediction")
                    .formatted(Formatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.cenchant.needEnchantingPrediction.enable", "/ctemprule set enchantingPrediction true"));
            sendFeedback(text);
            return 0;
        }
        EnchantmentCracker.EnchantManipulationStatus status =
                EnchantmentCracker.manipulateEnchantments(itemAndEnchantmentsPredicate.item, itemAndEnchantmentsPredicate.predicate);
        if (status != EnchantmentCracker.EnchantManipulationStatus.OK) {
            throw new CommandException(new TranslatableText(status.getTranslation()));
        } else {
            sendFeedback("commands.cenchant.success");
            return 0;
        }
    }

}
