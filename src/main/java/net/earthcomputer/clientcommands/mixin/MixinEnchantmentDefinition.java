package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.EnchantmentDefinition.class)
public class MixinEnchantmentDefinition {
    @Shadow
    @Final
    private int weight;

    @Inject(method = "weight", at = @At("HEAD"), cancellable = true)
    public void injectGetWeight(CallbackInfoReturnable<Integer> ci) {
        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        if (protocolVersion >= MultiVersionCompat.V1_14 && protocolVersion <= MultiVersionCompat.V1_14_2) {
            int ret = switch (weight) {
                case 10 -> 30;
                case 5 -> 10;
                case 2 -> 3;
                case 1 -> 1;
                default -> weight;
            };
            ci.setReturnValue(ret);
        }
    }

}
