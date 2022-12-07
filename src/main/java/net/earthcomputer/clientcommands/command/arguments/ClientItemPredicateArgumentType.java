package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ClientItemPredicateArgumentType implements ArgumentType<ClientItemPredicateArgumentType.ClientItemPredicate> {

    private final RegistryWrapper<Item> registryWrapper;

    private ClientItemPredicateArgumentType(CommandRegistryAccess registryAccess) {
        registryWrapper = registryAccess.createWrapper(RegistryKeys.ITEM);
    }

    /**
     * @deprecated Use {@link #clientItemPredicate(CommandRegistryAccess)} instead
     */
    @Deprecated
    public static ItemPredicateArgumentType itemPredicate(CommandRegistryAccess registryAccess) {
        return ItemPredicateArgumentType.itemPredicate(registryAccess);
    }

    public static ClientItemPredicateArgumentType clientItemPredicate(CommandRegistryAccess registryAccess) {
        return new ClientItemPredicateArgumentType(registryAccess);
    }

    public static ClientItemPredicate getClientItemPredicate(CommandContext<FabricClientCommandSource> ctx, String name) {
        return ctx.getArgument(name, ClientItemPredicate.class);
    }

    @Override
    public ClientItemPredicate parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        var result = ItemStringReader.itemOrTag(registryWrapper, reader);
        return result.map(
                itemResult -> new ItemPredicate(itemResult.item(), itemResult.nbt()),
                tagResult -> new TagPredicate(reader.getString().substring(start, reader.getCursor()), tagResult.tag(), tagResult.nbt())
        );
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return ItemStringReader.getSuggestions(registryWrapper, builder, true);
    }

    public sealed interface ClientItemPredicate extends Predicate<ItemStack> {
        String getPrettyString();
        Collection<Item> getPossibleItems();
    }

    record TagPredicate(String id, RegistryEntryList<Item> tag, NbtCompound compound) implements ClientItemPredicate {
        @Override
        public boolean test(ItemStack stack) {
            return tag.contains(stack.getRegistryEntry()) && NbtHelper.matches(this.compound, stack.getNbt(), true);
        }

        @Override
        public Collection<Item> getPossibleItems() {
            return tag.stream().map(RegistryEntry::value).toList();
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

    record ItemPredicate(RegistryEntry<Item> item, NbtCompound compound) implements ClientItemPredicate {
        @Override
        public boolean test(ItemStack stack) {
            return stack.itemMatches(item) && NbtHelper.matches(this.compound, stack.getNbt(), true);
        }

        @Override
        public Collection<Item> getPossibleItems() {
            return Collections.singletonList(item.value());
        }

        @Override
        public String getPrettyString() {
            String ret = String.valueOf(Registries.ITEM.getId(item.value()));
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

        @Override
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
