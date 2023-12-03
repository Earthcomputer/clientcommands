package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.ItemAndEnchantmentsPredicate;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CEnchantCommand {

    private static final Flag<Boolean> FLAG_SIMULATE = Flag.ofFlag("simulate").build();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cenchant = dispatcher.register(literal("cenchant")
            .then(argument("itemAndEnchantmentsPredicate", itemAndEnchantmentsPredicate().withEnchantmentPredicate(CEnchantCommand::enchantmentPredicate).constrainMaxLevel())
                    .executes(ctx -> cenchant(ctx.getSource(), getItemAndEnchantmentsPredicate(ctx, "itemAndEnchantmentsPredicate")))));
        FLAG_SIMULATE.addToCommand(dispatcher, cenchant, ctx -> true);
    }

    private static boolean enchantmentPredicate(Item item, Enchantment ench) {
        return !ench.isTreasure() && ench.isAvailableForRandomSelection() && (item == Items.BOOK || ench.target.isAcceptableItem(item));
    }

    private static int cenchant(FabricClientCommandSource source, ItemAndEnchantmentsPredicate itemAndEnchantmentsPredicate) {
        if (!Configs.getEnchantingPrediction()) {
            Text text = Text.translatable("commands.cenchant.needEnchantingPrediction")
                    .formatted(Formatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/cconfig clientcommands enchantingPrediction set true"));
            source.sendFeedback(text);
            return Command.SINGLE_SUCCESS;
        }
        if (!Configs.playerCrackState.knowsSeed() && Configs.enchCrackState != EnchantmentCracker.CrackState.CRACKED) {
            Text text = Text.translatable("commands.cenchant.uncracked")
                    .formatted(Formatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.crack", "/ccrackrng"));
            source.sendFeedback(text);
            return Command.SINGLE_SUCCESS;
        }

        boolean simulate = getFlag(source, FLAG_SIMULATE);

        var result = EnchantmentCracker.manipulateEnchantments(
                itemAndEnchantmentsPredicate.item(),
                itemAndEnchantmentsPredicate.predicate(),
                simulate
        );
        if (result == null) {
            source.sendFeedback(Text.translatable("commands.cenchant.failed"));
            if (Configs.playerCrackState != PlayerRandCracker.CrackState.CRACKED) {
                MutableText help = Text.translatable("commands.cenchant.help.uncrackedPlayerSeed")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.crack", "/ccrackrng"));
                sendHelp(help);
            }
        } else {
            if (result.itemThrows() < 0) {
                source.sendFeedback(Text.translatable("enchCrack.insn.itemThrows.noDummy"));
            } else {
                source.sendFeedback(Text.translatable("enchCrack.insn.itemThrows", result.itemThrows(), (float)result.itemThrows() / 20f));
            }
            source.sendFeedback(Text.translatable("enchCrack.insn.bookshelves", result.bookshelves()));
            source.sendFeedback(Text.translatable("enchCrack.insn.slot", result.slot() + 1));
            source.sendFeedback(Text.translatable("enchCrack.insn.enchantments"));
            for (EnchantmentLevelEntry ench : result.enchantments()) {
                source.sendFeedback(Text.literal("- ").append(ench.enchantment.getName(ench.level)));
            }
            if (!simulate) {
                source.sendFeedback(Text.translatable("commands.cenchant.success"));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

}
