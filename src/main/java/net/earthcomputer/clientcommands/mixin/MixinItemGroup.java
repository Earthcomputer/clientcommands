package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemGroup.class)
public abstract class MixinItemGroup implements IItemGroup {

    @Shadow @Mutable @Final public static ItemGroup[] GROUPS;

    @Shadow @Mutable @Final private Text translationKey;

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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void substring(int index, String id, CallbackInfo ci) {
        if (id.contains("clientcommands.")) {
            this.translationKey = new LiteralText(id.substring(15));
        }
    }
}
