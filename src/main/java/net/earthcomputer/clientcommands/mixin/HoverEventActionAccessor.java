package net.earthcomputer.clientcommands.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HoverEvent.Action.class)
public interface HoverEventActionAccessor {
    @Accessor
    Codec<HoverEvent.TypedHoverEvent<?>> getLegacyCodec();
}
