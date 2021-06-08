package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.RegexArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class SignSearchCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("csignsearch");

        dispatcher.register(literal("csignsearch")
            .then(literal("text")
                .then(argument("query", greedyString())
                    .executes(ctx -> FindBlockCommand.findBlock(ctx.getSource(), predicate(getString(ctx, "query")), FindBlockCommand.MAX_RADIUS, FindBlockCommand.RadiusType.CARTESIAN))))
            .then(literal("regex")
                .then(argument("query", greedyRegex())
                    .executes(ctx -> FindBlockCommand.findBlock(ctx.getSource(), predicate(getRegex(ctx, "query")), FindBlockCommand.MAX_RADIUS, FindBlockCommand.RadiusType.CARTESIAN)))));
    }

    private static Predicate<CachedBlockPosition> predicate(String query) {
        return signPredicateFromLinePredicate(line -> line.contains(query));
    }

    private static Predicate<CachedBlockPosition> predicate(Pattern query) {
        return signPredicateFromLinePredicate(line -> query.matcher(line).find());
    }

    private static Predicate<CachedBlockPosition> signPredicateFromLinePredicate(Predicate<String> linePredicate) {
        return pos -> {
            if (!(pos.getBlockState().getBlock() instanceof AbstractSignBlock)) {
                return false;
            }
            BlockEntity be = pos.getBlockEntity();
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
