package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DefaultedList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public class MixinPlayerInventory {

    @Shadow @Final public DefaultedList<ItemStack> main;

    @Inject(method = "offerOrDrop", at = @At("HEAD"))
    public void onOfferOrDrop(World world, ItemStack stack, CallbackInfo ci) {
        if (world.isClient) {
            int stackSize = stack.getCount();
            for (ItemStack item : main) {
                if (item.isEmpty())
                    stackSize -= stack.getMaxCount();
                else if (item.isItemEqual(stack))
                    stackSize -= stack.getMaxCount() - item.getCount();
            }
            if (stackSize > 0) {
                PlayerRandCracker.onDropItem();
            }
        }
    }

}
