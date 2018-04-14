package net.earthcomputer.clientcommands;

import org.lwjgl.input.Keyboard;

import net.earthcomputer.clientcommands.network.NetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class SpecialActionKey {

	private SpecialActionKey() {
	}

	private static final KeyBinding KEY_BINDING = new KeyBinding("key.specialAction", new IKeyConflictContext() {
		@Override
		public boolean isActive() {
			return KeyConflictContext.IN_GAME.isActive();
		}

		@Override
		public boolean conflicts(IKeyConflictContext other) {
			return false;
		}
	}, Keyboard.KEY_LMENU, "key.categories.misc");

	private static boolean isSpecialActionKeyPressed() {
		return Keyboard.isKeyDown(KEY_BINDING.getKeyCode());
	}

	public static void registerKeyBinding() {
		ClientRegistry.registerKeyBinding(KEY_BINDING);
	}

	public static void registerEvents() {
		EventManager.addOutboundPacketPreListener(e -> {
			Packet<?> packet = e.getPacket();
			if (packet instanceof CPacketPlayerTryUseItemOnBlock) {
				if (isSpecialActionKeyPressed()) {
					EntityPlayerSP player = Minecraft.getMinecraft().player;
					NetUtils.sendPacket(new CPacketPlayer.Rotation(MathHelper.wrapDegrees(player.rotationYaw + 180),
							-player.rotationPitch, player.onGround));
				}
			}
		});
		EventManager.addOutboundPacketPostListener(e -> {
			Packet<?> packet = e.getPacket();
			if (packet instanceof CPacketPlayerTryUseItemOnBlock) {
				if (isSpecialActionKeyPressed()) {
					EntityPlayerSP player = Minecraft.getMinecraft().player;
					NetUtils.sendPacket(
							new CPacketPlayer.Rotation(player.rotationYaw, player.rotationPitch, player.onGround));
				}
			}
		});
	}

}
