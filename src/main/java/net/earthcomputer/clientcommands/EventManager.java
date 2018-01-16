package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.earthcomputer.clientcommands.command.CommandRelog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
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

	// OUTBOUND PACKET PRE

	private static Listeners<PacketEvent.Outbound.Pre> outboundPacketPreListeners = new Listeners<>();

	public static void addOutboundPacketPreListener(Consumer<PacketEvent.Outbound.Pre> listener) {
		outboundPacketPreListeners.add(listener);
	}

	@SubscribeEvent
	public void onPacketOutboundPre(PacketEvent.Outbound.Pre e) {
		outboundPacketPreListeners.invoke(e);
	}

	public static Packet<?> firePacketOutboundPre(NetworkManager netManager, Packet<?> packet) {
		if (netManager.getDirection() == EnumPacketDirection.SERVERBOUND) {
			return packet;
		}
		PacketEvent.Outbound.Pre event = new PacketEvent.Outbound.Pre(packet);
		MinecraftForge.EVENT_BUS.post(event);
		return event.isCanceled() ? null : event.getPacket();
	}

	// OUTBOUND PACKET POST

	private static Listeners<PacketEvent.Outbound.Post> outboundPacketPostListeners = new Listeners<>();

	public static void addOutboundPacketPostListener(Consumer<PacketEvent.Outbound.Post> listener) {
		outboundPacketPostListeners.add(listener);
	}

	@SubscribeEvent
	public void onPacketOutboundPost(PacketEvent.Outbound.Post e) {
		outboundPacketPostListeners.invoke(e);
	}

	public static void firePacketOutboundPost(NetworkManager netManager, Packet<?> packet) {
		if (netManager.getDirection() == EnumPacketDirection.CLIENTBOUND) {
			MinecraftForge.EVENT_BUS.post(new PacketEvent.Outbound.Post(packet));
		}
	}

	// INBOUND PACKET PRE

	private static Listeners<PacketEvent.Inbound.Pre> inboundPacketPreListeners = new Listeners<>();

	public static void addInboundPacketPreListener(Consumer<PacketEvent.Inbound.Pre> listener) {
		inboundPacketPreListeners.add(listener);
	}

	@SubscribeEvent
	public void onPacketInboundPre(PacketEvent.Inbound.Pre e) {
		inboundPacketPreListeners.invoke(e);
	}

	public static Packet<?> firePacketInboundPre(NetworkManager netManager, Packet<?> packet) {
		if (netManager == null) {
			if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
				return packet;
			}
		} else {
			if (netManager.getDirection() == EnumPacketDirection.SERVERBOUND) {
				return packet;
			}
		}
		PacketEvent.Inbound.Pre event = new PacketEvent.Inbound.Pre(packet);
		MinecraftForge.EVENT_BUS.post(event);
		return event.isCanceled() ? null : event.getPacket();
	}

	// INBOUND PACKET POST

	private static Listeners<PacketEvent.Inbound.Post> inboundPacketPostListeners = new Listeners<>();

	public static void addInboundPacketPostListener(Consumer<PacketEvent.Inbound.Post> listener) {
		inboundPacketPostListeners.add(listener);
	}

	@SubscribeEvent
	public void onPacketInboundPost(PacketEvent.Inbound.Post e) {
		inboundPacketPostListeners.invoke(e);
	}

	public static void firePacketInboundPost(NetworkManager netManager, Packet<?> packet) {
		if (netManager == null) {
			if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
				return;
			}
		} else {
			if (netManager.getDirection() == EnumPacketDirection.SERVERBOUND) {
				return;
			}
		}
		MinecraftForge.EVENT_BUS.post(new PacketEvent.Inbound.Post(packet));
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
