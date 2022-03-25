package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.RegexArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class SignSearchCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("csignsearch")
            .then(literal("text")
                .then(argument("query", greedyString())
                    .executes(ctx -> FindBlockCommand.findBlock(ctx.getSource(), predicate(getString(ctx, "query")), FindBlockCommand.MAX_RADIUS, FindBlockCommand.RadiusType.CARTESIAN))))
            .then(literal("regex")
                .then(argument("query", greedyRegex())
                    .executes(ctx -> FindBlockCommand.findBlock(ctx.getSource(), predicate(getRegex(ctx, "query")), FindBlockCommand.MAX_RADIUS, FindBlockCommand.RadiusType.CARTESIAN)))));
    }

    private static ClientBlockPredicateArgumentType.ClientBlockPredicate predicate(String query) {
        return signPredicateFromLinePredicate(line -> line.contains(query));
    }

    private static ClientBlockPredicateArgumentType.ClientBlockPredicate predicate(Pattern query) {
        return signPredicateFromLinePredicate(line -> query.matcher(line).find());
    }

    private static ClientBlockPredicateArgumentType.ClientBlockPredicate signPredicateFromLinePredicate(Predicate<String> linePredicate) {
        return (blockView, pos) -> {
            if (!(blockView.getBlockState(pos).getBlock() instanceof AbstractSignBlock)) {
                return false;
            }
            BlockEntity be = blockView.getBlockEntity(pos);
            if (!(be instanceof SignBlockEntity sign)) {
                return false;
            }

            for (int i = 0; i < 4; i++) {
                String line = sign.getTextOnRow(i, MinecraftClient.getInstance().shouldFilterText()).getString();
                if (linePredicate.test(line)) {
                    return true;
                }
            }

            return false;
        };
    }

}
