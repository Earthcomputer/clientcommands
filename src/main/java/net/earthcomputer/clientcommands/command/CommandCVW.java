package net.earthcomputer.clientcommands.command;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.earthcomputer.clientcommands.cvw.ClientVirtualChunkLoader;
import net.earthcomputer.clientcommands.cvw.ServerConnector;
import net.earthcomputer.clientcommands.cvw.ServerConnectorCVW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;

public class CommandCVW extends ClientCommandBase {

	@Override
	public String getName() {
		return "cvw";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.cvw.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}
		switch (args[0]) {
		case "stop":
			ServerConnector connector = ServerConnector.forCurrentServer();
			if (connector instanceof ServerConnectorCVW) {
				connector.onDisconnectButtonPressed();
			} else {
				throw new CommandException("commands.cvw.stop.notRunning");
			}
			break;
		case "start":
			GameType gameType;
			if (args.length < 2) {
				gameType = GameType.CREATIVE;
			} else {
				gameType = GameType.parseGameTypeWithDefault(args[1], GameType.NOT_SET);
				if (gameType == GameType.NOT_SET) {
					gameType = WorldSettings.getGameTypeById(parseInt(args[1], 0, GameType.values().length - 2));
				}
			}
			startCVW(gameType);
			break;
		default:
			throw new WrongUsageException(getUsage(sender));
		}
	}

	private static void startCVW(GameType gameType) {
		WorldClient world = Minecraft.getMinecraft().world;
		Long2ObjectMap<NBTTagCompound> chunks = ClientVirtualChunkLoader.generateChunkMap(world,
				world.getChunkProvider());
		NBTTagCompound playerTag = Minecraft.getMinecraft().player.writeToNBT(new NBTTagCompound());
		ServerConnector oldServer = ServerConnector.forCurrentServer();
		oldServer.disconnect();
		ServerConnectorCVW newServer = new ServerConnectorCVW(world.provider.getDimension(), gameType, oldServer,
				chunks, playerTag);
		newServer.connect();
	}

}
