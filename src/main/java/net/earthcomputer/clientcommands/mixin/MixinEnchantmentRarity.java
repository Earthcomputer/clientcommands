package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.Rarity.class)
public class MixinEnchantmentRarity {

    @Inject(method = "getWeight", at = @At("HEAD"), cancellable = true)
    public void injectGetWeight(CallbackInfoReturnable<Integer> ci) {
        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        if (protocolVersion >= MultiVersionCompat.V1_14 && protocolVersion <= MultiVersionCompat.V1_14_2) {
            int ret = switch ((Enchantment.Rarity) (Object) this) {
                case COMMON -> 30;
                case UNCOMMON -> 10;
                case RARE -> 3;
                case VERY_RARE -> 1;
            };
            ci.setReturnValue(ret);
        }
    }

}
