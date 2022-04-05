package net.earthcomputer.clientcommands.mixin;

import net.minecraft.loot.entry.LootTableEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootTableEntry.class)
public interface LootTableEntryAccessor {
    @Accessor
    Identifier getId();
}
