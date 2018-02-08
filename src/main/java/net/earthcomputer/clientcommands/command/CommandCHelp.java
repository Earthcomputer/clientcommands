package net.earthcomputer.clientcommands.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.earthcomputer.clientcommands.EventManager;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandNotFoundException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.ClientCommandHandler;

public class CommandCHelp extends ClientCommandBase {

	static {
		EventManager.addChatSentListener(e -> {
			String message = e.getMessage();
			if (e.getMessage().startsWith("/")) {
				message = message.substring(1).trim();
				if (message.startsWith("help ")) {
					String[] args = message.split(" ");
					if (args.length > 1) {
						if (ClientCommandHandler.instance.getCommands().containsKey(args[1])) {
							TextComponentString hint = new TextComponentString("/chelp " + args[1]);
							hint.getStyle().setUnderlined(Boolean.TRUE);
							hint.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
									new TextComponentString("/chelp " + args[1])));
							hint.getStyle()
									.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chelp " + args[1]));
							Minecraft.getMinecraft().ingameGUI.getChatGUI()
									.printChatMessage(new TextComponentTranslation("commands.chelp.redirect.left")
											.appendSibling(hint).appendSibling(
													new TextComponentTranslation("commands.chelp.redirect.right")));
						}
					}
				}
			}
		});
		EventManager.addChatReceivedListener(e -> {
			if (e.getMessage() instanceof TextComponentTranslation) {
				String key = ((TextComponentTranslation) e.getMessage()).getKey();
				if ("commands.help.footer".equals(key)) {
					TextComponentTranslation message = new TextComponentTranslation("commands.chelp.helpHint");
					message.getStyle().setColor(TextFormatting.GREEN);
					Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(message);
				}
			}
		});
	}

	@Override
	public String getName() {
		return "chelp";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.chelp.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		List<ICommand> commandList = ClientCommandHandler.instance.getPossibleCommands(sender);
		Collections.sort(commandList);

		final int commandsPerPage = 7;
		int maxPage = (commandList.size() - 1) / commandsPerPage;
		int currentPage;

		try {
			currentPage = args.length == 0 ? 0 : parseInt(args[0], 1, maxPage + 1) - 1;
		} catch (NumberInvalidException e) {
			Map<String, ICommand> commandMap = ClientCommandHandler.instance.getCommands();
			ICommand command = commandMap.get(args[0]);

			if (command != null) {
				// this is the right usage of the chelp command!
				throw new WrongUsageException(command.getUsage(sender));
			}

			try {
				Integer.parseInt(args[0]);
				throw e;
			} catch (NumberFormatException e1) {
				throw new CommandNotFoundException("commands.chelp.unknownCommand");
			}
		}

		int endIndex = Math.min((currentPage + 1) * commandsPerPage, commandList.size());

		TextComponentTranslation message = new TextComponentTranslation("commands.chelp.header", currentPage + 1,
				maxPage + 1);
		message.getStyle().setColor(TextFormatting.DARK_GREEN);
		sender.sendMessage(message);

		for (int i = currentPage * commandsPerPage; i < endIndex; i++) {
			ICommand command = commandList.get(i);
			message = new TextComponentTranslation(command.getUsage(sender));
			message.getStyle()
					.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + command.getName() + " "));
			sender.sendMessage(message);
		}

		if (currentPage == 0) {
			message = new TextComponentTranslation("commands.help.footer");
			message.getStyle().setColor(TextFormatting.GREEN);
			sender.sendMessage(message);
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
			BlockPos targetPos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, ClientCommandHandler.instance.getCommands().keySet());
		} else {
			return Collections.emptyList();
		}
	}

}
