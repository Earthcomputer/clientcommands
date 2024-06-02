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
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.concurrent.CompletableFuture;

public class ProfessionArgument implements ArgumentType<VillagerProfession> {
    public VillagerProfession lastParsed = null;

    @Override
    public VillagerProfession parse(StringReader reader) throws CommandSyntaxException {
        return lastParsed = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.read(reader));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        SharedSuggestionProvider.suggestResource(BuiltInRegistries.VILLAGER_PROFESSION.keySet(), builder);

        return builder.buildFuture();
    }

    public static ProfessionArgument profession() {
        return new ProfessionArgument();
    }


    public static <S> VillagerProfession getProfession(CommandContext<S> context, String name) {
        return context.getArgument(name, VillagerProfession.class);
    }
}
