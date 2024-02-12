package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.features.ClientCommandFunctions;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class StartupCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType COULD_NOT_CREATE_FILE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cstartup.couldNotCreateFile"));
    private static final SimpleCommandExceptionType COULD_NOT_EDIT_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cstartup.couldNotEdit"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cstartup")
            .then(literal("local")
                .then(literal("add")
                    .then(argument("command", greedyString())
                        .executes(ctx -> addStartupCommand(ctx.getSource(), false, getString(ctx, "command")))))
                .then(literal("edit")
                    .executes(ctx -> editStartupCommand(false))))
            .then(literal("global")
                .then(literal("add")
                    .then(argument("command", greedyString())
                        .executes(ctx -> addStartupCommand(ctx.getSource(), true, getString(ctx, "command")))))
                .then(literal("edit")
                    .executes(ctx -> editStartupCommand(true)))));
    }

    private static int addStartupCommand(FabricClientCommandSource source, boolean global, String command) throws CommandSyntaxException {
        ensureStartupFileExists(global);

        Path file = global ? ClientCommandFunctions.getGlobalStartupFunction() : ClientCommandFunctions.getLocalStartupFunction();
        if (file == null) {
            throw COULD_NOT_CREATE_FILE_EXCEPTION.create();
        }

        try {
            Files.writeString(file, command + System.lineSeparator(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to append command to startup file", e);
            throw COULD_NOT_EDIT_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable("commands.cstartup.add.success"));

        return Command.SINGLE_SUCCESS;
    }

    private static int editStartupCommand(boolean global) throws CommandSyntaxException {
        ensureStartupFileExists(global);

        Path file = global ? ClientCommandFunctions.getGlobalStartupFunction() : ClientCommandFunctions.getLocalStartupFunction();
        if (file == null) {
            throw COULD_NOT_CREATE_FILE_EXCEPTION.create();
        }
        Util.getPlatform().openFile(file.toFile());
        return Command.SINGLE_SUCCESS;
    }

    private static void ensureStartupFileExists(boolean global) throws CommandSyntaxException {
        boolean result = true;
        try {
            if (global) {
                ClientCommandFunctions.ensureGlobalStartupFunctionExists();
            } else {
                result = ClientCommandFunctions.ensureLocalStartupFunctionExists();
            }
        } catch (IOException e) {
            LOGGER.error("Unable to create startup command file", e);
            result = false;
        }
        if (!result) {
            throw COULD_NOT_CREATE_FILE_EXCEPTION.create();
        }
    }
}
