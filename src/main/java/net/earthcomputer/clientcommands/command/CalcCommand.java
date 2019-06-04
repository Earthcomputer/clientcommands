package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType;
import net.minecraft.ChatFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.command.ServerCommandSource;

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
        int iresult = 0;

        Component feedback = new TextComponent(expression.strVal + " = ");

        if (Math.round(result) == result) {
            String strResult = String.valueOf(result);
            feedback.append(new TextComponent(strResult.contains("E") ? strResult : strResult.substring(0, strResult.length() - 2)).applyFormat(ChatFormat.BOLD));
            iresult = (int) result;
            if (iresult == result && iresult > 0) {
                int stacks = iresult / 64;
                int remainder = iresult % 64;
                feedback.append(" = " + stacks + " * 64 + " + remainder);
            }
        } else {
            feedback.append(new TextComponent(String.valueOf(result)).applyFormat(ChatFormat.BOLD));
        }

        sendFeedback(feedback);

        return iresult;
    }

}
