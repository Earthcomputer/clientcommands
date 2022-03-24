package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.entry.CombinedEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CombinedEntry.class)
public interface CombinedEntryAccessor {
    @Accessor
    LootPoolEntry[] getChildren();
}
