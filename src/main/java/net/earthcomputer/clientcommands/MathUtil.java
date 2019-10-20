package net.earthcomputer.clientcommands;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class MathUtil {

    public static Vec3d getClosestPoint(BlockPos blockPos, VoxelShape voxel, Vec3d pos) {
        class Result {
            Vec3d val;
            double distanceSq = Double.POSITIVE_INFINITY;
        }
        Result result = new Result();
        voxel.forEachBox((x1, y1, z1, x2, y2, z2) -> {
            double minX = Math.min(x1 + blockPos.getX(), x2 + blockPos.getX());
            double minY = Math.min(y1 + blockPos.getY(), y2 + blockPos.getY());
            double minZ = Math.min(z1 + blockPos.getZ(), z2 + blockPos.getZ());
            double maxX = Math.max(x1 + blockPos.getX(), x2 + blockPos.getX());
            double maxY = Math.max(y1 + blockPos.getY(), y2 + blockPos.getY());
            double maxZ = Math.max(z1 + blockPos.getZ(), z2 + blockPos.getZ());
            for (Direction face : Direction.values()) {
                // Since the faces are axis aligned, it's a simple clamp operation
                Vec3d val = new Vec3d(face.getAxis() == Direction.Axis.X ? (face.getDirection() == Direction.AxisDirection.NEGATIVE ? minX : maxX) : MathHelper.clamp(pos.x, minX, maxX),
                        face.getAxis() == Direction.Axis.Y ? (face.getDirection() == Direction.AxisDirection.NEGATIVE ? minY : maxY) : MathHelper.clamp(pos.y, minY, maxY),
                        face.getAxis() == Direction.Axis.Z ? (face.getDirection() == Direction.AxisDirection.NEGATIVE ? minZ : maxZ) : MathHelper.clamp(pos.z, minZ, maxZ));
                double distanceSq = val.squaredDistanceTo(pos);
                if (distanceSq < result.distanceSq) {
                    result.val = val;
                    result.distanceSq = distanceSq;
                }
            }
        });
        return result.val;
    }

}
