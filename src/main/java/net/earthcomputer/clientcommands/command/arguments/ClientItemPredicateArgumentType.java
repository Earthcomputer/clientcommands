package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.tag.Tag;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
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
            ItemPredicate predicate = new ItemPredicate(itemReader.getItem(), itemReader.getTag());
            return ctx -> predicate;
        } else {
            Identifier tagId = itemReader.getId();
            return ctx -> {
                @SuppressWarnings("ConstantConditions") Tag<Item> tag = MinecraftClient.getInstance().getNetworkHandler().getTagManager().getItems().getTag(tagId);
                if (tag == null) {
                    throw UNKNOWN_TAG_EXCEPTION.create(tagId.toString());
                } else {
                    return new TagPredicate(tag, tagId, itemReader.getTag());
                }
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


    static class TagPredicate implements ClientItemPredicate {
        private final Tag<Item> tag;
        private final Identifier id;
        private final CompoundTag compound;

        public TagPredicate(Tag<Item> tag, Identifier id, CompoundTag compound) {
            this.tag = tag;
            this.id = id;
            this.compound = compound;
        }

        @Override
        public boolean test(ItemStack stack) {
            return this.tag.contains(stack.getItem()) && NbtHelper.matches(this.compound, stack.getTag(), true);
        }

        @Override
        public String getPrettyString() {
            String ret = "#" + this.id;
            if (compound != null) {
                ret += compound;
            }
            return ret;
        }
    }

    static class ItemPredicate implements ClientItemPredicate {
        private final Item item;
        private final CompoundTag compound;

        public ItemPredicate(Item item, CompoundTag compound) {
            this.item = item;
            this.compound = compound;
        }

        @Override
        public boolean test(ItemStack stack) {
            return stack.getItem() == this.item && NbtHelper.matches(this.compound, stack.getTag(), true);
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
