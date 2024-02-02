package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IEditBox;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public abstract class MixinEditBox implements IEditBox {

    @Accessor("maxLength")
    @Override
    public abstract int clientcommands_getMaxLength();
}
