package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ClientBlockPredicateArgument implements ArgumentType<ClientBlockPredicateArgument.ParseResult> {
    private final HolderLookup<Block> holderLookup;
    private boolean allowNbt = true;
    private boolean allowTags = true;

    private ClientBlockPredicateArgument(CommandBuildContext context) {
        holderLookup = context.lookupOrThrow(Registries.BLOCK);
    }

    public static ClientBlockPredicateArgument blockPredicate(CommandBuildContext context) {
        return new ClientBlockPredicateArgument(context);
    }

    public ClientBlockPredicateArgument disallowNbt() {
        allowNbt = false;
        return this;
    }

    public ClientBlockPredicateArgument disallowTags() {
        allowTags = false;
        return this;
    }

    @Override
    public ParseResult parse(StringReader stringReader) throws CommandSyntaxException {
        var result = BlockStateParser.parseForTesting(holderLookup, stringReader, allowNbt);
        return new ParseResult(result, holderLookup);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return BlockStateParser.fillSuggestions(holderLookup, builder, allowTags, allowNbt);
    }

    public static ClientBlockPredicate getBlockPredicate(CommandContext<FabricClientCommandSource> context, String arg) throws CommandSyntaxException {
        return getBlockPredicate(context.getArgument(arg, ParseResult.class));
    }

    public static ClientBlockPredicate getBlockPredicate(ParseResult result) throws CommandSyntaxException {
        Predicate<BlockState> predicate = getPredicateForListWithoutNbt(Collections.singletonList(result.result));
        CompoundTag nbtData = result.result.map(BlockStateParser.BlockResult::nbt, BlockStateParser.TagResult::nbt);
        if (nbtData == null) {
            return ClientBlockPredicate.simple(predicate);
        }

        return new ClientBlockPredicate() {
            @Override
            public boolean test(HolderLookup.Provider holderLookupProvider, BlockGetter blockGetter, BlockPos pos) {
                if (!predicate.test(blockGetter.getBlockState(pos))) {
                    return false;
                }
                BlockEntity be = blockGetter.getBlockEntity(pos);
                return be != null && NbtUtils.compareNbt(nbtData, be.saveWithoutMetadata(holderLookupProvider), true);
            }

            @Override
            public boolean canEverMatch(BlockState state) {
                return predicate.test(state);
            }
        };
    }

    public static ClientBlockPredicate getBlockPredicateList(CommandContext<FabricClientCommandSource> context, String arg) throws CommandSyntaxException {
        List<ParseResult> results = ListArgument.getList(context, arg);
        Predicate<BlockState> predicate = getPredicateForListWithoutNbt(results.stream().map(ParseResult::result).toList());

        List<Pair<Predicate<BlockState>, CompoundTag>> nbtPredicates = new ArrayList<>(results.size());
        boolean nbtSensitive = false;
        for (ParseResult result : results) {
            CompoundTag nbtData = result.result.map(BlockStateParser.BlockResult::nbt, BlockStateParser.TagResult::nbt);
            if (nbtData != null) {
                nbtSensitive = true;
            }
            nbtPredicates.add(Pair.of(getPredicateWithoutNbt(result.result), nbtData));
        }

        if (!nbtSensitive) {
            return ClientBlockPredicate.simple(predicate);
        }

        // sort by non-nbt-sensitive versions first in case we can get away with not querying block entities
        nbtPredicates.sort(Map.Entry.comparingByValue(Comparator.nullsFirst(Comparator.comparingInt(System::identityHashCode))));

        return new ClientBlockPredicate() {
            @Override
            public boolean test(HolderLookup.Provider holderLookupProvider, BlockGetter blockGetter, BlockPos pos) {
                BlockState state = blockGetter.getBlockState(pos);
                if (!predicate.test(state)) {
                    return false;
                }

                CompoundTag actualNbt = null;
                for (Pair<Predicate<BlockState>, CompoundTag> nbtPredicate : nbtPredicates) {
                    if (nbtPredicate.getLeft().test(state)) {
                        CompoundTag nbt = nbtPredicate.getRight();
                        if (nbt == null) {
                            return true;
                        }
                        if (actualNbt == null) {
                            BlockEntity be = blockGetter.getBlockEntity(pos);
                            if (be == null) {
                                // from this point we would always require a block entity
                                return false;
                            }
                            actualNbt = be.saveWithoutMetadata(holderLookupProvider);
                        }
                        if (NbtUtils.compareNbt(nbt, actualNbt, true)) {
                            return true;
                        }
                    }
                }

                return false;
            }

            @Override
            public boolean canEverMatch(BlockState state) {
                return predicate.test(state);
            }
        };
    }

    private static Predicate<BlockState> getPredicateForListWithoutNbt(List<Either<BlockStateParser.BlockResult, BlockStateParser.TagResult>> results) throws CommandSyntaxException {
        List<Predicate<BlockState>> predicates = new ArrayList<>(results.size());
        for (var result : results) {
            predicates.add(getPredicateWithoutNbt(result));
        }

        // slower than lazy computation but thread safe
        BitSet mask = new BitSet();
        BlockState state;
        for (int id = 0; (state = Block.BLOCK_STATE_REGISTRY.byId(id)) != null; id++) {
            for (Predicate<BlockState> predicate : predicates) {
                if (predicate.test(state)) {
                    mask.set(id);
                    break;
                }
            }
        }

        return blockState -> mask.get(Block.BLOCK_STATE_REGISTRY.getId(blockState));
    }

    private static Predicate<BlockState> getPredicateWithoutNbt(Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> result) throws CommandSyntaxException {
        return result.map(
                blockResult -> {
                    Map<Property<?>, Comparable<?>> props = blockResult.properties();
                    return state -> {
                        if (!state.is(blockResult.blockState().getBlock())) {
                            return false;
                        }
                        for (Map.Entry<Property<?>, Comparable<?>> entry : props.entrySet()) {
                            if (state.getValue(entry.getKey()) != entry.getValue()) {
                                return false;
                            }
                        }
                        return true;
                    };
                },
                tagResult -> {
                    HolderSet<Block> myTag = tagResult.tag();

                    Map<String, String> props = tagResult.vagueProperties();
                    return state -> {
                        if (!state.is(myTag)) {
                            return false;
                        }
                        for (Map.Entry<String, String> entry : props.entrySet()) {
                            Property<?> prop = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                            if (prop == null) {
                                return false;
                            }
                            Comparable<?> expectedValue = prop.getValue(entry.getValue()).orElse(null);
                            if (expectedValue == null) {
                                return false;
                            }
                            if (state.getValue(prop) != expectedValue) {
                                return false;
                            }
                        }
                        return true;
                    };
                }
        );
    }

    public record ParseResult(
            Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> result,
            HolderLookup<Block> holderLookup
    ) {
    }

    public interface ClientBlockPredicate {
        boolean test(HolderLookup.Provider holderLookupProvider, BlockGetter blockGetter, BlockPos pos) throws CommandSyntaxException;
        boolean canEverMatch(BlockState state);

        static ClientBlockPredicate simple(Predicate<BlockState> delegate) {
            return new ClientBlockPredicate() {
                @Override
                public boolean test(HolderLookup.Provider holderLookupProvider, BlockGetter blockGetter, BlockPos pos) {
                    return delegate.test(blockGetter.getBlockState(pos));
                }

                @Override
                public boolean canEverMatch(BlockState state) {
                    return delegate.test(state);
                }
            };
        }
    }
}
