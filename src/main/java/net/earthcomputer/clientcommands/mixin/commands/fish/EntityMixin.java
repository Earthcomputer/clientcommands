package net.earthcomputer.clientcommands.mixin.commands.fish;

import net.earthcomputer.clientcommands.features.FishingCracker;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private Level level;

    @Shadow
    private Vec3 position;

    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("RETURN"))
    private void onItemStackSet(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        if (level.isClientSide() && (Object) this instanceof ItemEntity item && accessor == ItemEntity.DATA_ITEM && FishingCracker.canManipulateFishing()) {
            FishingCracker.processItemSpawn(position, item.getItem());
        }
    }
}
