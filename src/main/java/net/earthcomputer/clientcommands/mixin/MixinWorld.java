package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.ClientWeather;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class MixinWorld {

    @Inject(method = "getRainLevel(F)F", at = {@At("HEAD")}, cancellable = true)
    private void onGetRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        float rain = ClientWeather.getRain();
        if (rain > -1) {
            cir.setReturnValue(rain);
        }
    }

    @Inject(method = "getThunderLevel(F)F", at = {@At("HEAD")}, cancellable = true)
    private void onGetThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        float thunder = ClientWeather.getThunder();
        if (thunder > -1) {
            cir.setReturnValue(thunder);
        }
    }

}
