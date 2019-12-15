package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.script.ScriptManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void preTick(boolean inSneakingPose, CallbackInfo ci) {
        if (ScriptManager.blockingInput()) {
            Input _this = (Input) (Object) this;
            _this.pressingForward = _this.pressingBack = _this.pressingLeft = _this.pressingRight = _this.jumping = _this.sneaking = false;
            ScriptManager.copyScriptInputToPlayer(inSneakingPose);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(boolean inSneakingPose, CallbackInfo ci) {
        ScriptManager.copyScriptInputToPlayer(inSneakingPose);
    }

}
