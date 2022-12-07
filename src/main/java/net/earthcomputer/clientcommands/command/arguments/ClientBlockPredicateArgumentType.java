package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ClientBlockPredicateArgumentType implements ArgumentType<ClientBlockPredicateArgumentType.ParseResult> {
    private final RegistryWrapper<Block> registryWrapper;
    private boolean allowNbt = true;
    private boolean allowTags = true;

    private ClientBlockPredicateArgumentType(CommandRegistryAccess registryAccess) {
        registryWrapper = registryAccess.createWrapper(RegistryKeys.BLOCK);
    }

    public static ClientBlockPredicateArgumentType blockPredicate(CommandRegistryAccess registryAccess) {
        return new ClientBlockPredicateArgumentType(registryAccess);
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
    public ParseResult parse(StringReader stringReader) throws CommandSyntaxException {
        var result = BlockArgumentParser.blockOrTag(registryWrapper, stringReader, allowNbt);
        return new ParseResult(result, registryWrapper);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return BlockArgumentParser.getSuggestions(registryWrapper, builder, allowTags, allowNbt);
    }

    public static ClientBlockPredicate getBlockPredicate(CommandContext<FabricClientCommandSource> context, String arg) throws CommandSyntaxException {
        ParseResult result = context.getArgument(arg, ParseResult.class);
        ClientBlockPredicate predicate = getPredicateForListWithoutNbt(Collections.singletonList(result.result));
        NbtCompound nbtData = result.result.map(BlockArgumentParser.BlockResult::nbt, BlockArgumentParser.TagResult::nbt);
        if (nbtData == null) {
            return predicate;
        }

        return (blockView, pos) -> {
            if (!predicate.test(blockView, pos)) {
                return false;
            }
            BlockEntity be = blockView.getBlockEntity(pos);
            return be != null && NbtHelper.matches(nbtData, be.createNbt(), true);
        };
    }

    public static ClientBlockPredicate getBlockPredicateList(CommandContext<FabricClientCommandSource> context, String arg) throws CommandSyntaxException {
        List<ParseResult> results = ListArgumentType.getList(context, arg);
        ClientBlockPredicate predicate = getPredicateForListWithoutNbt(results.stream().map(ParseResult::result).toList());

        List<Pair<Predicate<BlockState>, NbtCompound>> nbtPredicates = new ArrayList<>(results.size());
        boolean nbtSensitive = false;
        for (ParseResult result : results) {
            NbtCompound nbtData = result.result.map(BlockArgumentParser.BlockResult::nbt, BlockArgumentParser.TagResult::nbt);
            if (nbtData != null) {
                nbtSensitive = true;
            }
            nbtPredicates.add(Pair.of(getPredicateWithoutNbt(result.result), nbtData));
        }

        if (!nbtSensitive) {
            return predicate;
        }

        // sort by non-nbt-sensitive versions first in case we can get away with not querying block entities
        nbtPredicates.sort(Map.Entry.comparingByValue(Comparator.nullsFirst(Comparator.comparingInt(System::identityHashCode))));

        return (blockView, pos) -> {
            if (!predicate.test(blockView, pos)) {
                return false;
            }

            BlockState state = blockView.getBlockState(pos);
            NbtCompound actualNbt = null;
            for (Pair<Predicate<BlockState>, NbtCompound> nbtPredicate : nbtPredicates) {
                if (nbtPredicate.getLeft().test(state)) {
                    NbtCompound nbt = nbtPredicate.getRight();
                    if (nbt == null) {
                        return true;
                    }
                    if (actualNbt == null) {
                        BlockEntity be = blockView.getBlockEntity(pos);
                        if (be == null) {
                            // from this point we would always require a block entity
                            return false;
                        }
                        actualNbt = be.createNbt();
                    }
                    if (NbtHelper.matches(nbt, actualNbt, true)) {
                        return true;
                    }
                }
            }

            return false;
        };
    }

    private static ClientBlockPredicate getPredicateForListWithoutNbt(List<Either<BlockArgumentParser.BlockResult, BlockArgumentParser.TagResult>> results) throws CommandSyntaxException {
        List<Predicate<BlockState>> predicates = new ArrayList<>(results.size());
        for (var result : results) {
            predicates.add(getPredicateWithoutNbt(result));
        }

        // slower than lazy computation but thread safe
        BitSet mask = new BitSet();
        BlockState state;
        for (int id = 0; (state = Block.STATE_IDS.get(id)) != null; id++) {
            for (Predicate<BlockState> predicate : predicates) {
                if (predicate.test(state)) {
                    mask.set(id);
                    break;
                }
            }
        }

        return (blockView, pos) -> mask.get(Block.STATE_IDS.getRawId(blockView.getBlockState(pos)));
    }

    private static Predicate<BlockState> getPredicateWithoutNbt(Either<BlockArgumentParser.BlockResult, BlockArgumentParser.TagResult> result) throws CommandSyntaxException {
        return result.map(
                blockResult -> {
                    Map<Property<?>, Comparable<?>> props = blockResult.properties();
                    return state -> {
                        if (!state.isOf(blockResult.blockState().getBlock())) {
                            return false;
                        }
                        for (Map.Entry<Property<?>, Comparable<?>> entry : props.entrySet()) {
                            if (state.get(entry.getKey()) != entry.getValue()) {
                                return false;
                            }
                        }
                        return true;
                    };
                },
                tagResult -> {
                    RegistryEntryList<Block> myTag = tagResult.tag();

                    Map<String, String> props = tagResult.vagueProperties();
                    return state -> {
                        if (!state.isIn(myTag)) {
                            return false;
                        }
                        for (Map.Entry<String, String> entry : props.entrySet()) {
                            Property<?> prop = state.getBlock().getStateManager().getProperty(entry.getKey());
                            if (prop == null) {
                                return false;
                            }
                            Comparable<?> expectedValue = prop.parse(entry.getValue()).orElse(null);
                            if (expectedValue == null) {
                                return false;
                            }
                            if (state.get(prop) != expectedValue) {
                                return false;
                            }
                        }
                        return true;
                    };
                }
        );
    }

    public record ParseResult(
            Either<BlockArgumentParser.BlockResult, BlockArgumentParser.TagResult> result,
            RegistryWrapper<Block> registryWrapper
    ) {
    }

    @FunctionalInterface
    public interface ClientBlockPredicate {
        boolean test(BlockView blockView, BlockPos pos);
    }
}
