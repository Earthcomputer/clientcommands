package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.ItemAndEnchantmentsPredicate;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.getItemAndEnchantmentsPredicate;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgumentType.itemAndEnchantmentsPredicate;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CEnchantCommand {

    private static final Flag<Boolean> FLAG_SIMULATE = Flag.ofFlag("simulate").build();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cenchant = dispatcher.register(literal("cenchant")
            .then(argument("itemAndEnchantmentsPredicate", itemAndEnchantmentsPredicate().withEnchantmentPredicate(CEnchantCommand::enchantmentPredicate).constrainMaxLevel())
                    .executes(ctx -> cenchant(ctx.getSource(), getItemAndEnchantmentsPredicate(ctx, "itemAndEnchantmentsPredicate")))));
        FLAG_SIMULATE.addToCommand(dispatcher, cenchant, ctx -> true);
    }

    private static boolean enchantmentPredicate(Item item, Enchantment ench) {
        return !ench.isTreasureOnly() && ench.isDiscoverable() && (item == Items.BOOK || ench.category.canEnchant(item));
    }

    private static int cenchant(FabricClientCommandSource source, ItemAndEnchantmentsPredicate itemAndEnchantmentsPredicate) {
        if (!Configs.getEnchantingPrediction()) {
            Component text = Component.translatable("commands.cenchant.needEnchantingPrediction")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/cconfig clientcommands enchantingPrediction set true"));
            source.sendFeedback(text);
            return Command.SINGLE_SUCCESS;
        }
        if (!Configs.playerCrackState.knowsSeed() && Configs.enchCrackState != EnchantmentCracker.CrackState.CRACKED) {
            Component text = Component.translatable("commands.cenchant.uncracked")
                    .withStyle(ChatFormatting.RED)
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
            source.sendFeedback(Component.translatable("commands.cenchant.failed"));
            if (Configs.playerCrackState != PlayerRandCracker.CrackState.CRACKED) {
                MutableComponent help = Component.translatable("commands.cenchant.help.uncrackedPlayerSeed")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.crack", "/ccrackrng"));
                sendHelp(help);
            }
        } else {
            if (result.itemThrows() < 0) {
                source.sendFeedback(Component.translatable("enchCrack.insn.itemThrows.noDummy"));
            } else {
                source.sendFeedback(Component.translatable("enchCrack.insn.itemThrows", result.itemThrows(), (float)result.itemThrows() / 20f));
            }
            source.sendFeedback(Component.translatable("enchCrack.insn.bookshelves", result.bookshelves()));
            source.sendFeedback(Component.translatable("enchCrack.insn.slot", result.slot() + 1));
            source.sendFeedback(Component.translatable("enchCrack.insn.enchantments"));
            for (EnchantmentInstance ench : result.enchantments()) {
                source.sendFeedback(Component.literal("- ").append(ench.enchantment.getFullname(ench.level)));
            }
            if (!simulate) {
                source.sendFeedback(Component.translatable("commands.cenchant.success"));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

}
