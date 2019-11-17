package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IMaterial;
import net.minecraft.block.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Material.class)
public class MixinMaterial implements IMaterial {

    @Unique private int id;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(CallbackInfo ci) {
        this.id = nextId.getAndIncrement();
    }

    @Override
    public int clientcommands_getId() {
        return id;
    }
}
