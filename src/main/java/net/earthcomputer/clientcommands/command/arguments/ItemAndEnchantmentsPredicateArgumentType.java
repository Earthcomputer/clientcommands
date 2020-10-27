package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemEnchantmentArgumentType;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ItemAndEnchantmentsPredicateArgumentType implements ArgumentType<ItemAndEnchantmentsPredicateArgumentType.ItemAndEnchantmentsPredicate> {

    private static final Collection<String> EXAMPLES = Arrays.asList("stick with sharpness 4 without sweeping *", "minecraft:diamond_sword with sharpness *");

    private static final SimpleCommandExceptionType EXPECTED_WITH_WITHOUT_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cenchant.expectedWithWithout"));
    private static final SimpleCommandExceptionType INCOMPATIBLE_ENCHANTMENT_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cenchant.incompatible"));

    private ItemAndEnchantmentsPredicateArgumentType() {}

    public static ItemAndEnchantmentsPredicateArgumentType itemAndEnchantmentsPredicate() {
        return new ItemAndEnchantmentsPredicateArgumentType();
    }

    public static ItemAndEnchantmentsPredicate getItemAndEnchantmentsPredicate(CommandContext<?> context, String name) {
        return context.getArgument(name, ItemAndEnchantmentsPredicate.class);
    }

    @Override
    public ItemAndEnchantmentsPredicate parse(StringReader reader) throws CommandSyntaxException {
        Parser parser = new Parser(reader);
        parser.parse();

        Predicate<List<EnchantmentLevelEntry>> predicate = enchantments -> {
            for (EnchantmentLevelEntry with : parser.with) {
                boolean found = false;
                for (EnchantmentLevelEntry ench : enchantments) {
                    if (with.enchantment == ench.enchantment && (with.level == -1 || with.level == ench.level)) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    return false;
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

        return new ItemAndEnchantmentsPredicate(parser.item, predicate);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());

        Parser parser = new Parser(reader);
        try {
            parser.parse();
        } catch (CommandSyntaxException ignore) {}

        if (parser.suggestor != null) {
            parser.suggestor.accept(builder);
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class ItemAndEnchantmentsPredicate {
        public final Item item;
        public final Predicate<List<EnchantmentLevelEntry>> predicate;

        public ItemAndEnchantmentsPredicate(Item item, Predicate<List<EnchantmentLevelEntry>> predicate) {
            this.item = item;
            this.predicate = predicate;
        }
    }

    private static class Parser {
        private final StringReader reader;
        private Consumer<SuggestionsBuilder> suggestor;

        private Item item;
        private List<EnchantmentLevelEntry> with = new ArrayList<>();
        private List<EnchantmentLevelEntry> without = new ArrayList<>();

        public Parser(StringReader reader) {
            this.reader = reader;
        }

        public List<EnchantmentLevelEntry> getWith() {
            return with;
        }

        public List<EnchantmentLevelEntry> getWithout() {
            return without;
        }

        public void parse() throws CommandSyntaxException {
            this.item = parseItem();

            while (reader.canRead()) {
                parseSpace();
                parseInfoEnchantment();
            }
        }

        private Item parseItem() throws CommandSyntaxException {
            suggestEnchantableItem();
            int start = reader.getCursor();
            Identifier identifier = Identifier.fromCommandInput(reader);
            Item item = Registry.ITEM.getOrEmpty(identifier).orElseThrow(() -> {
                reader.setCursor(start);
                return ItemStringReader.ID_INVALID_EXCEPTION.createWithContext(reader, identifier);
            });
            if (item.getEnchantability() <= 0) {
                reader.setCursor(start);
                throw INCOMPATIBLE_ENCHANTMENT_EXCEPTION.createWithContext(reader);
            }
            return item;
        }

        private void parseInfoEnchantment() throws CommandSyntaxException {
            ItemStack stack = new ItemStack(item);

            boolean negative = parseWithWithout();
            boolean suggest = reader.canRead();
            parseSpace();
            Enchantment enchantment = parseEnchantment(suggest, negative, stack);
            suggest = reader.canRead();
            parseSpace();
            int level = parseEnchantmentLevel(suggest, negative, stack, enchantment);

            if (negative)
                without.add(new EnchantmentLevelEntry(enchantment, level));
            else
                with.add(new EnchantmentLevelEntry(enchantment, level));
        }

        private boolean parseWithWithout() throws CommandSyntaxException {
            suggestWithWithout();
            int start = reader.getCursor();
            String option = reader.readUnquotedString();
            if ("with".equals(option)) {
                return false;
            } else if ("without".equals(option)) {
                return true;
            } else {
                reader.setCursor(start);
                throw EXPECTED_WITH_WITHOUT_EXCEPTION.createWithContext(reader);
            }
        }

        private Enchantment parseEnchantment(boolean suggest, boolean negative, ItemStack stack) throws CommandSyntaxException {
            List<Enchantment> allowedEnchantments = new ArrayList<>();
            for (Enchantment ench : Registry.ENCHANTMENT) {
                boolean allowed = (ench.isAcceptableItem(stack) || stack.getItem() == Items.BOOK) && !ench.isTreasure();
                if (negative) {
                    for (EnchantmentLevelEntry ench2 : with)
                        if (ench2.enchantment == ench && ench2.level == -1)
                            allowed = false;
                    for (EnchantmentLevelEntry ench2 : without)
                        if (ench2.enchantment == ench && ench2.level == -1)
                            allowed = false;
                } else {
                    for (EnchantmentLevelEntry ench2 : with)
                        if (ench2.enchantment == ench || !ench2.enchantment.canCombine(ench)) // "different" = compatible
                            allowed = false;
                    for (EnchantmentLevelEntry ench2 : without)
                        if (ench2.enchantment == ench && ench2.level == -1)
                            allowed = false;
                }
                if (allowed)
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
                return ItemEnchantmentArgumentType.UNKNOWN_ENCHANTMENT_EXCEPTION.createWithContext(reader, identifier);
            });

            if (!enchantment.isAcceptableItem(stack) && stack.getItem() != Items.BOOK) {
                reader.setCursor(start);
                throw INCOMPATIBLE_ENCHANTMENT_EXCEPTION.createWithContext(reader);
            }

            return enchantment;
        }

        private int parseEnchantmentLevel(boolean suggest, boolean negative, ItemStack stack, Enchantment enchantment) throws CommandSyntaxException {
            int maxLevel;
            {
                int enchantability = stack.getItem().getEnchantability();
                int level = 30 + 1 + enchantability / 4 + enchantability / 4;
                level += Math.round(level * 0.15f);
                for (maxLevel = enchantment.getMaxLevel(); maxLevel >= 1; maxLevel--) {
                    if (level >= enchantment.getMinPower(maxLevel))
                        break;
                }
            }

            List<Integer> allowedLevels = new ArrayList<>();
            for (int level = -1; level <= maxLevel; level++) {
                if (level == 0) continue;

                boolean allowed = true;

                if (negative) {
                    for (EnchantmentLevelEntry ench : with) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level))
                            allowed = false;
                    }
                    for (EnchantmentLevelEntry ench : without) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level))
                            allowed = false;
                    }
                } else {
                    for (EnchantmentLevelEntry ench : without) {
                        if (ench.enchantment == enchantment && (level == -1 || level == ench.level))
                            allowed = false;
                    }
                }
                if (allowed)
                    allowedLevels.add(level);
            }

            int start = reader.getCursor();

            if (suggest) {
                suggestor = suggestions -> {
                    SuggestionsBuilder builder = suggestions.createOffset(start);
                    for (int allowed : allowedLevels) {
                        if (allowed == -1)
                            builder.suggest("*");
                        else
                            builder.suggest(allowed);
                    }
                    suggestions.add(builder);
                };
            }

            if (!reader.canRead())
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader);

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
                if (reader.peek() != CommandDispatcher.ARGUMENT_SEPARATOR_CHAR)
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherExpectedArgumentSeparator().createWithContext(reader);
                reader.skip();
            }
        }

        private void suggestEnchantableItem() {
            List<Identifier> allowed = new ArrayList<>();
            for (Item item : Registry.ITEM) {
                if (item.getEnchantability() > 0)
                    allowed.add(Registry.ITEM.getId(item));
            }
            int start = reader.getCursor();
            suggestor = suggestions -> {
                SuggestionsBuilder builder = suggestions.createOffset(start);
                CommandSource.suggestIdentifiers(allowed, builder);
                suggestions.add(builder);
            };
        }

        private void suggestWithWithout() {
            int start = reader.getCursor();
            suggestor = suggestions -> {
                SuggestionsBuilder builder = suggestions.createOffset(start);
                CommandSource.suggestMatching(new String[] {"with", "without"}, builder);
                suggestions.add(builder);
            };
        }
    }
}
