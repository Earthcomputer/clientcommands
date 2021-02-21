package net.earthcomputer.clientcommands.command.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class CalcCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ccalc");

        dispatcher.register(literal("ccalc")
            .then(argument("expr", expression())
                .executes(ctx -> evaluateExpression(ctx.getSource(), getExpression(ctx, "expr")))));
    }

    private static int evaluateExpression(ServerCommandSource source, ExpressionArgumentType.Expression expression) {
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
