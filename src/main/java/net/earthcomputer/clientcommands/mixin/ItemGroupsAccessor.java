package net.earthcomputer.clientcommands.mixin;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemGroups.class)
public interface ItemGroupsAccessor {
    @Accessor("GROUPS")
    @Mutable
    static void setGroups(List<ItemGroup> groups) {
    }
}
