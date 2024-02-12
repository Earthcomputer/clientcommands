package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ClientItemPredicateArgumentType implements ArgumentType<ClientItemPredicateArgumentType.ClientItemPredicate> {

    private final HolderLookup<Item> holderLookup;

    private ClientItemPredicateArgumentType(CommandBuildContext context) {
        holderLookup = context.holderLookup(Registries.ITEM);
    }

    /**
     * @deprecated Use {@link #clientItemPredicate(CommandBuildContext)} instead
     */
    @Deprecated
    public static ItemPredicateArgument itemPredicate(CommandBuildContext context) {
        return ItemPredicateArgument.itemPredicate(context);
    }

    public static ClientItemPredicateArgumentType clientItemPredicate(CommandBuildContext context) {
        return new ClientItemPredicateArgumentType(context);
    }

    public static ClientItemPredicate getClientItemPredicate(CommandContext<FabricClientCommandSource> ctx, String name) {
        return ctx.getArgument(name, ClientItemPredicate.class);
    }

    @Override
    public ClientItemPredicate parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        var result = ItemParser.parseForTesting(holderLookup, reader);
        return result.map(
                itemResult -> new ItemPredicate(itemResult.item(), itemResult.nbt()),
                tagResult -> new TagPredicate(reader.getString().substring(start, reader.getCursor()), tagResult.tag(), tagResult.nbt())
        );
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return ItemParser.fillSuggestions(holderLookup, builder, true);
    }

    public sealed interface ClientItemPredicate extends Predicate<ItemStack> {
        String getPrettyString();
        Collection<Item> getPossibleItems();
    }

    record TagPredicate(String id, HolderSet<Item> tag, CompoundTag compound) implements ClientItemPredicate {
        @Override
        public boolean test(ItemStack stack) {
            return tag.contains(stack.getItemHolder()) && NbtUtils.compareNbt(this.compound, stack.getTag(), true);
        }

        @Override
        public Collection<Item> getPossibleItems() {
            return tag.stream().map(Holder::value).toList();
        }

        @Override
        public String getPrettyString() {
            String ret = "#" + id;
            if (compound != null) {
                ret += compound;
            }
            return ret;
        }
    }

    record ItemPredicate(Holder<Item> item, CompoundTag compound) implements ClientItemPredicate {
        @Override
        public boolean test(ItemStack stack) {
            return stack.is(item) && NbtUtils.compareNbt(this.compound, stack.getTag(), true);
        }

        @Override
        public Collection<Item> getPossibleItems() {
            return Collections.singletonList(item.value());
        }

        @Override
        public String getPrettyString() {
            String ret = String.valueOf(BuiltInRegistries.ITEM.getKey(item.value()));
            if (compound != null) {
                ret += compound;
            }
            return ret;
        }
    }

    public record EnchantedItemPredicate(String prettyString, ItemAndEnchantmentsPredicateArgumentType.ItemAndEnchantmentsPredicate predicate) implements ClientItemPredicate {
        @Override
        public boolean test(ItemStack stack) {
            return predicate.test(stack);
        }

        public boolean isEnchantedBook() {
            return predicate.item() == Items.ENCHANTED_BOOK || predicate.item() == Items.BOOK;
        }

        public Collection<Item> getPossibleItems() {
            if (predicate.item() == Items.BOOK || predicate.item() == Items.ENCHANTED_BOOK) {
                return Arrays.asList(Items.BOOK, Items.ENCHANTED_BOOK);
            } else {
                return Collections.singletonList(predicate.item());
            }
        }

        @Override
        public String getPrettyString() {
            return prettyString;
        }
    }
}
