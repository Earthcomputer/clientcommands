package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.command.argument.BlockPredicateArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ClientBlockPredicateArgumentType extends BlockPredicateArgumentType {
    private static final DynamicCommandExceptionType UNKNOWN_TAG_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("arguments.block.tag.unknown", arg));

    private boolean allowNbt = true;
    private boolean allowTags = true;

    private ClientBlockPredicateArgumentType() {}

    public static ClientBlockPredicateArgumentType blockPredicate() {
        return new ClientBlockPredicateArgumentType();
    }

    public ClientBlockPredicateArgumentType disallowNbt() {
        allowNbt = false;
        return this;
    }

    public ClientBlockPredicateArgumentType disallowTags() {
        allowTags = false;
        return this;
    }

    @Override
    public BlockPredicate parse(StringReader stringReader) throws CommandSyntaxException {
        BlockArgumentParser blockParser = (new BlockArgumentParser(stringReader, allowTags)).parse(allowNbt);
        BlockPredicate predicate;
        if (blockParser.getBlockState() != null) {
            var statePredicate = new BlockPredicateArgumentType.StatePredicate(blockParser.getBlockState(), blockParser.getBlockProperties().keySet(), blockParser.getNbtData());
            predicate = tagManager -> statePredicate;
        } else {
            Identifier tagId = blockParser.getTagId();
            predicate = tagManager -> {
                Tag<Block> tag = tagManager.getTag(Registry.BLOCK_KEY, tagId, id -> UNKNOWN_TAG_EXCEPTION.create(id.toString()));
                return new BlockPredicateArgumentType.TagPredicate(tag, blockParser.getProperties(), blockParser.getNbtData());
            };
        }

        if (blockParser.getNbtData() == null) {
            // optimization: if there is no NBT data, we can cache the blockstate results
            return tagManager -> {
                Predicate<CachedBlockPosition> oldPredicate = predicate.create(tagManager);
                var cache = new HashMap<BlockState, Boolean>();
                return pos -> cache.computeIfAbsent(pos.getBlockState(), state -> oldPredicate.test(pos));
            };
        }

        return predicate;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        BlockArgumentParser blockParser = new BlockArgumentParser(stringReader, allowTags);

        try {
            blockParser.parse(true);
        } catch (CommandSyntaxException ignore) {
        }

        return blockParser.getSuggestions(builder, BlockTags.getTagGroup());
    }

    public static Predicate<CachedBlockPosition> getBlockPredicate(CommandContext<ServerCommandSource> context, String arg) throws CommandSyntaxException {
        //noinspection ConstantConditions
        return context.getArgument(arg, BlockPredicate.class).create(MinecraftClient.getInstance().getNetworkHandler().getTagManager());
    }
}
