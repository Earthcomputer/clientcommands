package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.earthcomputer.clientcommands.TempRules;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.StringIdentifiable;

import java.util.Comparator;
import java.util.List;

import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class TempRuleCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctemprule")
            .then(literal("list")
                .executes(ctx -> listRules(ctx.getSource())))
            .then(createGetSubcommand())
            .then(createSetSubcommand())
            .then(createResetSubcommand()));
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> createGetSubcommand() {
        var subcmd = literal("get");
        for (String rule : TempRules.getRules()) {
            subcmd.then(literal(rule)
                .executes(ctx -> getRule(ctx.getSource(), rule)));
        }
        return subcmd;
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> createSetSubcommand() {
        var subcmd = literal("set");
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
                var subsubcmd = literal(rule);
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

    private static ArgumentBuilder<FabricClientCommandSource, ?> createResetSubcommand() {
        var subcmd = literal("reset");
        for (String rule : TempRules.getWritableRules()) {
            subcmd.then(literal(rule)
                .executes(ctx -> resetRule(ctx.getSource(), rule)));
        }
        return subcmd;
    }

    private static int listRules(FabricClientCommandSource source) {
        List<String> rules = TempRules.getRules();
        rules.sort(Comparator.naturalOrder());

        source.sendFeedback(new TranslatableText("commands.ctemprule.list.header", rules.size()));
        for (String rule : rules) {
            source.sendFeedback(new LiteralText("- " + rule));
        }

        return rules.size();
    }

    private static int getRule(FabricClientCommandSource source, String rule) {
        Object val = TempRules.get(rule);
        String str = TempRules.asString(val);
        source.sendFeedback(new LiteralText(rule + " = " + str));
        return 0;
    }

    private static int setRule(FabricClientCommandSource source, String rule, Object value) {
        TempRules.set(rule, value);
        String str = TempRules.asString(value);
        source.sendFeedback(new TranslatableText("commands.ctemprule.set.success", rule, str));
        return 0;
    }

    private static int resetRule(FabricClientCommandSource source, String rule) {
        TempRules.reset(rule);
        String str = TempRules.asString(TempRules.get(rule));
        source.sendFeedback(new TranslatableText("commands.ctemprule.reset.success", rule, str));
        return 0;
    }

}
