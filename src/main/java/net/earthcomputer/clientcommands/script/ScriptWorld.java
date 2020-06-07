package net.earthcomputer.clientcommands.script;

import net.earthcomputer.clientcommands.MathUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LightType;

@SuppressWarnings("unused")
public class ScriptWorld {

    ScriptWorld() {}

    private static ClientWorld getWorld() {
        return MinecraftClient.getInstance().world;
    }

    public String getDimension() {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        assert networkHandler != null;
        return ScriptUtil.simplifyIdentifier(networkHandler.method_29091().getDimensionTypeRegistry().getId(getWorld().getDimension()));
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

    public ScriptPosition getClosestVisiblePoint(int x, int y, int z) {
        return getClosestVisiblePoint(x, y, z, null);
    }

    public ScriptPosition getClosestVisiblePoint(int x, int y, int z, String side) {
        Vec3d ret = getClosestVisiblePoint0(x, y, z, side, false);
        return ret == null ? null : new ScriptPosition(ret);
    }

    static Vec3d getClosestVisiblePoint0(int x, int y, int z, String side, boolean keepExistingHitResult) {
        BlockPos pos = new BlockPos(x, y, z);
        Direction dir = ScriptUtil.getDirectionFromString(side);

        ClientWorld world = MinecraftClient.getInstance().world;
        BlockState state = world.getBlockState(pos);
        if (state.isAir())
            return null;
        PlayerEntity player = MinecraftClient.getInstance().player;
        Vec3d origin = player.getCameraPosVec(0);
        HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
        Vec3d closestPos;
        if (keepExistingHitResult && hitResult.getType() == HitResult.Type.BLOCK && ((BlockHitResult) hitResult).getBlockPos().equals(pos)) {
            closestPos = hitResult.getPos();
        } else {
            closestPos = MathUtil.getClosestVisiblePoint(world, pos, origin, player, dir);
        }
        if (closestPos != null && origin.squaredDistanceTo(closestPos) > 6 * 6) {
            return null;
        }
        return closestPos;
    }

}
