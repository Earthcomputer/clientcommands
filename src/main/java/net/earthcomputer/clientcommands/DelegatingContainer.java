package net.earthcomputer.clientcommands;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class DelegatingContainer extends Container {

	@Proxy
	private Container delegate;

	public DelegatingContainer(Container delegate) {
		this.delegate = delegate;
		this.windowId = delegate.windowId;
		this.inventoryItemStacks = delegate.inventoryItemStacks;
		this.inventorySlots = delegate.inventorySlots;
		this.listeners = ReflectionHelper.getPrivateValue(Container.class, delegate, "listeners", "field_75149_d");
	}

	@Override
	public boolean canInteractWith(EntityPlayer playerIn) {
		return delegate.canInteractWith(playerIn);
	}

}
