package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.concurrent.CompletableFuture;

public class EnchantmentArgument implements ArgumentType<Enchantment> {
    public Enchantment lastParsed = null;

    @Override
    public Enchantment parse(StringReader reader) throws CommandSyntaxException {
        return lastParsed = BuiltInRegistries.ENCHANTMENT.get(ResourceLocation.read(reader));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENCHANTMENT.keySet(), builder);

        return builder.buildFuture();
    }

    public static EnchantmentArgument enchantment() {
        return new EnchantmentArgument();
    }


    public static <S> Enchantment getEnchantment(CommandContext<S> context, String name) {
        return context.getArgument(name, Enchantment.class);
    }
}
