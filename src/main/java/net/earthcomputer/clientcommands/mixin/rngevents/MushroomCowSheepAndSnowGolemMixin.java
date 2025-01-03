package net.earthcomputer.clientcommands.mixin.rngevents;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MushroomCow.class, Sheep.class, SnowGolem.class})
public class MushroomCowSheepAndSnowGolemMixin {
    @SuppressWarnings({"MixinAnnotationTarget", "UnqualifiedMemberReference", "UnresolvedMixinReference"}) // mcdev doesn't understand unqualified @At references
    @Inject(
        method = "mobInteract",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/world/item/Items;SHEARS:Lnet/minecraft/world/item/Item;")),
        at = {
            @At(value = "INVOKE", target = "level()Lnet/minecraft/world/level/Level;", ordinal = 0, remap = false),
            @At(value = "INVOKE", target = "method_37908()Lnet/minecraft/class_1937;", ordinal = 0, remap = false),
        }
    )
    public void onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onItemDamage(1, player, player.getItemInHand(hand));
    }
}
