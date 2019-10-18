package net.earthcomputer.clientcommands.script;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

public class ScriptWorld {

    ScriptWorld() {}

    private static ClientWorld getWorld() {
        return MinecraftClient.getInstance().world;
    }

    public String getBlock(int x, int y, int z) {
        Block block = getWorld().getBlockState(new BlockPos(x, y, z)).getBlock();
        return ScriptUtil.simplifyIdentifier(Registry.BLOCK.getId(block));
    }

    public Object getBlockProperty(int x, int y, int z, String property) {
        BlockState state = getWorld().getBlockState(new BlockPos(x, y, z));
        for (Property<?> propertyObj : state.getProperties()) {
            if (propertyObj.getName().equals(property)) {
                Object val = state.get(propertyObj);
                if (val instanceof Number)
                    return val;
                else
                    return propertyGetName(propertyObj, val);
            }
        }
        return null;
    }

    public Object getBlockEntityNbt(int x, int y, int z) {
        BlockEntity be = getWorld().getBlockEntity(new BlockPos(x, y, z));
        if (be == null)
            return null;
        return ScriptUtil.fromNbt(be.toTag(new CompoundTag()));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propertyGetName(Property<T> prop, Object val) {
        return prop.getName((T) val);
    }
}
