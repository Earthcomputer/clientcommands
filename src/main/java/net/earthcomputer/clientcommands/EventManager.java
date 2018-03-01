package net.earthcomputer.clientcommands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.earthcomputer.clientcommands.ToolDamageManager.ToolDamagedEvent;
import net.earthcomputer.clientcommands.command.CommandRelog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent;
import net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import net.minecraftforge.fml.relauncher.Side;

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

	// GUI INIT

	private static Listeners<InitGuiEvent.Post> initGuiListeners = new Listeners<>();

	public static void addInitGuiListener(Consumer<InitGuiEvent.Post> listener) {
		initGuiListeners.add(listener);
	}

	@SubscribeEvent
	public void onInitGui(InitGuiEvent.Post e) {
		initGuiListeners.invoke(e);
	}

	// GUI ACTION PERFORMED

	private static Listeners<ActionPerformedEvent.Pre> guiActionPerformedListeners = new Listeners<>();

	public static void addGuiActionPerformedListener(Consumer<ActionPerformedEvent.Pre> listener) {
		guiActionPerformedListeners.add(listener);
	}

	@SubscribeEvent
	public void onGuiActionPerformed(ActionPerformedEvent.Pre e) {
		guiActionPerformedListeners.invoke(e);
	}

	// GUI OVERLAY

	private static Listeners<DrawScreenEvent.Post> guiOverlayListeners = new Listeners<>();

	public static void addGuiOverlayListener(Consumer<DrawScreenEvent.Post> listener) {
		guiOverlayListeners.add(listener);
	}

	@SubscribeEvent
	public void onGuiOverlay(DrawScreenEvent.Post e) {
		guiOverlayListeners.invoke(e);
	}

	// CHAT SENT

	private static Listeners<ClientChatEvent> chatSentListeners = new Listeners<>();

	public static void addChatSentListener(Consumer<ClientChatEvent> listener) {
		chatSentListeners.add(listener);
	}

	@SubscribeEvent
	public void onChatSent(ClientChatEvent e) {
		chatSentListeners.invoke(e);
	}

	// CHAT RECEIVED

	private static Listeners<ClientChatReceivedEvent> chatReceivedListeners = new Listeners<>();

	public static void addChatReceivedListener(Consumer<ClientChatReceivedEvent> listener) {
		chatReceivedListeners.add(listener);
	}

	@SubscribeEvent
	public void onChatReceived(ClientChatReceivedEvent e) {
		chatReceivedListeners.invoke(e);
	}

	// ENTITY CONSTRUCTING

	private static Listeners<EntityConstructing> entityConstructingListeners = new Listeners<>();

	public static void addEntityConstructingListener(Consumer<EntityConstructing> listener) {
		entityConstructingListeners.add(listener);
	}

	@SubscribeEvent
	public void onEntityConstructing(EntityConstructing e) {
		entityConstructingListeners.invoke(e);
	}

	// ENTITY JOIN WORLD

	private static Listeners<EntityJoinWorldEvent> entitySpawnListeners = new Listeners<>();

	public static void addEntitySpawnListener(Consumer<EntityJoinWorldEvent> listener) {
		entitySpawnListeners.add(listener);
	}

	@SubscribeEvent
	public void onEntitySpawn(EntityJoinWorldEvent e) {
		if (Minecraft.getMinecraft().player != null && e.getWorld().isRemote) {
			entitySpawnListeners.invoke(e);
		}
	}

	// PLAYER TICK

	private static Listeners<PlayerTickEvent> playerTickListeners = new Listeners<>();

	public static void addPlayerTickListener(Consumer<PlayerTickEvent> listener) {
		playerTickListeners.add(listener);
	}

	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent e) {
		if (e.side == Side.CLIENT && e.phase == TickEvent.Phase.END) {
			playerTickListeners.invoke(e);
		}
	}

	// LIVING ATTACK

	private static Listeners<LivingAttackEvent> livingAttackListeners = new Listeners<>();

	public static void addLivingAttackListener(Consumer<LivingAttackEvent> listener) {
		livingAttackListeners.add(listener);
	}

	@SubscribeEvent
	public void onLivingAttack(LivingAttackEvent e) {
		livingAttackListeners.invoke(e);
	}

	// ANVIL REPAIR

	private static Listeners<AnvilRepairEvent> anvilRepairListeners = new Listeners<>();

	public static void addAnvilRepairListener(Consumer<AnvilRepairEvent> listener) {
		anvilRepairListeners.add(listener);
	}

	@SubscribeEvent
	public void onAnvilRepair(AnvilRepairEvent e) {
		if (e.getEntityPlayer() == Minecraft.getMinecraft().player) {
			anvilRepairListeners.invoke(e);
		}
	}

	// ATTACK BLOCK

	private static Listeners<LeftClickBlock> attackBlockListeners = new Listeners<>();

	public static void addAttackBlockListener(Consumer<LeftClickBlock> listener) {
		attackBlockListeners.add(listener);
	}

	@SubscribeEvent
	public void onAttackBlock(LeftClickBlock e) {
		if (e.getSide() == Side.CLIENT) {
			attackBlockListeners.invoke(e);
		}
	}

	// ATTACK ENTITY

	private static Listeners<AttackEntityEvent> attackEntityListeners = new Listeners<>();

	public static void addAttackEntityListener(Consumer<AttackEntityEvent> listener) {
		attackEntityListeners.add(listener);
	}

	@SubscribeEvent
	public void onAttackEntity(AttackEntityEvent e) {
		if (e.getEntityPlayer() == Minecraft.getMinecraft().player) {
			attackEntityListeners.invoke(e);
		}
	}

	// USE BLOCK

	private static Listeners<RightClickBlock> useBlockListeners = new Listeners<>();

	public static void addUseBlockListener(Consumer<RightClickBlock> listener) {
		useBlockListeners.add(listener);
	}

	@SubscribeEvent
	public void onUseBlock(RightClickBlock e) {
		if (e.getSide() == Side.CLIENT) {
			useBlockListeners.invoke(e);
		}
	}

	// USE ITEM

	private static Listeners<RightClickItem> useItemListeners = new Listeners<>();

	public static void addUseItemListener(Consumer<RightClickItem> listener) {
		useItemListeners.add(listener);
	}

	@SubscribeEvent
	public void onUseItem(RightClickItem e) {
		if (e.getSide() == Side.CLIENT) {
			useItemListeners.invoke(e);
		}
	}

	// STOP USE ITEM

	private static Listeners<LivingEntityUseItemEvent.Stop> stopUseItemListeners = new Listeners<>();

	public static void addStopUseItemListener(Consumer<LivingEntityUseItemEvent.Stop> listener) {
		stopUseItemListeners.add(listener);
	}

	@SubscribeEvent
	public void onStopUseItem(LivingEntityUseItemEvent.Stop e) {
		if (e.getEntity() == Minecraft.getMinecraft().player) {
			stopUseItemListeners.invoke(e);
		}
	}

	// USE ENTITY

	private static Listeners<EntityInteract> useEntityListeners = new Listeners<>();

	public static void addUseEntityListener(Consumer<EntityInteract> listener) {
		useEntityListeners.add(listener);
	}

	@SubscribeEvent
	public void onUseEntity(EntityInteract e) {
		if (e.getSide() == Side.CLIENT) {
			useEntityListeners.invoke(e);
		}
	}

	// FIRE BOW

	private static Listeners<ArrowLooseEvent> fireBowListeners = new Listeners<>();

	public static void addFireBowListener(Consumer<ArrowLooseEvent> listener) {
		fireBowListeners.add(listener);
	}

	@SubscribeEvent
	public void onFireBow(ArrowLooseEvent e) {
		if (e.getEntityPlayer() == Minecraft.getMinecraft().player) {
			fireBowListeners.invoke(e);
		}
	}

	// PRE DAMAGE ITEM

	private static Listeners<ToolDamagedEvent.Pre> preDamageItemListeners = new Listeners<>();

	public static void addPreDamageItemListener(Consumer<ToolDamagedEvent.Pre> listener) {
		preDamageItemListeners.add(listener);
	}

	@SubscribeEvent
	public void onPreDamageItem(ToolDamagedEvent.Pre e) {
		preDamageItemListeners.invoke(e);
	}

	// POST DAMAGE ITEM

	private static Listeners<ToolDamagedEvent.Post> postDamageItemListeners = new Listeners<>();

	public static void addPostDamageItemListener(Consumer<ToolDamagedEvent.Post> listener) {
		postDamageItemListeners.add(listener);
	}

	@SubscribeEvent
	public void onPostDamageItem(ToolDamagedEvent.Post e) {
		postDamageItemListeners.invoke(e);
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
