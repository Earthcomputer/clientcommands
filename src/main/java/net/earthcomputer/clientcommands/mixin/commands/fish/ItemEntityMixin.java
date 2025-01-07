package net.earthcomputer.clientcommands.mixin.commands.fish;

import net.earthcomputer.clientcommands.features.FishingCracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    @Shadow public abstract ItemStack getItem();

    public ItemEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "onSyncedDataUpdated", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setEntityRepresentation(Lnet/minecraft/world/entity/Entity;)V"))
    private void onStackSet(CallbackInfo ci) {
        if (level().isClientSide && FishingCracker.canManipulateFishing()) {
            FishingCracker.processItemSpawn(position(), getItem());
        }
    }

}
