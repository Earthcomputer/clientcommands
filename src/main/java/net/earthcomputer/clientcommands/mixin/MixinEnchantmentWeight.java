package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.MulticonnectCompat;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.Rarity.class)
public class MixinEnchantmentWeight {

    @Inject(method = "getWeight", at = @At("HEAD"), cancellable = true)
    public void injectGetWeight(CallbackInfoReturnable<Integer> ci) {
        int protocolVersion = MulticonnectCompat.getProtocolVersion();
        if (protocolVersion >= MulticonnectCompat.V1_14 && protocolVersion <= MulticonnectCompat.V1_14_2) {
            int ret;
            switch ((Enchantment.Rarity) (Object) this) {
                case COMMON:
                    ret = 30;
                    break;
                case UNCOMMON:
                    ret = 10;
                    break;
                case RARE:
                    ret = 3;
                    break;
                case VERY_RARE:
                    ret = 1;
                    break;
                default:
                    return;
            }
            ci.setReturnValue(ret);
        }
    }

}
