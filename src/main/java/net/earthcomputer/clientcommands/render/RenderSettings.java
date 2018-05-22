package net.earthcomputer.clientcommands.render;

import java.util.Set;

import com.google.common.collect.Sets;

import net.earthcomputer.clientcommands.EventManager;
import net.minecraft.entity.Entity;

public class RenderSettings {

	public static void registerEvents() {
		EventManager.addDisconnectExceptRelogListener(e -> {
			entitiesDisabled.clear();
		});
	}

	private static Set<Class<? extends Entity>> entitiesDisabled = Sets.newIdentityHashSet();

	public static boolean isEntityRenderingDisabled(Class<? extends Entity> clazz) {
		return entitiesDisabled.contains(clazz);
	}

	public static void enableEntityRendering(Class<? extends Entity> clazz) {
		entitiesDisabled.remove(clazz);
	}

	public static void disableEntityRendering(Class<? extends Entity> clazz) {
		entitiesDisabled.add(clazz);
	}

}
