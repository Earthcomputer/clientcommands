package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.arguments.ItemPredicateArgumentType;
import net.minecraft.command.arguments.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tag.Tag;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.TagHelper;

import java.util.function.Predicate;

public class ClientItemPredicateArgumentType extends ItemPredicateArgumentType {

    private static final DynamicCommandExceptionType UNKNOWN_TAG_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("arguments.item.tag.unknown", arg));

    private ClientItemPredicateArgumentType() {}

    /**
     * @deprecated Use {@link #clientItemPredicate()} instead
     */
    @Deprecated
    public static ItemPredicateArgumentType itemPredicate() {
        return ItemPredicateArgumentType.itemPredicate();
    }

    public static ClientItemPredicateArgumentType clientItemPredicate() {
        return new ClientItemPredicateArgumentType();
    }

    @Override
    public ItemPredicateArgument method_9800(StringReader reader) throws CommandSyntaxException {
        ItemStringReader itemReader = new ItemStringReader(reader, true).consume();
        if (itemReader.getItem() != null) {
            ItemPredicate predicate = new ItemPredicate(itemReader.getItem(), itemReader.getTag());
            return ctx -> predicate;
        } else {
            Identifier tagId = itemReader.getId();
            return ctx -> {
                @SuppressWarnings("ConstantConditions") Tag<Item> tag = MinecraftClient.getInstance().getNetworkHandler().getTagManager().items().get(tagId);
                if (tag == null) {
                    throw UNKNOWN_TAG_EXCEPTION.create(tagId.toString());
                } else {
                    return new TagPredicate(tag, itemReader.getTag());
                }
            };
        }
    }



    static class TagPredicate implements Predicate<ItemStack> {
        private final Tag<Item> tag;
        private final CompoundTag compound;

        public TagPredicate(Tag<Item> tag, CompoundTag compound) {
            this.tag = tag;
            this.compound = compound;
        }

        @Override
        public boolean test(ItemStack stack) {
            return this.tag.contains(stack.getItem()) && TagHelper.areTagsEqual(this.compound, stack.getTag(), true);
        }
    }

    static class ItemPredicate implements Predicate<ItemStack> {
        private final Item item;
        private final CompoundTag compound;

        public ItemPredicate(Item item, CompoundTag compound) {
            this.item = item;
            this.compound = compound;
        }

        @Override
        public boolean test(ItemStack stack) {
            return stack.getItem() == this.item && TagHelper.areTagsEqual(this.compound, stack.getTag(), true);
        }
    }
}
