package net.earthcomputer.clientcommands.render;

import java.util.*;

import com.google.common.collect.Maps;

import net.earthcomputer.clientcommands.EventManager;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import org.apache.commons.lang3.tuple.Pair;

public class RenderSettings {

	public static void registerEvents() {
		EventManager.addDisconnectExceptRelogListener(e -> {
			filters.clear();
		});
	}

	private static Map<Class<? extends Entity>, List<Pair<NBTTagCompound, Boolean>>> filters = Maps.newHashMap();

	public static boolean shouldRender(Entity entity) {
		List<Pair<NBTTagCompound, Boolean>> filters = RenderSettings.filters.get(entity.getClass());
		if (filters == null)
			return true;

		NBTTagCompound nbt = CommandBase.entityToNBT(entity);
		boolean shouldRender = true;

		for (Pair<NBTTagCompound, Boolean> filter : filters) {
			if (NBTUtil.areNBTEquals(filter.getLeft(), nbt, true)) {
				shouldRender = filter.getRight();
			}
		}

		return shouldRender;
	}

	public static void addRenderingFilter(Class<? extends Entity> clazz, NBTTagCompound filter, boolean shouldRender) {
		if (filter.hasNoTags() && shouldRender) {
			RenderSettings.filters.remove(clazz);
			return;
		}

		List<Pair<NBTTagCompound, Boolean>> filters = RenderSettings.filters.computeIfAbsent(clazz, k -> new ArrayList<>());
		filters.removeIf(existingFilter -> NBTUtil.areNBTEquals(filter, existingFilter.getLeft(), true));
		filters.add(Pair.of(filter, shouldRender));
	}

}
