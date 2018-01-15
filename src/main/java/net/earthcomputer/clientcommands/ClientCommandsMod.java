package net.earthcomputer.clientcommands;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.earthcomputer.clientcommands.command.CommandAbort;
import net.earthcomputer.clientcommands.command.CommandCClear;
import net.earthcomputer.clientcommands.command.CommandCGive;
import net.earthcomputer.clientcommands.command.CommandCHelp;
import net.earthcomputer.clientcommands.command.CommandCalc;
import net.earthcomputer.clientcommands.command.CommandFind;
import net.earthcomputer.clientcommands.command.CommandFindBlock;
import net.earthcomputer.clientcommands.command.CommandFindItem;
import net.earthcomputer.clientcommands.command.CommandLook;
import net.earthcomputer.clientcommands.command.CommandNote;
import net.earthcomputer.clientcommands.command.CommandRelog;
import net.earthcomputer.clientcommands.command.CommandSimGen;
import net.earthcomputer.clientcommands.command.CommandTempRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.GameRules;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = ClientCommandsMod.MODID, version = ClientCommandsMod.VERSION, clientSideOnly = true)
public class ClientCommandsMod {
	public static final String MODID = "clientcommands";
	public static final String VERSION = "1.0";

	@Instance(MODID)
	public static ClientCommandsMod INSTANCE;

	private List<GuiBlocker> guiBlockers = new ArrayList<>();
	private Queue<LongTask> longTaskQueue = new ArrayDeque<>();
	private LongTask currentLongTask;
	private GameRules tempRules;

	@NetworkCheckHandler
	public boolean checkConnect(Map<String, String> mods, Side otherSide) {
		return true;
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		tempRules = new GameRules();
		Map<String, ?> rules = ReflectionHelper.getPrivateValue(GameRules.class, tempRules, "rules", "field_82771_a");
		rules.clear();
		resetTempRules();

		Minecraft.getMinecraft().playerController = new PlayerControllerMP(Minecraft.getMinecraft(), null) {

		};

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
		ClientCommandHandler.instance.registerCommand(new CommandSimGen());
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(GuiBetterEnchantment.class);
	}

	public void resetTempRules() {
		tempRules.addGameRule("enchantingPrediction", "false", GameRules.ValueType.BOOLEAN_VALUE);
		tempRules.addGameRule("blockReachDistance", "default", GameRules.ValueType.ANY_VALUE);
	}

	public void addGuiBlocker(GuiBlocker blocker) {
		guiBlockers.add(blocker);
	}

	public void ensureNoTasks() throws CommandException {
		if (currentLongTask != null || !longTaskQueue.isEmpty()) {
			throw new CommandException("Looks like there is already a task running! Try /cabort.");
		}
	}

	public boolean abortTasks() {
		boolean result = !longTaskQueue.isEmpty() || currentLongTask != null;
		longTaskQueue.clear();
		if (currentLongTask != null) {
			currentLongTask.cleanup();
			currentLongTask = null;
		}
		return result;
	}

	public void addLongTask(LongTask task) {
		longTaskQueue.add(task);
	}

	public GameRules getTempRules() {
		return tempRules;
	}

	@SubscribeEvent
	public void onGuiOpened(GuiOpenEvent e) {
		Iterator<GuiBlocker> guiBlockerItr = guiBlockers.iterator();
		while (guiBlockerItr.hasNext()) {
			GuiBlocker guiBlocker = guiBlockerItr.next();
			if (guiBlocker.isFinished()) {
				guiBlockerItr.remove();
			} else {
				if (!guiBlocker.processGui(e.getGui())) {
					e.setCanceled(true);
				}
				if (guiBlocker.isFinished()) {
					guiBlockerItr.remove();
				}
			}
		}
	}

	@SubscribeEvent
	public void tick(ClientTickEvent e) {
		while (true) {
			if (currentLongTask == null) {
				if (!longTaskQueue.isEmpty()) {
					currentLongTask = longTaskQueue.remove();
					currentLongTask.start();
				} else {
					break;
				}
			}
			if (currentLongTask.isFinished()) {
				currentLongTask.cleanup();
				currentLongTask = null;
			} else {
				currentLongTask.tick();
				if (currentLongTask.isFinished()) {
					currentLongTask.cleanup();
					currentLongTask = null;
				} else {
					break;
				}
			}
		}

		if (Minecraft.getMinecraft().inGameHasFocus) {
			try {
				double reachDistance = Double.parseDouble(tempRules.getString("blockReachDistance"));
				Minecraft.getMinecraft().player.getAttributeMap().getAttributeInstance(EntityPlayer.REACH_DISTANCE)
						.setBaseValue(reachDistance);
			} catch (NumberFormatException e1) {
			}
		}
	}

	@SubscribeEvent
	public void onDisconnect(ClientDisconnectionFromServerEvent e) {
		if (CommandRelog.isRelogging) {
			CommandRelog.isRelogging = false;
		} else {
			abortTasks();
			guiBlockers.clear();
			resetTempRules();
			GuiBetterEnchantment.reset();
		}
	}
}
