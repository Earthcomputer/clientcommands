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
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EnchantmentArgumentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ItemAndEnchantmentsPredicateArgumentType implements ArgumentType<ItemAndEnchantmentsPredicateArgumentType.ItemAndEnchantmentsPredicate> {

    private static final Collection<String> EXAMPLES = Arrays.asList("stick with sharpness 4 without sweeping *", "minecraft:diamond_sword with sharpness *");

    private static final SimpleCommandExceptionType EXPECTED_WITH_WITHOUT_EXACTLY_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cenchant.expectedWithWithoutExactly"));
    private static final SimpleCommandExceptionType INCOMPATIBLE_ENCHANTMENT_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cenchant.incompatible"));
    private static final DynamicCommandExceptionType ID_INVALID_EXCEPTION = new DynamicCommandExceptionType(id -> Text.translatable("argument.item.id.invalid", id));

    private Predicate<Item> itemPredicate = item -> true;
    private Predicate<Enchantment> enchantmentPredicate = ench -> true;

    private ItemAndEnchantmentsPredicateArgumentType() {
    }

    public static ItemAndEnchantmentsPredicateArgumentType itemAndEnchantmentsPredicate() {
        return new ItemAndEnchantmentsPredicateArgumentType();
    }

    public ItemAndEnchantmentsPredicateArgumentType withItemPredicate(Predicate<Item> predicate) {
        this.itemPredicate = predicate;
        return this;
    }

    public ItemAndEnchantmentsPredicateArgumentType withEnchantmentPredicate(Predicate<Enchantment> predicate) {
        this.enchantmentPredicate = predicate;
        return this;
    }

    public static ItemAndEnchantmentsPredicate getItemAndEnchantmentsPredicate(CommandContext<?> context, String name) {
        return context.getArgument(name, ItemAndEnchantmentsPredicate.class);
    }

    @Override
    public ItemAndEnchantmentsPredicate parse(StringReader reader) throws CommandSyntaxException {
        Parser parser = new Parser(reader);
        parser.parse();

        Predicate<List<EnchantmentLevelEntry>> predicate = enchantments -> {
            if (parser.exact && (parser.with.size() != enchantments.size())) {
                return false;
            }
            for (EnchantmentLevelEntry with : parser.with) {
                boolean found = false;
                for (EnchantmentLevelEntry ench : enchantments) {
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
            for (EnchantmentLevelEntry without : parser.without) {
                for (EnchantmentLevelEntry ench : enchantments) {
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

    public record ItemAndEnchantmentsPredicate(Item item, Predicate<List<EnchantmentLevelEntry>> predicate, int numEnchantments) implements Predicate<ItemStack> {
        @Override
        public boolean test(ItemStack stack) {
            if (item != stack.getItem() && (item != Items.BOOK || stack.getItem() != Items.ENCHANTED_BOOK)) {
                return false;
            }
            Map<Enchantment, Integer> enchantmentMap = EnchantmentHelper.get(stack);
            List<EnchantmentLevelEntry> enchantments = new ArrayList<>(enchantmentMap.size());
            enchantmentMap.forEach((id, lvl) -> enchantments.add(new EnchantmentLevelEntry(id, lvl)));
            return predicate.test(enchantments);
        }
    }

    private class Parser {
        private final StringReader reader;
        private Consumer<SuggestionsBuilder> suggestor;

        private Item item;
        private final List<EnchantmentLevelEntry> with = new ArrayList<>();
        private final List<EnchantmentLevelEntry> without = new ArrayList<>();

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
            Identifier identifier = Identifier.fromCommandInput(reader);
            Item item = Registry.ITEM.getOrEmpty(identifier).orElseThrow(() -> {
                reader.setCursor(start);
                return ID_INVALID_EXCEPTION.createWithContext(reader, identifier);
            });
            if ((item.getEnchantability() <= 0 || !itemPredicate.test(item)) && (item != Items.ENCHANTED_BOOK || !itemPredicate.test(Items.BOOK))) {
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
                with.add(new EnchantmentLevelEntry(enchantment, level));
            } else {
                without.add(new EnchantmentLevelEntry(enchantment, level));
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
            nextEnchantment: for (Enchantment ench : Registry.ENCHANTMENT) {
                boolean skip = (!ench.isAcceptableItem(stack) && stack.getItem() != Items.BOOK) || !enchantmentPredicate.test(ench);
                if (skip) {
                    continue;
                }
                if (option == Option.WITH) {
                    for (EnchantmentLevelEntry ench2 : with)
                        if (ench2.enchantment == ench || !ench2.enchantment.canCombine(ench)) {
                            continue nextEnchantment;
                        }
                    for (EnchantmentLevelEntry ench2 : without)
                        if (ench2.enchantment == ench && ench2.level == -1) {
                            continue nextEnchantment;
                        }
                } else {
                    for (EnchantmentLevelEntry ench2 : with)
                        if (ench2.enchantment == ench && ench2.level == -1) {
                            continue nextEnchantment;
                        }
                    for (EnchantmentLevelEntry ench2 : without)
                        if (ench2.enchantment == ench && ench2.level == -1) {
                            continue nextEnchantment;
                        }
                }
                allowedEnchantments.add(ench);
            }

            int start = reader.getCursor();
            if (suggest) {
                suggestor = suggestions -> {
                    SuggestionsBuilder builder = suggestions.createOffset(start);
                    CommandSource.suggestIdentifiers(allowedEnchantments.stream().map(Registry.ENCHANTMENT::getId), builder);
                    suggestions.add(builder);
                };
            }

            Identifier identifier = Identifier.fromCommandInput(reader);
            Enchantment enchantment = Registry.ENCHANTMENT.getOrEmpty(identifier).orElseThrow(() -> {
                reader.setCursor(start);
                return EnchantmentArgumentType.UNKNOWN_ENCHANTMENT_EXCEPTION.createWithContext(reader, identifier);
            });

            if (!enchantment.isAcceptableItem(stack) && stack.getItem() != Items.BOOK) {
                reader.setCursor(start);
                throw INCOMPATIBLE_ENCHANTMENT_EXCEPTION.createWithContext(reader);
            }

            return enchantment;
        }

        private int parseEnchantmentLevel(boolean suggest, Option option, ItemStack stack, Enchantment enchantment) throws CommandSyntaxException {
            int maxLevel;
            {
                int enchantability = stack.getItem().getEnchantability();
                int level = 30 + 1 + enchantability / 4 + enchantability / 4;
                level += Math.round(level * 0.15f);
                for (maxLevel = enchantment.getMaxLevel(); maxLevel >= 1; maxLevel--) {
                    if (level >= enchantment.getMinPower(maxLevel)) {
                        break;
                    }
                }
            }

            List<Integer> allowedLevels = new ArrayList<>();
            nextLevel: for (int level = -1; level <= maxLevel; level++) {
                if (level == 0) {
                    continue;
                }

                if (option == Option.WITH) {
                    for (EnchantmentLevelEntry ench : without) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level)) {
                            continue nextLevel;
                        }
                    }
                } else {
                    for (EnchantmentLevelEntry ench : with) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level)) {
                            continue nextLevel;
                        }
                    }
                    for (EnchantmentLevelEntry ench : without) {
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
            List<Identifier> allowed = new ArrayList<>();
            for (Item item : Registry.ITEM) {
                if (item.getEnchantability() > 0 && itemPredicate.test(item)) {
                    allowed.add(Registry.ITEM.getId(item));
                } else if (item == Items.ENCHANTED_BOOK && itemPredicate.test(Items.BOOK)) {
                    allowed.add(Registry.ITEM.getId(Items.ENCHANTED_BOOK));
                }
            }
            int start = reader.getCursor();
            suggestor = suggestions -> {
                SuggestionsBuilder builder = suggestions.createOffset(start);
                CommandSource.suggestIdentifiers(allowed, builder);
                suggestions.add(builder);
            };
        }

        private void suggestWithWithoutExactly() {
            int start = reader.getCursor();
            suggestor = suggestions -> {
                SuggestionsBuilder builder = suggestions.createOffset(start);
                CommandSource.suggestMatching(new String[]{"with", "without", "exactly"}, builder);
                suggestions.add(builder);
            };
        }
    }
}
