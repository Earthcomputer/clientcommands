package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IKeyBinding;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public abstract class MixinKeyBinding implements IKeyBinding {

    @Accessor
    @Override
    public abstract void setPressed(boolean pressed);
}
