package net.earthcomputer.clientcommands.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

public class DebugUtil {

	private DebugUtil() {
	}

	public static EntityPlayerMP getPlayerMP() {
		return Minecraft.getMinecraft().getIntegratedServer().getPlayerList()
				.getPlayerByUUID(Minecraft.getMinecraft().player.getUniqueID());
	}

}
