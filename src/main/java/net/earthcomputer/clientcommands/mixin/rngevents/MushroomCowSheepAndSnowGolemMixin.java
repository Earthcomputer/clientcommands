package net.earthcomputer.clientcommands.mixin.rngevents;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MushroomCow.class, Sheep.class, SnowGolem.class})
public class MushroomCowSheepAndSnowGolemMixin {
    // require = 0 because NeoForge (e.g. via Sinytra Connector) moves Sheep and SnowGolem shearing out of mobInteract
    @Definition(id = "ServerLevel", type = ServerLevel.class)
    @Expression("? instanceof ServerLevel")
    @Inject(
        method = "mobInteract",
        at = @At("MIXINEXTRAS:EXPRESSION:LAST"),
        slice = @Slice(to = @At(value = "FIELD", target = "Lnet/minecraft/world/level/gameevent/GameEvent;SHEAR:Lnet/minecraft/core/Holder$Reference;", opcode = Opcodes.GETSTATIC)),
        require = 0
    )
    public void onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onItemDamage(1, player, player.getItemInHand(hand));
    }
}
