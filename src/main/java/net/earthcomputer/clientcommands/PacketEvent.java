package net.earthcomputer.clientcommands;

import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

public class PacketEvent extends Event {

	protected Packet<?> packet;

	public PacketEvent(Packet<?> packet) {
		this.packet = packet;
	}

	public Packet<?> getPacket() {
		return packet;
	}

	public static class Outbound extends PacketEvent {

		public Outbound(Packet<?> packet) {
			super(packet);
		}

		@Cancelable
		public static class Pre extends Outbound {

			public Pre(Packet<?> packet) {
				super(packet);
			}

			public void setPacket(Packet<?> packet) {
				this.packet = packet;
			}

		}

		public static class Post extends Outbound {

			public Post(Packet<?> packet) {
				super(packet);
			}

		}

	}

	public static class Inbound extends PacketEvent {

		public Inbound(Packet<?> packet) {
			super(packet);
		}

		@Cancelable
		public static class Pre extends Inbound {

			public Pre(Packet<?> packet) {
				super(packet);
			}

			public void setPacket(Packet<?> packet) {
				this.packet = packet;
			}

		}

		public static class Post extends Inbound {

			public Post(Packet<?> packet) {
				super(packet);
			}

		}

	}

}
