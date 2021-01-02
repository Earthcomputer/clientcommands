package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.FishingCracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntity extends Entity {

    @Shadow public abstract ItemStack getStack();

    public MixinItemEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onTrackedDataSet", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;setHolder(Lnet/minecraft/entity/Entity;)V"))
    private void onStackSet(CallbackInfo ci) {
        if (world.isClient && FishingCracker.canManipulateFishing()) {
            FishingCracker.processItemSpawn(getPos(), getStack());
        }
    }

}
