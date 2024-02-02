package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.RegexArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class SignSearchCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("csignsearch")
            .then(literal("text")
                .then(argument("query", greedyString())
                    .executes(ctx -> FindBlockCommand.findBlock(Component.translatable("commands.csignsearch.starting"), predicate(getString(ctx, "query"))))))
            .then(literal("regex")
                .then(argument("query", greedyRegex())
                    .executes(ctx -> FindBlockCommand.findBlock(Component.translatable("commands.csignsearch.starting"), predicate(getRegex(ctx, "query")))))));
    }

    private static ClientBlockPredicateArgumentType.ClientBlockPredicate predicate(String query) {
        return signPredicateFromLinePredicate(line -> line.contains(query));
    }

    private static ClientBlockPredicateArgumentType.ClientBlockPredicate predicate(Pattern query) {
        return signPredicateFromLinePredicate(line -> query.matcher(line).find());
    }

    private static ClientBlockPredicateArgumentType.ClientBlockPredicate signPredicateFromLinePredicate(Predicate<String> linePredicate) {
        return new ClientBlockPredicateArgumentType.ClientBlockPredicate() {
            @Override
            public boolean test(BlockGetter blockView, BlockPos pos) {
                if (!(blockView.getBlockState(pos).getBlock() instanceof SignBlock)) {
                    return false;
                }
                BlockEntity be = blockView.getBlockEntity(pos);
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
