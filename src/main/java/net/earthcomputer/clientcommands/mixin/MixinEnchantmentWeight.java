package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.multiconnect.api.MultiConnectAPI;
import net.earthcomputer.multiconnect.api.Protocols;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.Weight.class)
public class MixinEnchantmentWeight {

    @Inject(method = "getWeight", at = @At("HEAD"), cancellable = true)
    public void injectGetWeight(CallbackInfoReturnable<Integer> ci) {
        int protocolVersion = MultiConnectAPI.instance().getProtocolVersion();
        if (protocolVersion >= Protocols.V1_14 && protocolVersion <= Protocols.V1_14_2) {
            int ret;
            switch ((Enchantment.Weight) (Object) this) {
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
