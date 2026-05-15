package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class PostEffectArgument implements ArgumentType<Identifier> {

    private static final Collection<String> EXAMPLES = Arrays.asList("invert", "minecraft:spider", "clientcommands:crt");

    private static final DynamicCommandExceptionType UNKNOWN_POST_EFFECT_EXCEPTION = new DynamicCommandExceptionType(postEffect -> Component.translatable("commands.cposteffect.unknownPostEffect", postEffect));

    public static PostEffectArgument postEffect() {
        return new PostEffectArgument();
    }

    public static Identifier getPostEffect(final CommandContext<FabricClientCommandSource> context, final String name) {
        return context.getArgument(name, Identifier.class);
    }

    @Override
    public Identifier parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        Identifier postEffectId = Identifier.read(reader);

        boolean valid = getValidPostChains().anyMatch(id -> id.equals(postEffectId));
        if (!valid) {
            reader.setCursor(start);
            throw UNKNOWN_POST_EFFECT_EXCEPTION.createWithContext(reader, postEffectId);
        }
        return postEffectId;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(getValidPostChains(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private Stream<Identifier> getValidPostChains() {
        return Minecraft.getInstance().getShaderManager().compilationCache.configs.postChains().entrySet().stream()
            .filter(entry -> entry.getValue().passes().stream()
                .flatMap(PostChainConfig.Pass::referencedTargets)
                .filter(location -> !entry.getValue().internalTargets().containsKey(location))
                .allMatch(LevelTargetBundle.MAIN_TARGETS::contains))
            .map(Map.Entry::getKey);
    }
}
