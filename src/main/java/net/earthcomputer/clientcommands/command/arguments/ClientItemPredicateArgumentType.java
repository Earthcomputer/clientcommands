package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.tag.TagKey;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.registry.Registry;

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

    public static ClientItemPredicate getClientItemPredicate(CommandContext<ServerCommandSource> ctx, String name) throws CommandSyntaxException {
        return ctx.getArgument(name, ClientItemPredicateArgument.class).create(ctx);
    }

    @Override
    public ClientItemPredicateArgument parse(StringReader reader) throws CommandSyntaxException {
        ItemStringReader itemReader = new ItemStringReader(reader, true).consume();
        if (itemReader.getItem() != null) {
            ItemPredicate predicate = new ItemPredicate(itemReader.getItem(), itemReader.getNbt());
            return ctx -> predicate;
        } else {
            TagKey<Item> tag = itemReader.getId();
            return ctx -> {
                if (!Registry.ITEM.containsTag(tag)) {
                    throw UNKNOWN_TAG_EXCEPTION.create(tag);
                }
                return new TagPredicate(tag, itemReader.getNbt());
            };
        }
    }

    @FunctionalInterface
    public interface ClientItemPredicateArgument extends ItemPredicateArgument {
        @Override
        ClientItemPredicate create(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException;
    }

    public interface ClientItemPredicate extends Predicate<ItemStack> {
        String getPrettyString();
    }


    record TagPredicate(TagKey<Item> tag, NbtCompound compound) implements ClientItemPredicate {
        @Override
        public boolean test(ItemStack stack) {
            return stack.isIn(this.tag) && NbtHelper.matches(this.compound, stack.getNbt(), true);
        }

        @Override
        public String getPrettyString() {
            String ret = "#" + this.tag.id();
            if (compound != null) {
                ret += compound;
            }
            return ret;
        }
    }

    record ItemPredicate(Item item, NbtCompound compound) implements ClientItemPredicate {
        @Override
        public boolean test(ItemStack stack) {
            return stack.getItem() == this.item && NbtHelper.matches(this.compound, stack.getNbt(), true);
        }

        @Override
        public String getPrettyString() {
            String ret = String.valueOf(Registry.ITEM.getId(item));
            if (compound != null) {
                ret += compound;
            }
            return ret;
        }
    }
}
