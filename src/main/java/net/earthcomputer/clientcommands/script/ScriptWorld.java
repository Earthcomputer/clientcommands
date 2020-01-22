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
import net.minecraft.world.LightType;

@SuppressWarnings("unused")
public class ScriptWorld {

    ScriptWorld() {}

    private static ClientWorld getWorld() {
        return MinecraftClient.getInstance().world;
    }

    public String getDimension() {
        return ScriptUtil.simplifyIdentifier(Registry.DIMENSION_TYPE.getId(getWorld().dimension.getType()));
    }

    public String getBlock(int x, int y, int z) {
        Block block = getWorld().getBlockState(new BlockPos(x, y, z)).getBlock();
        return ScriptUtil.simplifyIdentifier(Registry.BLOCK.getId(block));
    }

    public Object getBlockProperty(int x, int y, int z, String property) {
        return getBlockState(x, y, z).getProperty(property);
    }

    public ScriptBlockState getBlockState(int x, int y, int z) {
        return new ScriptBlockState(getWorld().getBlockState(new BlockPos(x, y, z)));
    }

    public Object getBlockEntityNbt(int x, int y, int z) {
        BlockEntity be = getWorld().getBlockEntity(new BlockPos(x, y, z));
        if (be == null)
            return null;
        return ScriptUtil.fromNbt(be.toTag(new CompoundTag()));
    }

    public int getBlockLight(int x, int y, int z) {
        return getWorld().getLightLevel(LightType.BLOCK, new BlockPos(x, y, z));
    }

    public int getSkyLight(int x, int y, int z) {
        return getWorld().getLightLevel(LightType.SKY, new BlockPos(x, y, z));
    }
}
