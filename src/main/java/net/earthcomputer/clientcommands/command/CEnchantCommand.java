package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgument.*;
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
        return !ench.isTreasureOnly() && ench.isDiscoverable() && (item == Items.BOOK || ench.canEnchant(new ItemStack(item)));
    }

    private static int cenchant(FabricClientCommandSource source, ItemAndEnchantmentsPredicate itemAndEnchantmentsPredicate) throws CommandSyntaxException {
        if (!Configs.getEnchantingPrediction()) {
            Component component = Component.translatable("commands.cenchant.needEnchantingPrediction")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.enable", "/cconfig clientcommands enchantingPrediction set true"));
            source.sendFeedback(component);
            return Command.SINGLE_SUCCESS;
        }
        if (!Configs.playerCrackState.knowsSeed() && Configs.enchCrackState != EnchantmentCracker.CrackState.CRACKED) {
            Component component = Component.translatable("commands.cenchant.uncracked")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.crack", "/ccrackrng"));
            source.sendFeedback(component);
            return Command.SINGLE_SUCCESS;
        }

        boolean simulate = getFlag(source, FLAG_SIMULATE);

        String taskName = EnchantmentCracker.manipulateEnchantments(
            itemAndEnchantmentsPredicate.item(),
            itemAndEnchantmentsPredicate.predicate(),
            simulate,
            result -> {
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
                        source.sendFeedback(Component.translatable("enchCrack.insn.itemThrows", result.itemThrows(), (float)result.itemThrows() / (Configs.itemThrowsPerTick * 20)));
                    }
                    source.sendFeedback(Component.translatable("enchCrack.insn.bookshelves", result.bookshelves()));
                    source.sendFeedback(Component.translatable("enchCrack.insn.slot", result.slot() + 1));
                    source.sendFeedback(Component.translatable("enchCrack.insn.enchantments"));
                    for (EnchantmentInstance ench : result.enchantments()) {
                        source.sendFeedback(Component.literal("- ").append(ench.enchantment.getFullname(ench.level)));
                    }
                }
            }
        );

        source.sendFeedback(Component.translatable("commands.cenchant.success")
            .append(" ")
            .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

        return Command.SINGLE_SUCCESS;
    }

}
