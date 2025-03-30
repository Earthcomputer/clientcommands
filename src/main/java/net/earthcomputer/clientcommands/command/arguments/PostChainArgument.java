package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PostChainArgument implements ArgumentType<ResourceLocation> {

    private static final Collection<String> EXAMPLES = Arrays.asList("invert", "minecraft:spider", "minecraft:creeper");

    private static final DynamicCommandExceptionType UNKNOWN_POST_CHAIN_EXCEPTION = new DynamicCommandExceptionType(postChain -> Component.translatable("commands.cposteffect.unknownPostEffect", postChain));

    // known working post chains, "minecraft:entity_outline" and "minecraft:transparency" also exist but do not work directly
    // see assets/minecraft/post_effect for all post chains
    // perhaps this can be extracted from Minecraft.getInstance().getShaderManager().compilationCache.configs.postChains()
    private static final Set<ResourceLocation> SUPPORTED_POST_CHAINS = Set.of(GameRenderer.BLUR_POST_CHAIN_ID, ResourceLocation.withDefaultNamespace("creeper"), ResourceLocation.withDefaultNamespace("invert"), ResourceLocation.withDefaultNamespace("spider"));

    public static PostChainArgument postChain() {
        return new PostChainArgument();
    }

    public static ResourceLocation getPostChain(final CommandContext<FabricClientCommandSource> context, final String name) {
        return context.getArgument(name, ResourceLocation.class);
    }

    @Override
    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        ResourceLocation postChainId = ResourceLocation.read(reader);
        if (!SUPPORTED_POST_CHAINS.contains(postChainId)) {
            reader.setCursor(start);
            throw UNKNOWN_POST_CHAIN_EXCEPTION.createWithContext(reader, postChainId);
        }
        return postChainId;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(SUPPORTED_POST_CHAINS, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
