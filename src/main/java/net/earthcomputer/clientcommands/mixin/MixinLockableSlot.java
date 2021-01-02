package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.ICreativeSlot;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen$LockableSlot")
public class MixinLockableSlot implements ICreativeSlot {
}
