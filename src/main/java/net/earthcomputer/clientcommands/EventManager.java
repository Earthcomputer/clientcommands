package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.earthcomputer.clientcommands.command.CommandRelog;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;

public class EventManager {

	public static final EventManager INSTANCE = new EventManager();

	private EventManager() {
	}

	// DISCONNECT

	private static Listeners<ClientDisconnectionFromServerEvent> disconnectListeners = new Listeners<>();

	public static void addDisconnectListener(Consumer<ClientDisconnectionFromServerEvent> listener) {
		disconnectListeners.add(listener);
	}

	@SubscribeEvent
	public void onDisconnect(ClientDisconnectionFromServerEvent e) {
		if (CommandRelog.isRelogging) {
			CommandRelog.isRelogging = false;
		} else {
			disconnectListeners.invoke(e);
		}
	}

	// TICK

	private static Listeners<ClientTickEvent> tickListeners = new Listeners<>();

	public static void addTickListener(Consumer<ClientTickEvent> listener) {
		tickListeners.add(listener);
	}

	@SubscribeEvent
	public void onTick(ClientTickEvent e) {
		tickListeners.invoke(e);
	}

	// GUI OPENED

	private static Listeners<GuiOpenEvent> guiOpenListeners = new Listeners<>();

	public static void addGuiOpenListener(Consumer<GuiOpenEvent> listener) {
		guiOpenListeners.add(listener);
	}

	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent e) {
		guiOpenListeners.invoke(e);
	}

	// IMPLEMENTATION

	private static class Listeners<E extends Event> {
		private List<Consumer<E>> listeners = new ArrayList<>();

		public void invoke(E e) {
			listeners.forEach(l -> l.accept(e));
		}

		public void add(Consumer<E> listener) {
			listeners.add(listener);
		}
	}

}
