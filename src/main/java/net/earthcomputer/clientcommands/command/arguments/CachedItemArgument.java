package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.command.CrackVillagerRNGCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;

public class CachedItemArgument extends ItemArgument {
    public ItemInput lastItem;

    public CachedItemArgument(CommandBuildContext context) {
        super(context);
    }

    public static CachedItemArgument item(CommandBuildContext context) {
        return new CachedItemArgument(context);
    }

    public static <S> ItemInput getItem(CommandContext<S> context, String name) {
        return context.getArgument(name, ItemInput.class);
    }

    @Override
    public ItemInput parse(StringReader reader) throws CommandSyntaxException {
        return lastItem = super.parse(reader);

    }
}
