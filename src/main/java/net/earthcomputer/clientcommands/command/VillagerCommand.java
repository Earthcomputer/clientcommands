package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.arguments.PredicatedRangeArgument;
import net.earthcomputer.clientcommands.command.arguments.WithStringArgument;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.VillagerCracker;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CEntityArgument.*;
import static dev.xpple.clientarguments.arguments.CItemPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.PredicatedRangeArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.PredicatedRangeArgument.Ints.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class VillagerCommand {
    private static final SimpleCommandExceptionType NOT_A_VILLAGER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.notAVillager"));
    private static final SimpleCommandExceptionType SELECTED_ITEM_NOT_WORKSTATION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.selected_item_not_workstation"));
    private static final SimpleCommandExceptionType NO_CRACKED_VILLAGER_PRESENT = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.no_cracked_villager_present"));
    public static final List<Goal> goals = new ArrayList<>();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cvillager")
            .then(literal("add-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-item-quantity", withString(intRange(1, 64)))
                        .then(argument("result-item", withString(itemPredicate(context)))
                            .then(argument("result-item-quantity", withString(intRange(1, 64)))
                                .executes(ctx -> addGoal(getWithString(ctx, "first-item", CItemStackPredicateArgument.class), getWithString(ctx, "first-item-quantity", MinMaxBounds.Ints.class), null, null, getWithString(ctx, "result-item", CItemStackPredicateArgument.class), getWithString(ctx, "result-item-quantity", MinMaxBounds.Ints.class))))))))
            .then(literal("add-three-item-goal")
                .then(argument("first-item", withString(itemPredicate(context)))
                    .then(argument("first-item-quantity", withString(intRange(1, 64)))
                        .then(argument("second-item", withString(itemPredicate(context)))
                            .then(argument("second-item-quantity", withString(intRange(1, 64)))
                                .then(argument("result-item", withString(itemPredicate(context)))
                                    .then(argument("result-item-quantity", withString(intRange(1, 64)))
                                        .executes(ctx -> addGoal(getWithString(ctx, "first-item", CItemStackPredicateArgument.class), getWithString(ctx, "first-item-quantity", MinMaxBounds.Ints.class), getWithString(ctx, "second-item", CItemStackPredicateArgument.class), getWithString(ctx, "second-item-quantity", MinMaxBounds.Ints.class), getWithString(ctx, "result-item", CItemStackPredicateArgument.class), getWithString(ctx, "result-item-quantity", MinMaxBounds.Ints.class))))))))))
            .then(literal("list-goals")
                .executes(ctx -> listGoals(ctx.getSource())))
            .then(literal("clock")
                .then(argument("pos", blockPos())
                    .executes(ctx -> setClockBlockPos(getBlockPos(ctx, "pos")))))
            .then(literal("target")
                .executes(ctx -> setVillagerTarget(null))
                .then(argument("entity", entity())
                    .executes(ctx -> setVillagerTarget(getEntity(ctx, "entity")))))
            .then(literal("crack")
                .executes(ctx -> crack(ctx.getSource()))));
    }

    private static int setClockBlockPos(BlockPos pos) {
        VillagerCracker.clockBlockPos = pos;
        ClientCommandHelper.sendFeedback("commands.cvillager.clockSet", pos.getX(), pos.getY(), pos.getZ());
        return Command.SINGLE_SUCCESS;
    }

    private static int setVillagerTarget(@Nullable Entity target) throws CommandSyntaxException {
        if (!(target instanceof Villager) && target != null) {
            throw NOT_A_VILLAGER_EXCEPTION.create();
        }

        VillagerCracker.setTargetVillager((Villager) target);
        if (target == null) {
            ClientCommandHelper.sendFeedback("commands.cvillager.targetCleared");
        } else {
            ClientCommandHelper.sendFeedback("commands.cvillager.targetSet");
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int listGoals(FabricClientCommandSource source) {
        if (goals.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cvillager.listGoals.noGoals").withStyle(style -> style.withColor(ChatFormatting.RED)));
        } else {
            source.sendFeedback(Component.translatable("commands.cvillager.listGoals.success", FishingCracker.goals.size()));
            for (int i = 0; i < goals.size(); i++) {
                Goal goal = goals.get(i);
                if (goal.secondPredicate != null) {
                    source.sendFeedback(Component.literal(String.format("%d: %s + %s = %s", i + 1, goal.firstString, goal.secondString, goal.resultString)));
                } else {
                    source.sendFeedback(Component.literal(String.format("%d: %s = %s", i + 1, goal.firstString, goal.resultString)));
                }
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int crack(FabricClientCommandSource source) throws CommandSyntaxException {
        Villager targetVillager = VillagerCracker.getVillager();

        if (!(targetVillager instanceof IVillager iVillager) || iVillager.clientcommands_getCrackedRandom() == null) {
            throw NO_CRACKED_VILLAGER_PRESENT.create();
        }

        ItemStack selectedItem = source.getPlayer().getInventory().getSelected();
        if (selectedItem.getItem() instanceof BlockItem blockItem) {
            VillagerProfession profession = PoiTypes.forState(blockItem.getBlock().defaultBlockState())
                .flatMap(poi -> BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(p -> p.heldJobSite().test(poi)).findAny())
                .orElseThrow(SELECTED_ITEM_NOT_WORKSTATION::create);
            VillagerTrades.ItemListing[] listings = VillagerTrades.TRADES.get(profession).getOrDefault(1, new VillagerTrades.ItemListing[0]);
            int i = iVillager.clientcommands_bruteForceOffers(listings, profession, Configs.maxVillagerSimulationTicks, offer -> VillagerCommand.goals.stream().anyMatch(goal -> goal.matches(offer.first(), offer.second(), offer.result())));
            if (i == -1) {
                LogUtils.getLogger().info("Could not find any matches");
            } else {
                LogUtils.getLogger().info("Found a match at {} ticks in", i);
            }
        } else {
            throw SELECTED_ITEM_NOT_WORKSTATION.create();
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int addGoal(WithStringArgument.Result<CItemStackPredicateArgument> firstPredicate, WithStringArgument.Result<MinMaxBounds.Ints> firstItemQuantityRange, @Nullable WithStringArgument.Result<CItemStackPredicateArgument> secondPredicate, @Nullable WithStringArgument.Result<MinMaxBounds.Ints> secondItemQuantityRange, WithStringArgument.Result<CItemStackPredicateArgument> resultPredicate, WithStringArgument.Result<MinMaxBounds.Ints> resultItemQuantityRange) {
        goals.add(new Goal(
            String.format("[%s] %s", firstItemQuantityRange.string(), firstPredicate.string()),
            item -> firstPredicate.value().test(item) && firstItemQuantityRange.value().matches(item.getCount()),

            secondPredicate == null ? null : String.format("[%s] %s", secondItemQuantityRange.string(), secondPredicate.string()),
            secondPredicate == null ? null : item -> secondPredicate.value().test(item) && secondItemQuantityRange.value().matches(item.getCount()),

            String.format("[%s] %s", resultItemQuantityRange.string(), resultPredicate.string()),
            item -> resultPredicate.value().test(item) && resultItemQuantityRange.value().matches(item.getCount())));
        ClientCommandHelper.sendFeedback("commands.cvillager.goalAdded");

        return Command.SINGLE_SUCCESS;
    }

    public record Goal(String firstString, Predicate<ItemStack> firstPredicate, @Nullable String secondString, @Nullable Predicate<ItemStack> secondPredicate, String resultString, Predicate<ItemStack> resultPredicate) {
        public boolean matches(ItemStack firstItem, @Nullable ItemStack secondItem, ItemStack result) {
            return firstPredicate.test(firstItem)
                && ((secondPredicate == null && secondItem == null) || secondItem != null && secondPredicate != null && secondPredicate.test(secondItem))
                && resultPredicate.test(result);
        }
    }

    public record Offer(ItemStack first, @Nullable ItemStack second, ItemStack result) {}
}
