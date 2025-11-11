package net.earthcomputer.clientcommands.mixin.rngevents;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MushroomCow.class, Sheep.class, SnowGolem.class})
public class MushroomCowSheepAndSnowGolemMixin {
    @Definition(id = "ServerLevel", type = ServerLevel.class)
    @Expression("? instanceof ServerLevel")
    @Inject(method = "mobInteract", at = @At("MIXINEXTRAS:EXPRESSION"))
    public void onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onItemDamage(1, player, player.getItemInHand(hand));
    }
}
