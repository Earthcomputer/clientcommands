package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.LegacyEnchantment;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.List;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CEnchantCommand {

    private static final Flag<Boolean> FLAG_SIMULATE = Flag.ofFlag("simulate").build();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        var cenchant = dispatcher.register(literal("cenchant")
            .then(argument("itemAndEnchantmentsPredicate", itemAndEnchantmentsPredicate(context).withEnchantmentPredicate(CEnchantCommand::enchantmentPredicate).constrainMaxLevel())
                    .executes(ctx -> cenchant(ctx.getSource(), getItemAndEnchantmentsPredicate(ctx, "itemAndEnchantmentsPredicate")))));
        FLAG_SIMULATE.addToCommand(dispatcher, cenchant, ctx -> true);
    }

    private static boolean enchantmentPredicate(Item item, Holder<Enchantment> ench) {
        boolean inEnchantingTable;
        if (MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_21) {
            LegacyEnchantment legacyEnch = LegacyEnchantment.byEnchantmentKey(ench.unwrapKey().orElseThrow());
            inEnchantingTable = legacyEnch != null && legacyEnch.inEnchantmentTable();
        } else {
            inEnchantingTable = ench.is(EnchantmentTags.IN_ENCHANTING_TABLE);
        }
        return inEnchantingTable && (item == Items.BOOK || ench.value().canEnchant(new ItemStack(item)));
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
                ClientLevel level = Minecraft.getInstance().level;
                if (level == null) {
                    return;
                }

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
                    List<EnchantmentInstance> enchantments = new ArrayList<>(result.enchantments());
                    EnchantmentCracker.sortIntoTooltipOrder(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT), enchantments);
                    for (EnchantmentInstance ench : enchantments) {
                        source.sendFeedback(Component.literal("- ").append(Enchantment.getFullname(ench.enchantment, ench.level)));
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
