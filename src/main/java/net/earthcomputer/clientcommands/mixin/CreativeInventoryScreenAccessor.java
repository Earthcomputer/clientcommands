package net.earthcomputer.clientcommands.mixin;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen")
public interface CreativeInventoryScreenAccessor {

    @Accessor("selectedTab")
    static void setSelectedTab(int selectedTab) {
        throw new AssertionError();
    }

    @SuppressWarnings("AccessorTarget")
    @Dynamic @Accessor(value = "fabric_currentPage", remap = false)
    static void setFabricCurrentPage(int index) {
        throw new AssertionError();
    }
}
