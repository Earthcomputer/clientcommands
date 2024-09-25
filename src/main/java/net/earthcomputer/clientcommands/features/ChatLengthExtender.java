package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.ClientCommands;
import org.jetbrains.annotations.Nullable;

public class ChatLengthExtender {
    public static final int EXTENDED_LENGTH = 32767;
    @Nullable
    public static Integer currentLengthExtension = null;

    public static boolean isClientcommandsCommand(String command) {
        if (command.startsWith("/")) {
            String[] commandArgs = command.substring(1).split(" ");
            return commandArgs.length > 0 && ClientCommands.isClientcommandsCommand(commandArgs[0]);
        }

        return false;
    }
}
