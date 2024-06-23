package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.arguments.WithStringArgument;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.VillagerCracker;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CEntityArgument.*;
import static dev.xpple.clientarguments.arguments.CItemPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.PredicatedRangeArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class VillagerCommand {
    private static final SimpleCommandExceptionType NOT_A_VILLAGER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.notAVillager"));
    private static final SimpleCommandExceptionType NO_CRACKED_VILLAGER_PRESENT = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.noCrackedVillagerPresent"));
    private static final SimpleCommandExceptionType NO_PROFESSION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.noProfession"));
    private static final SimpleCommandExceptionType NOT_LEVEL_1 = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.notLevel1"));
    private static final SimpleCommandExceptionType NO_GOALS = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.listGoals.noGoals"));
    private static final SimpleCommandExceptionType ALREADY_BRUTE_FORCING = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.alreadyBruteForcing"));
    private static final Dynamic2CommandExceptionType INVALID_GOAL_INDEX = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("commands.cvillager.removeGoal.invalidIndex", a, b));
    public static final List<Goal> goals = new ArrayList<>();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cvillager")
            .then(literal("add-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-item-quantity", withString(intRange(1, 64)))
                        .then(argument("result-item", withString(itemPredicate(context)))
                            .then(argument("result-item-quantity", withString(intRange(1, 64)))
                                .executes(ctx -> addGoal(getWithString(ctx, "first-item", CItemStackPredicateArgument.class), getWithString(ctx, "first-item-quantity", MinMaxBounds.Ints.class), null, null, getWithString(ctx, "result-item", CItemStackPredicateArgument.class), getWithString(ctx, "result-item-quantity", MinMaxBounds.Ints.class))))))))
            .then(literal("add-enchanted-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-item-quantity", withString(intRange(1, 64)))
                        .then(argument("second-item", withString(itemPredicate(context)))
                            .then(argument("second-item-quantity", withString(intRange(1, 64)))
                                .then(argument("result-item", withString(itemAndEnchantmentsPredicate(context).withEnchantmentPredicate((item, enchantment) -> enchantment.is(EnchantmentTags.TRADEABLE))))
                                    .executes(ctx -> addGoal(getWithString(ctx, "first-item", CItemStackPredicateArgument.class), getWithString(ctx, "first-item-quantity", MinMaxBounds.Ints.class), getWithString(ctx, "second-item", CItemStackPredicateArgument.class), getWithString(ctx, "second-item-quantity", MinMaxBounds.Ints.class), getWithString(ctx, "result-item", ItemAndEnchantmentsPredicate.class), new WithStringArgument.Result<>("1", MinMaxBounds.Ints.between(1, 1))))))))))
            .then(literal("list-goals")
                .executes(ctx -> listGoals(ctx.getSource())))
            .then(literal("remove-goal")
                .then(argument("index", integer(1))
                    .executes(ctx -> removeGoal(ctx.getSource(), getInteger(ctx, "index")))))
            .then(literal("target")
                .executes(ctx -> setVillagerTarget(null))
                .then(argument("entity", entity())
                    .executes(ctx -> setVillagerTarget(getEntity(ctx, "entity")))))
            .then(literal("clock")
                .then(argument("pos", blockPos())
                    .executes(ctx -> setClockPos(getBlockPos(ctx, "pos")))))
            .then(literal("reset-cracker")
                .executes(ctx -> resetCracker()))
            .then(literal("brute-force")
                .executes(ctx -> bruteForce(false))
                .then(literal("first-level")
                    .executes(ctx -> bruteForce(false)))
                .then(literal("next-level")
                    .executes(ctx -> bruteForce(true)))));
    }

    private static int addGoal(Result<CItemStackPredicateArgument> firstPredicate, Result<MinMaxBounds.Ints> firstItemQuantityRange, @Nullable Result<CItemStackPredicateArgument> secondPredicate, @Nullable Result<MinMaxBounds.Ints> secondItemQuantityRange, Result<? extends Predicate<ItemStack>> resultPredicate, Result<MinMaxBounds.Ints> resultItemQuantityRange) {
        HolderLookup.Provider registries = Minecraft.getInstance().level.registryAccess();
        String firstItemString;
        try {
            ItemParser.ItemResult firstItemResult = new ItemParser(registries).parse(new StringReader(firstPredicate.string()));
            firstItemString = displayText(new ItemStack(firstItemResult.item(), 1, firstItemResult.components()), true);
        } catch (CommandSyntaxException e) {
            firstItemString = firstPredicate.string();
        }
        String secondItemString = null;
        if (secondPredicate != null) {
            try {
                ItemParser.ItemResult secondItemResult = new ItemParser(registries).parse(new StringReader(secondPredicate.string()));
                secondItemString = displayText(new ItemStack(secondItemResult.item(), 1, secondItemResult.components()), true);
            } catch (CommandSyntaxException e) {
                secondItemString = secondPredicate.string();
            }
        }
        String resultItemString;
        if (resultPredicate.value() instanceof ItemAndEnchantmentsPredicate predicate) {
            resultItemString = predicate.toString();
        } else {
            try {
                ItemParser.ItemResult resultItemResult = new ItemParser(registries).parse(new StringReader(resultPredicate.string()));
                resultItemString = displayText(new ItemStack(resultItemResult.item(), 1, resultItemResult.components()), true);
            } catch (CommandSyntaxException e) {
                resultItemString = resultPredicate.string();
            }
        }

        goals.add(new Goal(
            String.format("%sx %s", firstItemQuantityRange.string(), firstItemString),
            item -> firstPredicate.value().test(item) && firstItemQuantityRange.value().matches(item.getCount()),

            secondPredicate == null || secondItemQuantityRange == null ? null : String.format("%sx %s", secondItemQuantityRange.string(), secondItemString),
            secondPredicate == null || secondItemQuantityRange == null ? null : item -> secondPredicate.value().test(item) && secondItemQuantityRange.value().matches(item.getCount()),

            String.format("%sx %s", resultItemQuantityRange.string(), resultItemString),
            item -> resultPredicate.value().test(item) && resultItemQuantityRange.value().matches(item.getCount())));
        ClientCommandHelper.sendFeedback("commands.cvillager.goalAdded");

        return Command.SINGLE_SUCCESS;
    }

    private static int listGoals(FabricClientCommandSource source) {
        if (goals.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cvillager.listGoals.noGoals").withStyle(style -> style.withColor(ChatFormatting.RED)));
        } else {
            if (goals.size() == 1) {
                source.sendFeedback(Component.translatable("commands.cvillager.listGoal.success"));
            } else {
                source.sendFeedback(Component.translatable("commands.cvillager.listGoals.success", FishingCracker.goals.size() + 1));
            }
            for (int i = 0; i < goals.size(); i++) {
                Goal goal = goals.get(i);
                source.sendFeedback(Component.literal((i + 1) + ": " + goal.toString()));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeGoal(FabricClientCommandSource source, int index) throws CommandSyntaxException {
        index = index - 1;
        if (index < goals.size()) {
            Goal goal = goals.remove(index);
            source.sendFeedback(Component.translatable("commands.cvillager.removeGoal.success", goal.toString()));
        } else {
            throw INVALID_GOAL_INDEX.create(index + 1, goals.size());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setVillagerTarget(@Nullable Entity target) throws CommandSyntaxException {
        if (!(target instanceof Villager) && target != null) {
            throw NOT_A_VILLAGER_EXCEPTION.create();
        }

        VillagerCracker.setTargetVillager((Villager) target);
        if (target == null) {
            ClientCommandHelper.sendFeedback("commands.cvillager.target.cleared");
        } else {
            ClientCommandHelper.sendFeedback("commands.cvillager.target.set");
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int setClockPos(BlockPos pos) {
        VillagerCracker.setClockPos(pos == null ? null : new GlobalPos(Minecraft.getInstance().player.level().dimension(), pos));
        if (pos == null) {
            ClientCommandHelper.sendFeedback("commands.cvillager.clock.cleared");
        } else {
            ClientCommandHelper.sendFeedback("commands.cvillager.clock.set");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int resetCracker() {
        Villager targetVillager = VillagerCracker.getVillager();
        if (targetVillager instanceof IVillager iVillager) {
            iVillager.clientcommands_getVillagerRngSimulator().reset();
            ClientCommandHelper.sendFeedback("commands.cvillager.resetCracker");
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int bruteForce(boolean levelUp) throws CommandSyntaxException {
        Villager targetVillager = VillagerCracker.getVillager();

        if (goals.isEmpty()) {
            throw NO_GOALS.create();
        }

        if (!(targetVillager instanceof IVillager iVillager) || !iVillager.clientcommands_getVillagerRngSimulator().getCrackedState().isCracked()) {
            throw NO_CRACKED_VILLAGER_PRESENT.create();
        }

        VillagerProfession profession = targetVillager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE) {
            throw NO_PROFESSION.create();
        }

        if (iVillager.clientcommands_getVillagerRngSimulator().isCracking()) {
            throw ALREADY_BRUTE_FORCING.create();
        }

        int currentLevel = targetVillager.getVillagerData().getLevel();
        if (!levelUp && currentLevel != 1) {
            throw NOT_LEVEL_1.create();
        }

        int crackedLevel = levelUp ? currentLevel + 1 : currentLevel;

        VillagerTrades.ItemListing[] listings = VillagerTrades.TRADES.get(profession).getOrDefault(crackedLevel, new VillagerTrades.ItemListing[0]);
        int adjustmentTicks = 1 + (levelUp ? -40 : 0);
        Pair<Integer, Offer> pair = iVillager.clientcommands_getVillagerRngSimulator().bruteForceOffers(listings, levelUp ? 240 : 10, Configs.maxVillagerBruteForceSimulationCalls, offer -> VillagerCommand.goals.stream().anyMatch(goal -> goal.matches(offer.first(), offer.second(), offer.result()))).mapFirst(x -> x + adjustmentTicks * 2);
        int calls = pair.getFirst();
        Offer offer = pair.getSecond();
        if (calls < 0) {
            ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.bruteForce.failed", Configs.maxVillagerBruteForceSimulationCalls).withStyle(ChatFormatting.RED), 100);
        } else {
            String price;
            if (offer.second() == null) {
                price = displayText(offer.first(), false);
            } else {
                price = displayText(offer.first(), false) + " + " + displayText(offer.second(), false);
            }
            ClientCommandHelper.sendFeedback(Component.translatable("commands.cvillager.bruteForce.success", displayText(offer.result(), false), price, calls).withStyle(ChatFormatting.GREEN));
            VillagerCracker.targetOffer = offer;
            iVillager.clientcommands_getVillagerRngSimulator().setCallsUntilToggleGui(calls, offer.result());
        }

        return Command.SINGLE_SUCCESS;
    }

    public record Goal(String firstString, Predicate<ItemStack> firstPredicate, @Nullable String secondString, @Nullable Predicate<ItemStack> secondPredicate, String resultString, Predicate<ItemStack> resultPredicate) {
        public boolean matches(ItemStack firstItem, @Nullable ItemStack secondItem, ItemStack result) {
            return firstPredicate.test(firstItem)
                && ((secondPredicate == null && secondItem == null) || secondItem != null && secondPredicate != null && secondPredicate.test(secondItem))
                && resultPredicate.test(result);
        }

        @Override
        public String toString() {
            if (secondString == null) {
                return String.format("%s = %s", firstString, resultString);
            } else {
                return String.format("%s + %s = %s", firstString, secondString, resultString);
            }
        }
    }

    public record Offer(ItemStack first, @Nullable ItemStack second, ItemStack result) {
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Offer other)) {
                return false;
            }
            return ItemStack.isSameItemSameComponents(this.first, other.first) && this.first.getCount() == other.first.getCount()
                && (this.second == other.second || this.second != null && other.second != null && ItemStack.isSameItemSameComponents(this.second, other.second) && this.second.getCount() == other.second.getCount())
                && ItemStack.isSameItemSameComponents(this.result, other.result) && this.result.getCount() == other.result.getCount();
        }

        @Override
        public String toString() {
            if (second == null) {
                return String.format("%s = %s", displayText(first, false), displayText(result, false));
            } else {
                return String.format("%s + %s = %s", displayText(first, false), displayText(second, false), displayText(result, false));
            }
        }
    }

    public static String displayText(ItemStack stack, boolean ignoreCount) {
        String quantityPrefix;
        if (stack.getCount() == 1 || ignoreCount) {
            quantityPrefix = "";
        } else if (stack.getCount() < 64) {
            quantityPrefix = stack.getCount() + " ";
        } else if (stack.getCount() % 64 == 0) {
            quantityPrefix = stack.getCount() / 64 + " stx ";
        } else {
            quantityPrefix = stack.getCount() / 64 + " stx & " + stack.getCount() % 64 + " ";
        }
        List<Component> lines = stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
        String itemDescription = lines.stream().skip(1).map(Component::getString).collect(Collectors.joining(", "));
        if (lines.size() == 1) {
            return quantityPrefix + lines.getFirst().getString();
        } else {
            return quantityPrefix + lines.getFirst().getString() + " (" + itemDescription + ")";
        }
    }
}
