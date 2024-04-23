package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ItemAndEnchantmentsPredicateArgument implements ArgumentType<ItemAndEnchantmentsPredicateArgument.ItemAndEnchantmentsPredicate> {

    private static final Collection<String> EXAMPLES = Arrays.asList("stick with sharpness 4 without sweeping *", "minecraft:diamond_sword with sharpness *");

    private static final SimpleCommandExceptionType EXPECTED_WITH_WITHOUT_EXACTLY_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cenchant.expectedWithWithoutExactly"));
    private static final SimpleCommandExceptionType INCOMPATIBLE_ENCHANTMENT_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cenchant.incompatible"));
    private static final DynamicCommandExceptionType ID_INVALID_EXCEPTION = new DynamicCommandExceptionType(id -> Component.translatable("argument.item.id.invalid", id));

    private Predicate<Item> itemPredicate = item -> true;
    private BiPredicate<Item, Enchantment> enchantmentPredicate = (item, ench) -> true;
    private boolean constrainMaxLevel = false;

    private ItemAndEnchantmentsPredicateArgument() {
    }

    public static ItemAndEnchantmentsPredicateArgument itemAndEnchantmentsPredicate() {
        return new ItemAndEnchantmentsPredicateArgument();
    }

    public ItemAndEnchantmentsPredicateArgument withItemPredicate(Predicate<Item> predicate) {
        this.itemPredicate = predicate;
        return this;
    }

    public ItemAndEnchantmentsPredicateArgument withEnchantmentPredicate(BiPredicate<Item, Enchantment> predicate) {
        this.enchantmentPredicate = predicate;
        return this;
    }

    public ItemAndEnchantmentsPredicateArgument constrainMaxLevel() {
        this.constrainMaxLevel = true;
        return this;
    }

    public static ItemAndEnchantmentsPredicate getItemAndEnchantmentsPredicate(CommandContext<?> context, String name) {
        return context.getArgument(name, ItemAndEnchantmentsPredicate.class);
    }

    @Override
    public ItemAndEnchantmentsPredicate parse(StringReader reader) throws CommandSyntaxException {
        Parser parser = new Parser(reader);
        parser.parse();

        Predicate<List<EnchantmentInstance>> predicate = enchantments -> {
            if (parser.exact && (parser.with.size() != enchantments.size())) {
                return false;
            }
            for (EnchantmentInstance with : parser.with) {
                boolean found = false;
                for (EnchantmentInstance ench : enchantments) {
                    if (with.enchantment == ench.enchantment && (with.level == -1 || with.level == ench.level)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            if (parser.exact) {
                return true;
            }
            for (EnchantmentInstance without : parser.without) {
                for (EnchantmentInstance ench : enchantments) {
                    if (without.enchantment == ench.enchantment && (without.level == -1 || without.level == ench.level)) {
                        return false;
                    }
                }
            }
            return true;
        };

        return new ItemAndEnchantmentsPredicate(parser.item, predicate, parser.with.size());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());

        Parser parser = new Parser(reader);
        try {
            parser.parse();
        } catch (CommandSyntaxException ignore) {
        }

        if (parser.suggestor != null) {
            parser.suggestor.accept(builder);
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public record ItemAndEnchantmentsPredicate(Item item, Predicate<List<EnchantmentInstance>> predicate, int numEnchantments) implements Predicate<ItemStack> {
        @Override
        public boolean test(ItemStack stack) {
            if (item != stack.getItem() && (item != Items.BOOK || stack.getItem() != Items.ENCHANTED_BOOK)) {
                return false;
            }
            List<EnchantmentInstance> enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet().stream()
                .map(entry -> new EnchantmentInstance(entry.getKey().value(), entry.getIntValue()))
                .toList();
            return predicate.test(enchantments);
        }
    }

    private class Parser {
        private final StringReader reader;
        private Consumer<SuggestionsBuilder> suggestor;

        private Item item;
        private final List<EnchantmentInstance> with = new ArrayList<>();
        private final List<EnchantmentInstance> without = new ArrayList<>();

        private boolean exact = false;

        public Parser(StringReader reader) {
            this.reader = reader;
        }

        public void parse() throws CommandSyntaxException {
            this.item = parseItem();

            while (reader.canRead()) {
                parseSpace();
                parseInfoEnchantment();
                if (exact) {
                    return;
                }
            }
        }

        private Item parseItem() throws CommandSyntaxException {
            suggestEnchantableItem();
            int start = reader.getCursor();
            ResourceLocation identifier = ResourceLocation.read(reader);
            Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElseThrow(() -> {
                reader.setCursor(start);
                return ID_INVALID_EXCEPTION.createWithContext(reader, identifier);
            });
            if ((item.getEnchantmentValue() <= 0 || !itemPredicate.test(item)) && (item != Items.ENCHANTED_BOOK || !itemPredicate.test(Items.BOOK))) {
                reader.setCursor(start);
                throw INCOMPATIBLE_ENCHANTMENT_EXCEPTION.createWithContext(reader);
            }
            if (item == Items.ENCHANTED_BOOK) {
                item = Items.BOOK;
            }
            return item;
        }

        private void parseInfoEnchantment() throws CommandSyntaxException {
            ItemStack stack = new ItemStack(item);

            Option option = parseWithWithoutExactly();
            boolean suggest = reader.canRead();
            parseSpace();
            if (option == Option.EXACT) {
                exact = true;
                return;
            }
            Enchantment enchantment = parseEnchantment(suggest, option, stack);
            suggest = reader.canRead();
            parseSpace();
            int level = parseEnchantmentLevel(suggest, option, stack, enchantment);

            if (option == Option.WITH) {
                with.add(new EnchantmentInstance(enchantment, level));
            } else {
                without.add(new EnchantmentInstance(enchantment, level));
            }
        }

        private enum Option {
            WITH,
            WITHOUT,
            EXACT
        }

        private Option parseWithWithoutExactly() throws CommandSyntaxException {
            suggestWithWithoutExactly();
            int start = reader.getCursor();
            String option = reader.readUnquotedString();
            return switch (option) {
                case "with" -> Option.WITH;
                case "without" -> Option.WITHOUT;
                case "exactly" -> Option.EXACT;
                default -> {
                    reader.setCursor(start);
                    throw EXPECTED_WITH_WITHOUT_EXACTLY_EXCEPTION.createWithContext(reader);
                }
            };
        }

        private Enchantment parseEnchantment(boolean suggest, Option option, ItemStack stack) throws CommandSyntaxException {
            List<Enchantment> allowedEnchantments = new ArrayList<>();
            nextEnchantment: for (Enchantment ench : BuiltInRegistries.ENCHANTMENT) {
                boolean skip = (!ench.canEnchant(stack) && stack.getItem() != Items.BOOK) || !enchantmentPredicate.test(stack.getItem(), ench);
                if (skip) {
                    continue;
                }
                if (option == Option.WITH) {
                    for (EnchantmentInstance ench2 : with) {
                        if (ench2.enchantment == ench || !ench2.enchantment.isCompatibleWith(ench)) {
                            continue nextEnchantment;
                        }
                    }
                    for (EnchantmentInstance ench2 : without) {
                        if (ench2.enchantment == ench && ench2.level == -1) {
                            continue nextEnchantment;
                        }
                    }
                } else {
                    for (EnchantmentInstance ench2 : with) {
                        if (ench2.enchantment == ench && ench2.level == -1) {
                            continue nextEnchantment;
                        }
                    }
                    for (EnchantmentInstance ench2 : without) {
                        if (ench2.enchantment == ench && ench2.level == -1) {
                            continue nextEnchantment;
                        }
                    }
                }
                allowedEnchantments.add(ench);
            }

            int start = reader.getCursor();
            if (suggest) {
                suggestor = suggestions -> {
                    SuggestionsBuilder builder = suggestions.createOffset(start);
                    SharedSuggestionProvider.suggestResource(allowedEnchantments.stream().map(BuiltInRegistries.ENCHANTMENT::getKey), builder);
                    suggestions.add(builder);
                };
            }

            ResourceLocation identifier = ResourceLocation.read(reader);
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.getOptional(identifier).orElseThrow(() -> {
                reader.setCursor(start);
                return ResourceArgument.ERROR_UNKNOWN_RESOURCE.createWithContext(reader, identifier, Registries.ENCHANTMENT.location());
            });

            if (!enchantment.canEnchant(stack) && stack.getItem() != Items.BOOK) {
                reader.setCursor(start);
                throw INCOMPATIBLE_ENCHANTMENT_EXCEPTION.createWithContext(reader);
            }

            return enchantment;
        }

        private int parseEnchantmentLevel(boolean suggest, Option option, ItemStack stack, Enchantment enchantment) throws CommandSyntaxException {
            int maxLevel;
            if (constrainMaxLevel) {
                int enchantability = stack.getItem().getEnchantmentValue();
                int level = 30 + 1 + enchantability / 4 + enchantability / 4;
                level += Math.round(level * 0.15f);
                for (maxLevel = enchantment.getMaxLevel(); maxLevel >= 1; maxLevel--) {
                    if (level >= enchantment.getMinCost(maxLevel)) {
                        break;
                    }
                }
            } else {
                maxLevel = enchantment.getMaxLevel();
            }

            List<Integer> allowedLevels = new ArrayList<>();
            nextLevel: for (int level = -1; level <= maxLevel; level++) {
                if (level == 0) {
                    continue;
                }

                if (option == Option.WITH) {
                    for (EnchantmentInstance ench : without) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level)) {
                            continue nextLevel;
                        }
                    }
                } else {
                    for (EnchantmentInstance ench : with) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level)) {
                            continue nextLevel;
                        }
                    }
                    for (EnchantmentInstance ench : without) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level)) {
                            continue nextLevel;
                        }
                    }
                }
                allowedLevels.add(level);
            }

            int start = reader.getCursor();

            if (suggest) {
                suggestor = suggestions -> {
                    SuggestionsBuilder builder = suggestions.createOffset(start);
                    for (int allowed : allowedLevels) {
                        if (allowed == -1) {
                            builder.suggest("*");
                        }
                        else {
                            builder.suggest(allowed);
                        }
                    }
                    suggestions.add(builder);
                };
            }

            if (!reader.canRead()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader);
            }

            if (reader.peek() == '*') {
                reader.skip();
                return -1;
            }

            int level = reader.readInt();
            if (level == -1 || !allowedLevels.contains(level)) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, level);
            }
            return level;
        }

        private void parseSpace() throws CommandSyntaxException {
            if (reader.canRead()) {
                if (reader.peek() != CommandDispatcher.ARGUMENT_SEPARATOR_CHAR) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherExpectedArgumentSeparator().createWithContext(reader);
                }
                reader.skip();
            }
        }

        private void suggestEnchantableItem() {
            List<ResourceLocation> allowed = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item.getEnchantmentValue() > 0 && itemPredicate.test(item)) {
                    if (MultiVersionCompat.INSTANCE.doesItemExist(item)) {
                        allowed.add(BuiltInRegistries.ITEM.getKey(item));
                    }
                } else if (item == Items.ENCHANTED_BOOK && itemPredicate.test(Items.BOOK)) {
                    if (MultiVersionCompat.INSTANCE.doesItemExist(item)) {
                        allowed.add(BuiltInRegistries.ITEM.getKey(Items.ENCHANTED_BOOK));
                    }
                }
            }
            int start = reader.getCursor();
            suggestor = suggestions -> {
                SuggestionsBuilder builder = suggestions.createOffset(start);
                SharedSuggestionProvider.suggestResource(allowed, builder);
                suggestions.add(builder);
            };
        }

        private void suggestWithWithoutExactly() {
            int start = reader.getCursor();
            suggestor = suggestions -> {
                SuggestionsBuilder builder = suggestions.createOffset(start);
                SharedSuggestionProvider.suggest(new String[]{"with", "without", "exactly"}, builder);
                suggestions.add(builder);
            };
        }
    }
}
