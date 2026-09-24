package net.earthcomputer.clientcommands.mixin.commands.enchant;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin extends AbstractContainerMenuMixin {
    @Shadow
    @Final
    private DataSlot enchantmentSeed;

    @Override
    protected void clientcommands_onEnchantmentSetData(DataSlot slot) {
        if (slot == enchantmentSeed && EnchantmentCracker.isEnchantingPredictionEnabled()) {
            EnchantmentCracker.checkXpSeedState((EnchantmentMenu) (Object) this);
        }
    }
}
