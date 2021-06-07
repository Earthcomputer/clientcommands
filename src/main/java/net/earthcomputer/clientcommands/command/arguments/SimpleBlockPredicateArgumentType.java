package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.registry.Registry;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class SimpleBlockPredicateArgumentType implements ArgumentType<Predicate<Block>> {

    private static final Collection<String> EXAMPLES = Arrays.asList("minecraft:stone", "stone");

    public SimpleBlockPredicateArgumentType() {
    }

    public static SimpleBlockPredicateArgumentType blockPredicate() {
        return new SimpleBlockPredicateArgumentType();
    }

    @SuppressWarnings("unchecked")
    public static Predicate<Block> getBlockPredicate(CommandContext<ServerCommandSource> context, String name) {
        return (Predicate<Block>) context.getArgument(name, Predicate.class);
    }

    @Override
    public Predicate<Block> parse(StringReader stringReader) throws CommandSyntaxException {
        BlockArgumentParser blockArgumentParser = (new BlockArgumentParser(stringReader, false)).parse(false);
        BlockState parsedBlockState = blockArgumentParser.getBlockState();
        if (parsedBlockState == null) {
            if (Registry.BLOCK.getOrEmpty(blockArgumentParser.getTagId()).isPresent()) {
                Block parsedBlock = Registry.BLOCK.getOrEmpty(blockArgumentParser.getTagId()).get();
                return block -> block.is(parsedBlock);
            } else {
                throw BlockArgumentParser.INVALID_BLOCK_ID_EXCEPTION.create(blockArgumentParser.getTagId().toString());
            }
        } else {
            Block parsedBlock = parsedBlockState.getBlock();
            return block -> block.is(parsedBlock);
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        BlockArgumentParser blockArgumentParser = new BlockArgumentParser(stringReader, false);

        try {
            blockArgumentParser.parse(false);
        } catch (CommandSyntaxException ignored) {
        }

        return blockArgumentParser.getSuggestions(builder, BlockTags.getTagGroup());
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
