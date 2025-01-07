package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xpple.clientarguments.arguments.CItemPredicateArgument;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ClientItemPredicateArgument implements ArgumentType<ClientItemPredicateArgument.ClientItemPredicate> {
    private static final DynamicCommandExceptionType INVALID_ITEM_ID_EXCEPTION = new DynamicCommandExceptionType(object -> Component.translatableEscape("argument.item.id.invalid", object));
    private static final DynamicCommandExceptionType UNKNOWN_ITEM_TAG_EXCEPTION = new DynamicCommandExceptionType(object -> Component.translatableEscape("arguments.item.tag.unknown", object));

    private final CItemPredicateArgument delegate;
    private final HolderLookup<Item> holderLookup;

    private ClientItemPredicateArgument(CommandBuildContext context) {
        this.delegate = CItemPredicateArgument.itemPredicate(context);
        holderLookup = context.lookupOrThrow(Registries.ITEM);
    }

    /**
     * @deprecated Use {@link #clientItemPredicate(CommandBuildContext)} instead
     */
    @Deprecated
    public static ItemPredicateArgument itemPredicate(CommandBuildContext context) {
        return ItemPredicateArgument.itemPredicate(context);
    }

    public static ClientItemPredicateArgument clientItemPredicate(CommandBuildContext context) {
        return new ClientItemPredicateArgument(context);
    }

    public static ClientItemPredicate getClientItemPredicate(CommandContext<FabricClientCommandSource> ctx, String name) {
        return ctx.getArgument(name, ClientItemPredicate.class);
    }

    @Override
    public ClientItemPredicate parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
            reader.skipWhitespace();
            int tagStart = reader.getCursor();
            ResourceLocation tagId = ResourceLocation.read(reader);
            reader.setCursor(start);
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            HolderSet<Item> tag = holderLookup.get(tagKey).orElseThrow(() -> {
                reader.setCursor(tagStart);
                return UNKNOWN_ITEM_TAG_EXCEPTION.createWithContext(reader, tagId);
            });
            return new TagPredicate(tag, delegate.parse(reader));
        } else {
            ResourceLocation itemId = ResourceLocation.read(reader);
            reader.setCursor(start);
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
            Holder<Item> item = holderLookup.get(itemKey).orElseThrow(() -> INVALID_ITEM_ID_EXCEPTION.createWithContext(reader, itemId));
            return new ItemPredicate(item, delegate.parse(reader));
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return delegate.listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return delegate.getExamples();
    }

    public static abstract sealed class ClientItemPredicate implements Predicate<ItemStack> {
        private final Predicate<ItemStack> delegate;

        protected ClientItemPredicate(Predicate<ItemStack> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean test(ItemStack stack) {
            return delegate.test(stack);
        }

        public abstract Collection<Item> getPossibleItems();
    }

    static final class TagPredicate extends ClientItemPredicate {
        private final HolderSet<Item> tag;

        TagPredicate(HolderSet<Item> tag, Predicate<ItemStack> delegate) {
            super(delegate);
            this.tag = tag;
        }

        @Override
        public Collection<Item> getPossibleItems() {
            return tag.stream().map(Holder::value).toList();
        }
    }

    static final class ItemPredicate extends ClientItemPredicate {
        private final Holder<Item> item;

        ItemPredicate(Holder<Item> item, Predicate<ItemStack> delegate) {
            super(delegate);
            this.item = item;
        }

        @Override
        public Collection<Item> getPossibleItems() {
            return Collections.singletonList(item.value());
        }
    }

    public static final class EnchantedItemPredicate extends ClientItemPredicate {
        public final ItemAndEnchantmentsPredicateArgument.ItemAndEnchantmentsPredicate predicate;

        public EnchantedItemPredicate(ItemAndEnchantmentsPredicateArgument.ItemAndEnchantmentsPredicate predicate) {
            super(predicate);
            this.predicate = predicate;
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
    }
}
