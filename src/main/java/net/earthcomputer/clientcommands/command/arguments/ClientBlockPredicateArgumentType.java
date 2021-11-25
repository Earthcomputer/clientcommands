package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.state.property.Property;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.tag.TagManager;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
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

public class ClientBlockPredicateArgumentType implements ArgumentType<BlockArgumentParser> {
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
    public BlockArgumentParser parse(StringReader stringReader) throws CommandSyntaxException {
        return (new BlockArgumentParser(stringReader, allowTags)).parse(allowNbt);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        BlockArgumentParser blockParser = new BlockArgumentParser(stringReader, allowTags);

        try {
            blockParser.parse(allowNbt);
        } catch (CommandSyntaxException ignore) {
        }

        return blockParser.getSuggestions(builder, BlockTags.getTagGroup());
    }

    public static ClientBlockPredicate getBlockPredicate(CommandContext<ServerCommandSource> context, String arg) throws CommandSyntaxException {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        assert networkHandler != null;

        BlockArgumentParser argParser = context.getArgument(arg, BlockArgumentParser.class);
        ClientBlockPredicate predicate = getPredicateForListWithoutNbt(networkHandler.getTagManager(), Collections.singletonList(argParser));
        NbtCompound nbtData = argParser.getNbtData();
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

    public static ClientBlockPredicate getBlockPredicateList(CommandContext<ServerCommandSource> context, String arg) throws CommandSyntaxException {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        assert networkHandler != null;
        TagManager tagManager = networkHandler.getTagManager();

        List<BlockArgumentParser> argParsers = ListArgumentType.getList(context, arg);
        ClientBlockPredicate predicate = getPredicateForListWithoutNbt(tagManager, argParsers);

        List<Pair<Predicate<BlockState>, NbtCompound>> nbtPredicates = new ArrayList<>(argParsers.size());
        boolean nbtSensitive = false;
        for (BlockArgumentParser parser : argParsers) {
            NbtCompound nbtData = parser.getNbtData();
            if (nbtData != null) {
                nbtSensitive = true;
            }
            nbtPredicates.add(Pair.of(getPredicateWithoutNbt(tagManager, parser), nbtData));
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

    private static ClientBlockPredicate getPredicateForListWithoutNbt(TagManager tagManager, List<BlockArgumentParser> parsers) throws CommandSyntaxException {
        List<Predicate<BlockState>> predicates = new ArrayList<>(parsers.size());
        for (BlockArgumentParser parser : parsers) {
            predicates.add(getPredicateWithoutNbt(tagManager, parser));
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

    private static Predicate<BlockState> getPredicateWithoutNbt(TagManager tagManager, BlockArgumentParser parser) throws CommandSyntaxException {
        BlockState myState = parser.getBlockState();
        if (myState != null) {
            Map<Property<?>, Comparable<?>> props = parser.getBlockProperties();
            return state -> {
                if (!state.isOf(myState.getBlock())) {
                    return false;
                }
                for (Map.Entry<Property<?>, Comparable<?>> entry : props.entrySet()) {
                    if (state.get(entry.getKey()) != entry.getValue()) {
                        return false;
                    }
                }
                return true;
            };
        } else {
            Tag<Block> myTag = tagManager.getTag(Registry.BLOCK_KEY, parser.getTagId(), id -> UNKNOWN_TAG_EXCEPTION.create(id.toString()));
            Map<String, String> props = parser.getProperties();
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
    }

    @FunctionalInterface
    public interface ClientBlockPredicate {
        boolean test(BlockView blockView, BlockPos pos);
    }
}
