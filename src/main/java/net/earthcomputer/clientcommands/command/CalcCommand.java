package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.getFlag;
import static net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType.expression;
import static net.earthcomputer.clientcommands.command.arguments.ExpressionArgumentType.getExpression;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CalcCommand {

    private static final SimpleCommandExceptionType TOO_DEEPLY_NESTED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ccalc.tooDeeplyNested"));

    private static final Flag<Boolean> FLAG_PARSE = Flag.ofFlag("parse").build();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var ccalc = dispatcher.register(literal("ccalc")
            .then(argument("expr", expression())
                .executes(ctx -> evaluateExpression(ctx.getSource(), getExpression(ctx, "expr")))));
        FLAG_PARSE.addToCommand(dispatcher, ccalc, ctx -> true);
    }

    private static int evaluateExpression(FabricClientCommandSource source, ExpressionArgumentType.Expression expression) throws CommandSyntaxException {
        if (getFlag(source, FLAG_PARSE)) {
            Component parsedTree;
            try {
                parsedTree = expression.getParsedTree(0);
            } catch (StackOverflowError e) {
                throw TOO_DEEPLY_NESTED_EXCEPTION.create();
            }
            source.sendFeedback(Component.translatable("commands.ccalc.parse", parsedTree));
        }

        double result;
        try {
            result = expression.eval();
        } catch (StackOverflowError e) {
            throw TOO_DEEPLY_NESTED_EXCEPTION.create();
        }
        Configs.calcAnswer = result;
        int iresult = 0;

        MutableComponent feedback = Component.literal(expression.strVal + " = ");

        if (Math.round(result) == result) {
            String strResult = String.valueOf(result);
            feedback.append(Component.literal(strResult.contains("E") ? strResult : strResult.substring(0, strResult.length() - 2)).withStyle(ChatFormatting.BOLD));
            iresult = (int) result;
            if (iresult == result && iresult > 0) {
                int stacks = iresult / 64;
                int remainder = iresult % 64;
                feedback.append(" = " + stacks + " * 64 + " + remainder);
            }
        } else {
            feedback.append(Component.literal(String.valueOf(result)).withStyle(ChatFormatting.BOLD));
        }

        source.sendFeedback(feedback);

        return iresult;
    }
}
