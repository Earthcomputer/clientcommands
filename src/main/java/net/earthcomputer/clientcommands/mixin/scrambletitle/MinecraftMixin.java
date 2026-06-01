package net.earthcomputer.clientcommands.mixin.scrambletitle;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.earthcomputer.clientcommands.ClientCommands;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    // Earth annoying his friends <3 nothing to see here
    @ModifyReturnValue(method = "createTitle", at = @At("RETURN"))
    private String modifyWindowTitle(String original) {
        if (ClientCommands.scrambleWindowTitle) {
            List<Character> chars = original.chars().mapToObj(c -> (char) c).collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(chars);
            return chars.stream().map(String::valueOf).collect(Collectors.joining());
        }

        return original;
    }
}
