package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IArmorStand;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArmorStand.class)
public abstract class MixinArmorStand implements IArmorStand {

    @Accessor("invisible")
    @Override
    public abstract boolean isArmorStandInvisible();

}
