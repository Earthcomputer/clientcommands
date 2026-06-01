package net.earthcomputer.clientcommands.mixin.commands.fish;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin extends Entity {
    @Shadow
    public abstract Player getPlayerOwner();

    public FishingHookMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Definition(id = "min", method = "Ljava/lang/Math;min(II)I")
    @Definition(id = "outOfWaterTime", field = "Lnet/minecraft/world/entity/projectile/FishingHook;outOfWaterTime:I")
    @Expression("min(10, this.outOfWaterTime + 1)")
    @Inject(method = "tick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void onBobOutOfWater(CallbackInfo ci) {
        if (FishingCracker.canManipulateFishing() && level().isClientSide() && getPlayerOwner() == Minecraft.getInstance().player) {
            FishingCracker.onBobOutOfWater();
        }
    }
}
