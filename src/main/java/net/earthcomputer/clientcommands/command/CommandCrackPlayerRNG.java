package net.earthcomputer.clientcommands.command;

import net.earthcomputer.clientcommands.EnchantmentCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;

public class CommandCrackPlayerRNG extends ClientCommandBase {
    @Override
    public String getName() {
        return "ccrackplayerrng";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.ccrackplayerrng.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!Minecraft.getMinecraft().isIntegratedServerRunning())
            throw new CommandException("commands.ccrackplayerrng.multiplayer");

        long seed = EnchantmentCracker.singlePlayerCrackRNG();
        sender.sendMessage(new TextComponentTranslation("commands.ccrackplayerrng.success", Long.toHexString(seed)));
    }
}
