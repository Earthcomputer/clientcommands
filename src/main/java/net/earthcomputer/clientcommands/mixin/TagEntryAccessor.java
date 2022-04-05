package net.earthcomputer.clientcommands.mixin;

import net.minecraft.item.Item;
import net.minecraft.loot.entry.TagEntry;
import net.minecraft.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagEntry.class)
public interface TagEntryAccessor {
    @Accessor
    TagKey<Item> getName();
}
