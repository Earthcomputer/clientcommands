package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.ISlot;
import net.minecraft.container.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public abstract class MixinSlot implements ISlot {
    @Accessor
    @Override
    public abstract int getInvSlot();
}
