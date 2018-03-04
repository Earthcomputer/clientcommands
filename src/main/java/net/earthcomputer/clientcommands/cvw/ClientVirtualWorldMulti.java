package net.earthcomputer.clientcommands.cvw;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.village.VillageCollection;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.border.IBorderListener;
import net.minecraft.world.border.WorldBorder;

public class ClientVirtualWorldMulti extends ClientVirtualWorld {

	private ClientVirtualWorld delegate;
	private IBorderListener borderListener;

	public ClientVirtualWorldMulti(ClientVirtualServer server, int dimensionId, WorldSettings settings,
			Long2ObjectMap<NBTTagCompound> chunks, NBTTagCompound playerTag, ClientVirtualWorld delegate) {
		super(server, dimensionId, settings, chunks, playerTag);
		this.delegate = delegate;
		this.borderListener = new IBorderListener() {
			@Override
			public void onSizeChanged(WorldBorder border, double newSize) {
				getWorldBorder().setTransition(newSize);
			}

			@Override
			public void onTransitionStarted(WorldBorder border, double oldSize, double newSize, long time) {
				getWorldBorder().setTransition(oldSize, newSize, time);
			}

			@Override
			public void onCenterChanged(WorldBorder border, double x, double z) {
				getWorldBorder().setCenter(x, z);
			}

			@Override
			public void onWarningTimeChanged(WorldBorder border, int newTime) {
				getWorldBorder().setWarningTime(newTime);
			}

			@Override
			public void onWarningDistanceChanged(WorldBorder border, int newDistance) {
				getWorldBorder().setWarningDistance(newDistance);
			}

			@Override
			public void onDamageAmountChanged(WorldBorder border, double newAmount) {
				getWorldBorder().setDamageAmount(newAmount);
			}

			@Override
			public void onDamageBufferChanged(WorldBorder border, double newSize) {
				getWorldBorder().setDamageBuffer(newSize);
			}
		};

		delegate.getWorldBorder().addListener(borderListener);
	}

	@Override
	public World init() {
		mapStorage = delegate.getMapStorage();
		worldScoreboard = delegate.getScoreboard();
		lootTable = delegate.getLootTableManager();
		advancementManager = delegate.getAdvancementManager();

		String villageCollectionName = VillageCollection.fileNameForProvider(provider);
		villageCollection = new VillageCollection(this);
		perWorldStorage.setData(villageCollectionName, villageCollection);

		initCapabilities();
		return this;
	}

	@Override
	public void flush() {
		super.flush();
		delegate.getWorldBorder().removeListener(borderListener);
	}

}
