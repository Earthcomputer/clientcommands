package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.interfaces.ICreativeSlot;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$CustomCreativeSlot")
public class CustomCreativeSlotMixin implements ICreativeSlot {
}
