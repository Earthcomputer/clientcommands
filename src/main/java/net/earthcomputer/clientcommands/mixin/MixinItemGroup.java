package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IItemGroup;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemGroup.class)
public abstract class MixinItemGroup implements IItemGroup {

    @Shadow @Final @Mutable public static ItemGroup[] GROUPS;

    @Override
    public void shrink(int index) {
        ItemGroup[] tempGroups = GROUPS;
        GROUPS = new ItemGroup[index];
        System.arraycopy(tempGroups, 0, GROUPS, 0, GROUPS.length);
    }

    @Override
    public int getLength() {
        return GROUPS.length;
    }
}
