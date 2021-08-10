package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.ItemAndEnchantmentsPredicate;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.command.CommandException;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class CEnchantCommand {

    private static final int FLAG_SIMULATE = 1;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cenchant");

        var cenchant = dispatcher.register(literal("cenchant"));
        dispatcher.register(literal("cenchant")
                .then(literal("--simulate")
                        .redirect(cenchant, ctx -> withFlags(ctx.getSource(), FLAG_SIMULATE, true)))
                .then(argument("itemAndEnchantmentsPredicate", itemAndEnchantmentsPredicate().withEnchantmentPredicate(ench -> !ench.isTreasure()))
                        .executes(ctx -> cenchant(ctx.getSource(), getItemAndEnchantmentsPredicate(ctx, "itemAndEnchantmentsPredicate")))));
    }

    private static int cenchant(ServerCommandSource source, ItemAndEnchantmentsPredicate itemAndEnchantmentsPredicate) throws CommandException {
        if (!TempRules.getEnchantingPrediction()) {
            Text text = new TranslatableText("commands.cenchant.needEnchantingPrediction")
                    .formatted(Formatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/ctemprule set enchantingPrediction true"));
            sendFeedback(text);
            return 0;
        }
        if (!TempRules.playerCrackState.knowsSeed() && TempRules.enchCrackState != EnchantmentCracker.CrackState.CRACKED) {
            Text text = new TranslatableText("commands.cenchant.uncracked")
                    .formatted(Formatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.crack", "/ccrackrng"));
            sendFeedback(text);
            return 0;
        }

        boolean simulate = getFlag(source, FLAG_SIMULATE);

        var result = EnchantmentCracker.manipulateEnchantments(
                itemAndEnchantmentsPredicate.item(),
                itemAndEnchantmentsPredicate.predicate(),
                simulate
        );
        if (result == null) {
            sendFeedback("commands.cenchant.failed");
            return 0;
        } else {

            if (simulate) {
                if (result.itemThrows() < 0) {
                    sendFeedback("enchCrack.insn.itemThrows.noDummy");
                } else {
                    sendFeedback(new TranslatableText("enchCrack.insn.itemThrows", result.itemThrows(), (float)result.itemThrows() / 20f));
                }
                sendFeedback(new TranslatableText("enchCrack.insn.bookshelves", result.bookshelves()));
                sendFeedback(new TranslatableText("enchCrack.insn.slot", result.slot() + 1));
                sendFeedback("enchCrack.insn.enchantments");
                for (EnchantmentLevelEntry ench : result.enchantments()) {
                    sendFeedback(new LiteralText("- ").append(ench.enchantment.getName(ench.level)));
                }
                return 0;

            } else {
                sendFeedback("commands.cenchant.success");
                return 0;
            }
        }
    }

}
