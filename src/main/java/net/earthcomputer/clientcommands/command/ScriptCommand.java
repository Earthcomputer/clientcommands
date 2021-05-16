package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.script.ScriptManager;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.minecraft.server.command.CommandManager.*;

public class ScriptCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cscript");

        dispatcher.register(literal("cscript")
            .then(literal("reload")
                .executes(ctx -> reloadScripts()))
            .then(literal("run")
                .then(argument("script", string())
                    .suggests((ctx, builder) -> CommandSource.suggestMatching(ScriptManager.getScriptNames(), builder))
                    .executes(ctx -> runScript(getString(ctx, "script"))))));
    }

    private static int reloadScripts() {
        ScriptManager.reloadScripts();
        sendFeedback("commands.cscript.reload.success");
        return ScriptManager.getScriptNames().size();
    }

    private static int runScript(String name) throws CommandSyntaxException {
        ScriptManager.execute(name);
        sendFeedback("commands.cscript.run.success");
        return 0;
    }

}
