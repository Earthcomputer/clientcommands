package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(ItemGroup.class)
public abstract class MixinItemGroup implements IItemGroup {

    @Shadow @Mutable @Final public static ItemGroup[] GROUPS;

    @Shadow @Mutable @Final private Text displayName;

    @Override
    public void shrink() {
        GROUPS = Arrays.stream(GROUPS).filter(itemGroup -> !itemGroup.getName().startsWith("clientcommands.")).toArray(ItemGroup[]::new);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void substring(int index, String id, CallbackInfo ci) {
        if (id.startsWith("clientcommands.")) {
            this.displayName = Text.literal(id.substring(15));
        }
    }
}
