package net.earthcomputer.clientcommands.script;

import com.google.common.collect.Lists;
import net.earthcomputer.clientcommands.interfaces.IBlock;
import net.earthcomputer.clientcommands.interfaces.IFireBlock;
import net.earthcomputer.clientcommands.interfaces.IMaterial;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.state.property.Property;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public class ScriptBlockInfo {

    private BlockState state;

    ScriptBlockInfo(BlockState state) {
        this.state = state;
    }

    public int getLuminance() {
        return state.getLuminance();
    }

    public float getHardness() {
        return ((IBlock) state.getBlock()).getHardness();
    }

    public float getBlastResistance() {
        return state.getBlock().getBlastResistance();
    }

    public boolean isRandomTickable() {
        return state.hasRandomTicks();
    }

    public float getSlipperiness() {
        return state.getBlock().getSlipperiness();
    }

    public List<String> getStateProperties() {
        return Lists.transform(new ArrayList<>(state.getProperties()), Property::getName);
    }

    public String getLootTable() {
        return ScriptUtil.simplifyIdentifier(state.getBlock().getDropTableId());
    }

    public String getTranslationKey() {
        return state.getBlock().getTranslationKey();
    }

    public String getItem() {
        return ScriptUtil.simplifyIdentifier(Registry.ITEM.getId(state.getBlock().asItem()));
    }

    public int getMaterialId() {
        //noinspection ConstantConditions
        return ((IMaterial) (Object) state.getMaterial()).clientcommands_getId();
    }

    public int getMapColor() {
        try {
            return state.getTopMaterialColor(null, null).color;
        } catch (Throwable e) {
            return state.getMaterial().getColor().color;
        }
    }

    public String getPistonBehavior() {
        return state.getPistonBehavior().name().toLowerCase(Locale.ENGLISH);
    }

    public boolean isFlammable() {
        return state.getMaterial().isBurnable();
    }

    public boolean isCanBreakByHand() {
        return state.getMaterial().canBreakByHand();
    }

    public boolean isLiquid() {
        return state.getMaterial().isLiquid();
    }

    public boolean isBlocksLight() {
        return state.getMaterial().blocksLight();
    }

    public boolean isReplaceable() {
        return state.getMaterial().isReplaceable();
    }

    public boolean isSolid() {
        return state.getMaterial().isSolid();
    }

    public int getBurnChance() {
        return ((IFireBlock) Blocks.FIRE).callGetBurnChance(state);
    }

    public int getSpreadChance() {
        return ((IFireBlock) Blocks.FIRE).callGetSpreadChance(state);
    }

    public boolean isFallable() {
        return state.getBlock() instanceof FallingBlock;
    }

}
