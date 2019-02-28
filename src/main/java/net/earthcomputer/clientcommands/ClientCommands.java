package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.BookCommand;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.server.command.CommandSource;

public class ClientCommands implements ClientModInitializer {
    @Override
    public void onInitializeClient() {

    }

    public static void registerCommands(CommandDispatcher<CommandSource> dispatcher) {
        ClientCommandManager.clearClientSideCommands();
        BookCommand.register(dispatcher);
    }
}
