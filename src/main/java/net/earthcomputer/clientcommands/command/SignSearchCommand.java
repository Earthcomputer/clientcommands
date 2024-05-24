package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgument;
import net.earthcomputer.clientcommands.util.CUtil;
import net.earthcomputer.clientcommands.util.ThrowingPredicate;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.RegexArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class SignSearchCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("csignsearch")
            .then(literal("text")
                .then(argument("query", greedyString())
                    .executes(ctx -> FindBlockCommand.findBlock(ctx, Component.translatable("commands.csignsearch.starting"), predicate(getString(ctx, "query"))))))
            .then(literal("regex")
                .then(argument("query", greedyRegex())
                    .executes(ctx -> FindBlockCommand.findBlock(ctx, Component.translatable("commands.csignsearch.starting"), predicate(getRegex(ctx, "query")))))));
    }

    private static ClientBlockPredicateArgument.ClientBlockPredicate predicate(String query) {
        return signPredicateFromLinePredicate(line -> line.contains(query));
    }

    private static ClientBlockPredicateArgument.ClientBlockPredicate predicate(Pattern query) {
        return signPredicateFromLinePredicate(line -> CUtil.regexFindSafe(query, line));
    }

    private static ClientBlockPredicateArgument.ClientBlockPredicate signPredicateFromLinePredicate(ThrowingPredicate<String> linePredicate) {
        return new ClientBlockPredicateArgument.ClientBlockPredicate() {
            @Override
            public boolean test(HolderLookup.Provider holderLookupProvider, BlockGetter blockGetter, BlockPos pos) throws CommandSyntaxException {
                if (!(blockGetter.getBlockState(pos).getBlock() instanceof SignBlock)) {
                    return false;
                }
                BlockEntity be = blockGetter.getBlockEntity(pos);
                if (!(be instanceof SignBlockEntity sign)) {
                    return false;
                }

                SignText frontText = sign.getFrontText();
                SignText backText = sign.getBackText();
                for (int i = 0; i < SignText.LINES; i++) {
                    String line = frontText.getMessage(i, Minecraft.getInstance().isTextFilteringEnabled()).getString();
                    if (linePredicate.test(line)) {
                        return true;
                    }
                    line = backText.getMessage(i, Minecraft.getInstance().isTextFilteringEnabled()).getString();
                    if (linePredicate.test(line)) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean canEverMatch(BlockState state) {
                return state.getBlock() instanceof SignBlock;
            }
        };
    }

}
