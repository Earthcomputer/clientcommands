package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IArmorStandEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArmorStandEntity.class)
public abstract class MixinArmorStandEntity implements IArmorStandEntity {

    @Accessor("invisible")
    @Override
    public abstract boolean isArmorStandInvisible();

}
