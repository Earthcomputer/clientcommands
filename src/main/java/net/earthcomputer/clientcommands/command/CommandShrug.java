package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.network.NetUtils;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class CommandShrug extends ClientCommandBase {

    @Override
    public String getName() {
        return "cshrug";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.cshrug.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        NetUtils.sendChatMessage("¯\\_(ツ)_/¯");
    }
}
