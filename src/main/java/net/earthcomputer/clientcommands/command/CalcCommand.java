package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class CalcCommand {

    private static final int FLAG_PARSE = 1;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ccalc");

        LiteralCommandNode<ServerCommandSource> ccalc = dispatcher.register(literal("ccalc"));
        dispatcher.register(literal("ccalc")
            .then(literal("--parse")
                .redirect(ccalc, ctx -> withFlags(ctx.getSource(), FLAG_PARSE, true)))
            .then(argument("expr", expression())
                .executes(ctx -> evaluateExpression(ctx.getSource(), getExpression(ctx, "expr")))));
    }

    private static int evaluateExpression(ServerCommandSource source, ExpressionArgumentType.Expression expression) {
        if (getFlag(source, FLAG_PARSE)) {
            sendFeedback(new TranslatableText("commands.ccalc.parse", expression.getParsedTree()));
        }

        double result = expression.eval();
        TempRules.calcAnswer = result;
        int iresult = 0;

        MutableText feedback = new LiteralText(expression.strVal + " = ");

        if (Math.round(result) == result) {
            String strResult = String.valueOf(result);
            feedback.append(new LiteralText(strResult.contains("E") ? strResult : strResult.substring(0, strResult.length() - 2)).formatted(Formatting.BOLD));
            iresult = (int) result;
            if (iresult == result && iresult > 0) {
                int stacks = iresult / 64;
                int remainder = iresult % 64;
                feedback.append(" = " + stacks + " * 64 + " + remainder);
            }
        } else {
            feedback.append(new LiteralText(String.valueOf(result)).formatted(Formatting.BOLD));
        }

        sendFeedback(feedback);

        return iresult;
    }

}
