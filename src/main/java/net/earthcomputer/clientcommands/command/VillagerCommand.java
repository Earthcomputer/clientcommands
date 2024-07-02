package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import dev.xpple.clientarguments.arguments.CRangeArgument;
import net.earthcomputer.clientcommands.Configs;
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
import static dev.xpple.clientarguments.arguments.CRangeArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.ItemAndEnchantmentsPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class VillagerCommand {
    private static final SimpleCommandExceptionType NOT_A_VILLAGER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.notAVillager"));
    private static final SimpleCommandExceptionType NO_CRACKED_VILLAGER_PRESENT_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.noCrackedVillagerPresent"));
    private static final SimpleCommandExceptionType NO_PROFESSION_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.noProfession"));
    private static final SimpleCommandExceptionType NOT_LEVEL_1_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.notLevel1"));
    private static final SimpleCommandExceptionType NO_GOALS_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.listGoals.noGoals"));
    private static final SimpleCommandExceptionType ALREADY_BRUTE_FORCING_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.alreadyBruteForcing"));
    private static final Dynamic2CommandExceptionType INVALID_GOAL_INDEX_EXCEPTION = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("commands.cvillager.removeGoal.invalidIndex", a, b));
    private static final Dynamic2CommandExceptionType ITEM_QUANTITY_OUT_OF_RANGE_EXCEPTION = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("commands.cvillager.removeGoal.invalidIndex", a, b));
    private static final List<Goal> goals = new ArrayList<>();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cvillager")
            .then(literal("add-two-item-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-count", intRange())
                        .then(argument("result-item", withString(itemPredicate(context)))
                            .then(argument("result-count", intRange())
                                .executes(ctx -> addGoal(ctx.getSource(), getWithString(ctx, "first-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "first-count"), null, null, getWithString(ctx, "result-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "result-count"))))))))
            .then(literal("add-three-item-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-count", intRange())
                        .then(argument("second-item", withString(itemPredicate(context)))
                            .then(argument("second-count", intRange())
                                .then(argument("result-item", withString(itemPredicate(context)))
                                    .then(argument("result-count", intRange())
                                        .executes(ctx -> addGoal(ctx.getSource(), getWithString(ctx, "first-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "first-count"), getWithString(ctx, "second-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "second-count"), getWithString(ctx, "result-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "result-count"))))))))))
            .then(literal("add-two-item-enchanted-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-count", intRange())
                        .then(argument("result-item", withString(itemAndEnchantmentsPredicate(context).withEnchantmentPredicate((item, enchantment) -> enchantment.is(EnchantmentTags.TRADEABLE))))
                            .executes(ctx -> addGoal(ctx.getSource(), getWithString(ctx, "first-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "first-count"), null, null, getWithString(ctx, "result-item", ItemAndEnchantmentsPredicate.class), MinMaxBounds.Ints.between(1, 1)))))))
            .then(literal("add-three-item-enchanted-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-count", intRange())
                        .then(argument("second-item", withString(itemPredicate(context)))
                            .then(argument("second-count", intRange())
                                .then(argument("result-item", withString(itemAndEnchantmentsPredicate(context).withEnchantmentPredicate((item, enchantment) -> enchantment.is(EnchantmentTags.TRADEABLE))))
                                    .executes(ctx -> addGoal(ctx.getSource(), getWithString(ctx, "first-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "first-count"), getWithString(ctx, "second-item", CItemStackPredicateArgument.class), CRangeArgument.Ints.getRangeArgument(ctx, "second-count"), getWithString(ctx, "result-item", ItemAndEnchantmentsPredicate.class), MinMaxBounds.Ints.between(1, 1)))))))))
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

    private static int addGoal(FabricClientCommandSource ctx, Result<? extends Predicate<ItemStack>> first, MinMaxBounds.Ints firstCount, @Nullable Result<? extends Predicate<ItemStack>> second, @Nullable MinMaxBounds.Ints secondCount, Result<? extends Predicate<ItemStack>> result, MinMaxBounds.Ints resultCount) throws CommandSyntaxException {
        HolderLookup.Provider registries = ctx.getWorld().registryAccess();
        String firstString = displayPredicate(first, firstCount, registries);
        String secondString = second == null || secondCount == null ? null : displayPredicate(second, secondCount, registries);
        String resultString = displayPredicate(result, resultCount, registries);

        goals.add(new Goal(
            firstString,
            item -> first.value().test(item) && firstCount.matches(item.getCount()),

            secondString,
            second == null || secondCount == null ? null : item -> second.value().test(item) && secondCount.matches(item.getCount()),

            resultString,
            item -> result.value().test(item) && resultCount.matches(item.getCount())));

        ctx.sendFeedback(Component.translatable("commands.cvillager.goalAdded"));
        return Command.SINGLE_SUCCESS;
    }

    private static int listGoals(FabricClientCommandSource source) {
        if (goals.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cvillager.listGoals.noGoals").withStyle(style -> style.withColor(ChatFormatting.RED)));
        } else {
            if (goals.size() == 1) {
                source.sendFeedback(Component.translatable("commands.cvillager.listGoals.success.one"));
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
            throw INVALID_GOAL_INDEX_EXCEPTION.create(index + 1, goals.size());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setVillagerTarget(@Nullable Entity target) throws CommandSyntaxException {
        if (target instanceof Villager villager) {
            VillagerCracker.setTargetVillager(villager);
            ClientCommandHelper.sendFeedback("commands.cvillager.target.set");
        } else if (target == null) {
            VillagerCracker.setTargetVillager(null);
            ClientCommandHelper.sendFeedback("commands.cvillager.target.cleared");
        } else {
            throw NOT_A_VILLAGER_EXCEPTION.create();
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
            throw NO_GOALS_EXCEPTION.create();
        }

        if (!(targetVillager instanceof IVillager iVillager) || !iVillager.clientcommands_getVillagerRngSimulator().getCrackedState().isCracked()) {
            throw NO_CRACKED_VILLAGER_PRESENT_EXCEPTION.create();
        }

        VillagerProfession profession = targetVillager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE) {
            throw NO_PROFESSION_EXCEPTION.create();
        }

        if (iVillager.clientcommands_getVillagerRngSimulator().isCracking()) {
            throw ALREADY_BRUTE_FORCING_EXCEPTION.create();
        }

        int currentLevel = targetVillager.getVillagerData().getLevel();
        if (!levelUp && currentLevel != 1) {
            throw NOT_LEVEL_1_EXCEPTION.create();
        }

        int crackedLevel = levelUp ? currentLevel + 1 : currentLevel;

        VillagerTrades.ItemListing[] listings = VillagerTrades.TRADES.get(profession).getOrDefault(crackedLevel, new VillagerTrades.ItemListing[0]);
        int adjustmentTicks = 1 + (levelUp ? -40 : 0);
        Pair<Integer, Offer> pair = iVillager.clientcommands_getVillagerRngSimulator().bruteForceOffers(listings, levelUp ? 240 : 10, Configs.maxVillagerBruteForceSimulationCalls, offer -> VillagerCommand.goals.stream().anyMatch(goal -> goal.matches(offer))).mapFirst(x -> x + adjustmentTicks * 2);
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
            iVillager.clientcommands_getVillagerRngSimulator().setCallsUntilToggleGui(calls);
        }

        return Command.SINGLE_SUCCESS;
    }

    public record Goal(String firstString, Predicate<ItemStack> first, @Nullable String secondString, @Nullable Predicate<ItemStack> second, String resultString, Predicate<ItemStack> result) {
        public boolean matches(Offer offer) {
            return first.test(offer.first)
                && ((second == null && offer.second == null) || offer.second != null && second != null && second.test(offer.second))
                && result.test(offer.result);
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

    private static String displayPredicate(Result<? extends Predicate<ItemStack>> item, MinMaxBounds.Ints count, HolderLookup.Provider registries) throws CommandSyntaxException {
        String name;
        int maxCount = 64;
        if (item.value() instanceof ItemAndEnchantmentsPredicate itemAndEnchantmentsPredicate) {
            name = item.value().toString();
            maxCount = itemAndEnchantmentsPredicate.item().getDefaultMaxStackSize();
        } else {
            try {
                ItemParser.ItemResult firstItemResult = new ItemParser(registries).parse(new StringReader(item.string()));
                name = displayText(new ItemStack(firstItemResult.item(), 1, firstItemResult.components()), true);
                maxCount = firstItemResult.item().value().getDefaultMaxStackSize();
            } catch (CommandSyntaxException e) {
                name = item.string();
            }
        }

        @Nullable
        String rangeString = displayRange(maxCount, count);
        if (rangeString == null) {
            throw ITEM_QUANTITY_OUT_OF_RANGE_EXCEPTION.create(count.min().map(Object::toString).orElse("") + ".." + count.max().map(Object::toString).orElse(""), maxCount);
        }

        return rangeString + name;
    }

    @Nullable
    public static String displayRange(int maxCount, MinMaxBounds.Ints range) {
        if (range.max().isPresent() && range.max().get() > maxCount || range.min().isPresent() && range.min().get() > maxCount) {
            return null;
        }

        if (maxCount == 1) {
            return "";
        }

        String string = "";
        if ((range.min().isEmpty() || range.min().get() == 1) && (range.max().isPresent() && range.max().get() == maxCount)) {
            string = "*";
        } else if (range.min().equals(range.max()) && range.min().isPresent()) {
            string = range.min().get().toString();
        } else {
            if (range.min().isPresent()) {
                string = string + range.min().get();
            }
            if (!string.equals(" ") || range.max().isPresent()) {
                string = string + "..";
            }
            if (range.max().isPresent()) {
                string = string + range.max().get();
            }
        }

        return string;
    }

    public static String displayText(ItemStack stack, boolean hideCount) {
        String quantityPrefix = hideCount || stack.getCount() == 1 ? "" : stack.getCount() + " ";
        List<Component> lines = stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
        String itemDescription = lines.stream().skip(1).map(Component::getString).collect(Collectors.joining(", "));
        if (lines.size() == 1) {
            return quantityPrefix + lines.getFirst().getString();
        } else {
            return quantityPrefix + lines.getFirst().getString() + " (" + itemDescription + ")";
        }
    }
}
