package net.earthcomputer.clientcommands.script;

import com.google.common.collect.Lists;
import net.earthcomputer.clientcommands.interfaces.IBlock;
import net.earthcomputer.clientcommands.interfaces.IFireBlock;
import net.earthcomputer.clientcommands.interfaces.IMaterial;
import net.earthcomputer.clientcommands.mixin.BlockSettingsAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public class ScriptBlockState {

    static ScriptBlockState defaultState(String block) {
        Identifier id = new Identifier(block);
        if (!Registry.BLOCK.containsId(id))
            throw new IllegalArgumentException("No such block: " + block);
        return new ScriptBlockState(Registry.BLOCK.get(id).getDefaultState());
    }

    BlockState state;

    ScriptBlockState(BlockState state) {
        this.state = state;
    }

    public String getBlock() {
        return ScriptUtil.simplifyIdentifier(Registry.BLOCK.getId(state.getBlock()));
    }

    public Object getProperty(String property) {
        for (Property<?> propertyObj : state.getProperties()) {
            if (propertyObj.getName().equals(property)) {
                Object val = state.get(propertyObj);
                if (val instanceof Boolean)
                    return val;
                if (val instanceof Number)
                    return val;
                else
                    return propertyGetName(propertyObj, val);
            }
        }
        return null;
    }

    public int getLuminance() {
        return state.getLuminance();
    }

    public float getHardness() {
        return ((BlockSettingsAccessor) ((IBlock) state.getBlock()).getSettings()).getHardness();
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
        return ScriptUtil.simplifyIdentifier(state.getBlock().getLootTableId());
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
        return !state.isToolRequired();
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

    public List<String> getTags() {
        //noinspection ConstantConditions
        return Lists.transform(
                new ArrayList<>(MinecraftClient.getInstance().getNetworkHandler().getTagManager().blocks().getTagsFor(state.getBlock())),
                ScriptUtil::simplifyIdentifier);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propertyGetName(Property<T> prop, Object val) {
        return prop.name((T) val);
    }

}
