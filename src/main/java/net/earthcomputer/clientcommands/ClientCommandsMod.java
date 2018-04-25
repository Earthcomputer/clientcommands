package net.earthcomputer.clientcommands;

import java.util.Map;

import net.earthcomputer.clientcommands.command.CommandAbort;
import net.earthcomputer.clientcommands.command.CommandCClear;
import net.earthcomputer.clientcommands.command.CommandCClone;
import net.earthcomputer.clientcommands.command.CommandCEnchant;
import net.earthcomputer.clientcommands.command.CommandCFill;
import net.earthcomputer.clientcommands.command.CommandCGive;
import net.earthcomputer.clientcommands.command.CommandCHelp;
import net.earthcomputer.clientcommands.command.CommandCTime;
import net.earthcomputer.clientcommands.command.CommandCWeather;
import net.earthcomputer.clientcommands.command.CommandCalc;
import net.earthcomputer.clientcommands.command.CommandFind;
import net.earthcomputer.clientcommands.command.CommandFindBlock;
import net.earthcomputer.clientcommands.command.CommandFindItem;
import net.earthcomputer.clientcommands.command.CommandLook;
import net.earthcomputer.clientcommands.command.CommandNote;
import net.earthcomputer.clientcommands.command.CommandRelog;
import net.earthcomputer.clientcommands.command.CommandTempRule;
import net.earthcomputer.clientcommands.command.CommandTick;
import net.earthcomputer.clientcommands.cvw.ServerConnector;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = ClientCommandsMod.MODID, version = ClientCommandsMod.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.13)")
public class ClientCommandsMod {
	public static final String MODID = "clientcommands";
	public static final String VERSION = BuildConstants.VERSION;

	@Instance(MODID)
	public static ClientCommandsMod INSTANCE;

	@NetworkCheckHandler
	public boolean checkConnect(Map<String, String> mods, Side otherSide) {
		return true;
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		registerCommands();
		registerEventStuff();
		SpecialActionKey.registerKeyBinding();
	}

	private void registerCommands() {
		ClientCommandHandler.instance.registerCommand(new CommandFind());
		ClientCommandHandler.instance.registerCommand(new CommandFindBlock());
		ClientCommandHandler.instance.registerCommand(new CommandFindItem());
		ClientCommandHandler.instance.registerCommand(new CommandRelog());
		ClientCommandHandler.instance.registerCommand(new CommandLook());
		ClientCommandHandler.instance.registerCommand(new CommandCalc());
		ClientCommandHandler.instance.registerCommand(new CommandCHelp());
		ClientCommandHandler.instance.registerCommand(new CommandCClear());
		ClientCommandHandler.instance.registerCommand(new CommandCGive());
		ClientCommandHandler.instance.registerCommand(new CommandAbort());
		ClientCommandHandler.instance.registerCommand(new CommandNote());
		ClientCommandHandler.instance.registerCommand(new CommandTempRule());
		ClientCommandHandler.instance.registerCommand(new CommandCEnchant());
		ClientCommandHandler.instance.registerCommand(new CommandCTime());
		// ClientCommandHandler.instance.registerCommand(new CommandCVW());
		ClientCommandHandler.instance.registerCommand(new CommandCWeather());
		ClientCommandHandler.instance.registerCommand(new CommandTick());
		ClientCommandHandler.instance.registerCommand(new CommandCFill());
		ClientCommandHandler.instance.registerCommand(new CommandCClone());
	}

	private void registerEventStuff() {
		EnchantmentCracker.registerEvents();
		ToolDamageManager.registerEvents();
		TempRulesImpl.registerEvents();
		SpecialActionKey.registerEvents();
		ServerConnector.registerEvents();

		EventManager.addDisconnectExceptRelogListener(e -> TempRules.resetToDefault());

		EventManager.addPlayerTickListener(new CoreModSanityCheck());

		MinecraftForge.EVENT_BUS.register(EventManager.INSTANCE);
	}

}
