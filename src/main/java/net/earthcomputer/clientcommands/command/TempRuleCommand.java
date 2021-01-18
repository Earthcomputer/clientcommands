package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.earthcomputer.clientcommands.TempRules;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.StringIdentifiable;

import java.util.Comparator;
import java.util.List;

import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class TempRuleCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ctemprule");

        dispatcher.register(literal("ctemprule")
            .then(literal("list")
                .executes(ctx -> listRules(ctx.getSource())))
            .then(createGetSubcommand())
            .then(createSetSubcommand())
            .then(createResetSubcommand()));
    }

    private static ArgumentBuilder<ServerCommandSource, ?> createGetSubcommand() {
        ArgumentBuilder<ServerCommandSource, ?> subcmd = literal("get");
        for (String rule : TempRules.getRules()) {
            subcmd.then(literal(rule)
                .executes(ctx -> getRule(ctx.getSource(), rule)));
        }
        return subcmd;
    }

    private static ArgumentBuilder<ServerCommandSource, ?> createSetSubcommand() {
        ArgumentBuilder<ServerCommandSource, ?> subcmd = literal("set");
        for (String rule : TempRules.getWritableRules()) {
            Class<?> type = TempRules.getType(rule);
            if (type == boolean.class) {
                subcmd.then(literal(rule)
                    .then(argument("value", bool())
                        .executes(ctx -> setRule(ctx.getSource(), rule, getBool(ctx, "value")))));
            } else if (type == int.class) {
                subcmd.then(literal(rule)
                    .then(argument("value", integer())
                        .executes(ctx -> setRule(ctx.getSource(), rule, getInteger(ctx, "value")))));
            } else if (type == double.class) {
                subcmd.then(literal(rule)
                    .then(argument("value", doubleArg())
                        .executes(ctx -> setRule(ctx.getSource(), rule, getDouble(ctx, "value")))));
            } else if (type.isEnum() && StringIdentifiable.class.isAssignableFrom(type)) {
                ArgumentBuilder<ServerCommandSource, ?> subsubcmd = literal(rule);
                for (Object val : type.getEnumConstants()) {
                    subsubcmd.then(literal(((StringIdentifiable) val).asString())
                        .executes(ctx -> setRule(ctx.getSource(), rule, val)));
                }
                subcmd.then(subsubcmd);
            } else {
                throw new AssertionError("Unsupported rule of " + type);
            }
        }
        return subcmd;
    }

    private static ArgumentBuilder<ServerCommandSource, ?> createResetSubcommand() {
        ArgumentBuilder<ServerCommandSource, ?> subcmd = literal("reset");
        for (String rule : TempRules.getWritableRules()) {
            subcmd.then(literal(rule)
                .executes(ctx -> resetRule(ctx.getSource(), rule)));
        }
        return subcmd;
    }

    private static int listRules(ServerCommandSource source) {
        List<String> rules = TempRules.getRules();
        rules.sort(Comparator.naturalOrder());

        sendFeedback(new TranslatableText("commands.ctemprule.list.header", rules.size()));
        for (String rule : rules) {
            sendFeedback(new LiteralText("- " + rule));
        }

        return rules.size();
    }

    private static int getRule(ServerCommandSource source, String rule) {
        Object val = TempRules.get(rule);
        String str = TempRules.asString(val);
        sendFeedback(new LiteralText(rule + " = " + str));
        return 0;
    }

    private static int setRule(ServerCommandSource source, String rule, Object value) {
        TempRules.set(rule, value);
        String str = TempRules.asString(value);
        sendFeedback(new TranslatableText("commands.ctemprule.set.success", rule, str));
        return 0;
    }

    private static int resetRule(ServerCommandSource source, String rule) {
        TempRules.reset(rule);
        String str = TempRules.asString(TempRules.get(rule));
        sendFeedback(new TranslatableText("commands.ctemprule.reset.success", rule, str));
        return 0;
    }

}
