package net.earthcomputer.clientcommands;

import com.google.common.collect.Iterables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public class MathUtil {

    private static final double EPSILON = 0.001;

    public static Vec3 getClosestPoint(BlockPos blockPos, VoxelShape voxel, Vec3 pos) {
        return getClosestPoint(blockPos, voxel, pos, null);
    }

    public static Vec3 getClosestPoint(BlockPos blockPos, VoxelShape voxel, Vec3 pos, Direction dir) {
        ClosestPosResult result = new ClosestPosResult();
        Direction[] dirs = dir == null ? Direction.values() : new Direction[] {dir};
        voxel.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            AABB box = new AABB(x1, y1, z1, x2, y2, z2).move(blockPos);
            for (Direction face : dirs) {
                AABB faceBox = getFace(box, face);
                // Since the faces are axis aligned, it's a simple clamp operation
                Vec3 val = new Vec3(Mth.clamp(pos.x, faceBox.minX, faceBox.maxX),
                        Mth.clamp(pos.y, faceBox.minY, faceBox.maxY),
                        Mth.clamp(pos.z, faceBox.minZ, faceBox.maxZ));
                double distanceSq = val.distanceToSqr(pos);
                if (distanceSq < result.distanceSq) {
                    result.val = val;
                    result.distanceSq = distanceSq;
                }
            }
        });
        return result.val;
    }

    public static Vec3 getClosestVisiblePoint(Level world, BlockPos targetPos, Vec3 sourcePos, Entity excludingEntity) {
        return getClosestVisiblePoint(world, targetPos, sourcePos, excludingEntity, null);
    }

    public static Vec3 getClosestVisiblePoint(Level world, BlockPos targetPos, Vec3 sourcePos, Entity excludingEntity, Direction dir) {
        if (targetPos.distToLowCornerSqr(sourcePos.x, sourcePos.y, sourcePos.z) > 7 * 7) {
            return null;
        }

        AABB totalArea = new AABB(sourcePos, Vec3.atLowerCornerOf(targetPos));
        List<AABB> obscurers = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(totalArea.minX), Mth.floor(totalArea.minY), Mth.floor(totalArea.minZ),
                Mth.ceil(totalArea.maxX), Mth.ceil(totalArea.maxY), Mth.ceil(totalArea.maxZ))) {
            if (!pos.equals(targetPos)) {
                world.getBlockState(pos).getShape(world, pos).forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                        obscurers.add(new AABB(x1, y1, z1, x2, y2, z2).move(pos).inflate(EPSILON)));
            }
        }
        for (Entity entity : world.getEntities(excludingEntity, totalArea, entity -> !entity.isSpectator() && entity.isPickable())) {
            obscurers.add(entity.getBoundingBox().inflate(entity.getPickRadius() + EPSILON));
        }
        List<AABB> targetBoxes = new ArrayList<>();
        world.getBlockState(targetPos).getShape(world, targetPos).forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                targetBoxes.add(new AABB(x1, y1, z1, x2, y2, z2).move(targetPos)));

        Vec3 resultVal = null;
        double resultDistanceSq = Double.POSITIVE_INFINITY;
        Direction[] dirs = dir == null ? Direction.values() : new Direction[] {dir};
        for (AABB box : targetBoxes) {
            for (Direction face : dirs) {
                AABB faceBox = getFace(box.inflate(-EPSILON), face);
                if (sourcePos.subtract(faceBox.minX, faceBox.minY, faceBox.minZ)
                        .dot(new Vec3(face.getStepX(), face.getStepY(), face.getStepZ())) > 0) {
                    //noinspection StaticPseudoFunctionalStyleMethod
                    Vec3 val = getClosestVisiblePoint(world,
                            Iterables.concat(obscurers,
                                    Iterables.transform(Iterables.filter(targetBoxes, it -> it != box),
                                            b -> b.inflate(EPSILON))),
                            faceBox, sourcePos, face);
                    if (val != null) {
                        double distanceSq = val.distanceToSqr(sourcePos);
                        if (distanceSq < resultDistanceSq) {
                            resultVal = val;
                            resultDistanceSq = distanceSq;
                        }
                    }
                }
            }
        }

        return resultVal;
    }

    private static Vec3 getClosestVisiblePoint(BlockGetter world, Iterable<AABB> obscurers, AABB face, Vec3 sourcePos, Direction dir) {
        Direction.Axis xAxis, yAxis;
        switch (dir.getAxis()) {
            case X -> {
                xAxis = Direction.Axis.Z;
                yAxis = Direction.Axis.Y;
            }
            case Y -> {
                xAxis = Direction.Axis.X;
                yAxis = Direction.Axis.Z;
            }
            case Z -> {
                xAxis = Direction.Axis.X;
                yAxis = Direction.Axis.Y;
            }
            default -> throw new AssertionError();
        }

        double minX = getComponent(face.minX, face.minY, face.minZ, xAxis);
        double minY = getComponent(face.minX, face.minY, face.minZ, yAxis);
        double maxX = getComponent(face.maxX, face.maxY, face.maxZ, xAxis);
        double maxY = getComponent(face.maxX, face.maxY, face.maxZ, yAxis);
        Area area = new Area(new Rectangle2D.Double(minX + EPSILON, minY + EPSILON, maxX - minX - 2 * EPSILON, maxY - minY - 2 * EPSILON));

        double f = getComponent(face.minX, face.minY, face.minZ, dir.getAxis());
        double s = getComponent(sourcePos, dir.getAxis());
        double sx = getComponent(sourcePos, xAxis);
        double sy = getComponent(sourcePos, yAxis);
        for (AABB obscurer : obscurers) {
            for (Direction obscurerSide : Direction.values()) {
                AABB obscurerFace = getFace(obscurer, obscurerSide);
                Path2D.Double path = getShadow(sourcePos, obscurerFace, obscurerSide, f, dir,
                        minX + (maxX - minX) * .5, minY + (maxY - minY) * .5, xAxis, yAxis);
                if (path != null)
                    area.subtract(new Area(path));
            }
        }

        if (area.contains(sx, sy)) {
            return createFromComponents(sx, xAxis, sy, yAxis, f, dir.getAxis());
        }

        double closestX = Double.NaN;
        double closestY = Double.NaN;
        double closestDistanceSq = Double.POSITIVE_INFINITY;
        double[] pointsRet = new double[6];
        double lastX = 0;
        double lastY = 0;
        double firstX = 0;
        double firstY = 0;
        for (PathIterator itr = area.getPathIterator(null); !itr.isDone(); itr.next()) {
            int segtype = itr.currentSegment(pointsRet);
            double curX, curY;
            if (segtype == PathIterator.SEG_CLOSE) {
                curX = firstX;
                curY = firstY;
            } else {
                curX = pointsRet[0];
                curY = pointsRet[1];
            }
            if (segtype == PathIterator.SEG_LINETO || segtype == PathIterator.SEG_CLOSE) {
                double relCurX = curX - lastX;
                double relCurY = curY - lastY;
                double relSx = sx - lastX;
                double relSy = sy - lastY;
                double lambda = (relCurX * relSx + relCurY * relSy) / (relCurX * relCurX + relCurY * relCurY);
                double newClosestX, newClosestY;
                if (lambda <= 0) {
                    newClosestX = lastX;
                    newClosestY = lastY;
                } else if (lambda >= 1) {
                    newClosestX = curX;
                    newClosestY = curY;
                } else {
                    newClosestX = lastX + lambda * relCurX;
                    newClosestY = lastY + lambda * relCurY;
                }
                double dx = newClosestX - sx;
                double dy = newClosestY - sy;
                double newClosestDistanceSq = dx * dx + dy * dy;
                if (newClosestDistanceSq < closestDistanceSq) {
                    closestDistanceSq = newClosestDistanceSq;
                    closestX = newClosestX;
                    closestY = newClosestY;
                }
            }
            lastX = curX;
            lastY = curY;
            if (segtype == PathIterator.SEG_MOVETO) {
                firstX = curX;
                firstY = curY;
            }
        }

        if (Double.isNaN(closestX)) return null;
        return createFromComponents(closestX, xAxis, closestY, yAxis, f, dir.getAxis());
    }

    /** @noinspection DuplicatedCode it's a painful truth, deal with it :) */
    private static Path2D.Double getShadow(Vec3 sourcePos, AABB obscurerFace, Direction obscurerFaceNormal, double targetPlaneCoord, Direction targetPlaneNormal, double targetCenterX, double targetCenterY, Direction.Axis xAxis, Direction.Axis yAxis) {
        double sourceOffset = getComponent(sourcePos, targetPlaneNormal.getAxis());
        double[][] obscurer = new double[4][3];
        boolean[] behindSource = new boolean[4];
        {
            Direction dirB = obscurerFaceNormal.getAxis() == Direction.Axis.Y ? Direction.WEST : obscurerFaceNormal.getClockWise();
            Direction dirC = rotateClockwise(obscurerFaceNormal, dirB.getAxis());
            for (int i = 0; i < 4; i++) {
                AABB vertex = getFace(getFace(obscurerFace, i < 2 ? dirB : dirB.getOpposite()), i == 0 || i == 3 ? dirC : dirC.getOpposite());
                obscurer[i][0] = vertex.minX;
                obscurer[i][1] = vertex.minY;
                obscurer[i][2] = vertex.minZ;
                double vertexOffset = getComponent(vertex.minX, vertex.minY, vertex.minZ, targetPlaneNormal.getAxis());
                behindSource[i] = sourceOffset < targetPlaneCoord ? vertexOffset < sourceOffset : vertexOffset > sourceOffset;
            }
        }

        // the obstacle is fully behind the player, no need to do anything
        if (behindSource[0] && behindSource[1] && behindSource[2] && behindSource[3]) {
            return null;
        }

        int firstInFront, lastInFront;
        if (!behindSource[0] && !behindSource[3] && (behindSource[1] || behindSource[2])) {
            // deal with situations like behindSource = [false, true, true, false]
            firstInFront = behindSource[2] ? 3 : 2;
            lastInFront = behindSource[1] ? 0 : 1;
        } else {
            //noinspection StatementWithEmptyBody
            for (firstInFront = 0; behindSource[firstInFront]; firstInFront++)
                ;
            //noinspection StatementWithEmptyBody
            for (lastInFront = 3; behindSource[lastInFront]; lastInFront--)
                ;
        }

        double firstShadowX = 0, firstShadowY = 0, lastShadowX = 0, lastShadowY = 0;

        // compute shadow points of vertices in front of the player
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, 6);
        for (int i = firstInFront; i <= lastInFront; i++) {
            // Compute the equation of the line going through this point and the player's eye position
            // in the form r = a + lambda * b, where r, a and b are vectors and lambda is scalar.
            // We also force lambda to be the value which makes r lie on the target plane, where a is the
            // eye position and b is the vector from the eye position to this point.
            double lambda = (targetPlaneCoord - sourceOffset) / (getComponent(obscurer[i][0], obscurer[i][1], obscurer[i][2], targetPlaneNormal.getAxis()) - sourceOffset);
            if (!Double.isFinite(lambda)) {
                return null; // only possible if the player is inside the obstacle
            }
            // actually find the point on the target plane
            Vec3 projected = sourcePos.add((obscurer[i][0] - sourcePos.x) * lambda, (obscurer[i][1] - sourcePos.y) * lambda, (obscurer[i][2] - sourcePos.z) * lambda);
            double x = getComponent(projected, xAxis);
            double y = getComponent(projected, yAxis);
            if (i == firstInFront) {
                path.moveTo(x, y);
                firstShadowX = x;
                firstShadowY = y;
            } else {
                path.lineTo(x, y);
            }
            if (i == lastInFront) {
                lastShadowX = x;
                lastShadowY = y;
            }
        }

        if (firstInFront == 0 && lastInFront == 3) {
            // there was nothing behind the player, we're done.
            path.closePath();
            return path;
        }

        // Deal with the two edges linking points behind the player with points in front of the player
        // lastBehind links with firstInFront, lastInFront links with firstBehind

        double lastDx, lastDy;
        {
            // Compute the equation of the plane going through this point, the last point, and sourcePos
            // in the form ax + by + cz = d
            int lastBehind = firstInFront == 0 ? 3 : firstInFront - 1;
            double dx1 = obscurer[lastBehind][0] - obscurer[firstInFront][0],
                    dy1 = obscurer[lastBehind][1] - obscurer[firstInFront][1],
                    dz1 = obscurer[lastBehind][2] - obscurer[firstInFront][2];
            double dx2 = obscurer[firstInFront][0] - sourcePos.x,
                    dy2 = obscurer[firstInFront][1] - sourcePos.y,
                    dz2 = obscurer[firstInFront][2] - sourcePos.z;
            // cross product
            double a = dy1 * dz2 - dz1 * dy2, b = dz1 * dx2 - dx1 * dz2, c = dx1 * dy2 - dy1 * dx2;

            // Calculate the line of intersection between this plane and the target plane
            // Forcing z = targetPlaneCoord, we get ax + by = d - c*targetPlaneCoord, which is y = -a/b x + blah, so gradient is -a/b
            double dx = -getComponent(a, b, c, xAxis);
            double dy = getComponent(a, b, c, yAxis);
            // normalize (dx, dy)
            double n = Math.sqrt(dx * dx + dy * dy);
            if (n < EPSILON) return null; // only possible if the player is inside the obstacle
            lastDx = dx / n;
            lastDy = dy / n;
        }
        double firstDx, firstDy;
        {
            // do it again for the other infinite ray
            int firstBehind = lastInFront == 3 ? 0 : lastInFront + 1;
            double dx1 = obscurer[firstBehind][0] - obscurer[lastInFront][0],
                    dy1 = obscurer[firstBehind][1] - obscurer[lastInFront][1],
                    dz1 = obscurer[firstBehind][2] - obscurer[lastInFront][2];
            double dx2 = obscurer[lastInFront][0] - sourcePos.x,
                    dy2 = obscurer[lastInFront][1] - sourcePos.y,
                    dz2 = obscurer[lastInFront][2] - sourcePos.z;
            double a = dy1 * dz2 - dz1 * dy2, b = dz1 * dx2 - dx1 * dz2, c = dx1 * dy2 - dy1 * dx2;

            double dx = -getComponent(a, b, c, xAxis);
            double dy = getComponent(a, b, c, yAxis);
            double n = Math.sqrt(dx * dx + dy * dy);
            if (n < EPSILON) return null;
            firstDx = dx / n;
            firstDy = dy / n;
        }

        // create fake outside points to close the shape that's really infinite
        // these fake points will lie on a circle of radius 10, centered on targetCenter

        double firstInfiniteX, firstInfiniteY;
        {
            // http://mathworld.wolfram.com/Circle-LineIntersection.html except the circle translated outside the origin
            double det = (lastShadowX - targetCenterX) * (lastShadowY + firstDy - targetCenterY) - (lastShadowX + firstDx - targetCenterX) * (lastShadowY - targetCenterY);
            double descriminant = 100 - det * det;
            if (descriminant < 0) {
                double dx = lastShadowX - targetCenterX, dy = lastShadowY - targetCenterY;
                double n = Math.sqrt(dx * dx + dy * dy);
                firstInfiniteX = targetCenterX + dx / n * 10;
                firstInfiniteY = targetCenterY + dy / n * 10;
            } else {
                descriminant = Math.sqrt(descriminant);
                double x1 = firstDy < 0 ? det * firstDy - firstDx * descriminant : det * firstDy + firstDx * descriminant;
                double x2 = firstDy < 0 ? det * firstDy + firstDx * descriminant : det * firstDy - firstDx * descriminant;
                double y1 = -det * firstDx + Math.abs(firstDy) * descriminant;
                double y2 = -det * firstDx - Math.abs(firstDy) * descriminant;
                if (firstDx * x1 + firstDy * y1 > firstDx * x2 + firstDy * y2) {
                    firstInfiniteX = x1 + targetCenterX;
                    firstInfiniteY = y1 + targetCenterY;
                } else {
                    firstInfiniteX = x2 + targetCenterX;
                    firstInfiniteY = y2 + targetCenterY;
                }
            }
        }

        double lastInfiniteX, lastInfiniteY;
        {
            double det = (firstShadowX - targetCenterX) * (firstShadowY + lastDy - targetCenterY) - (firstShadowX + lastDx - targetCenterX) * (firstShadowY - targetCenterY);
            double descriminant = 100 - det * det;
            if (descriminant < 0) {
                double dx = firstShadowX - targetCenterX, dy = firstShadowY - targetCenterY;
                double n = Math.sqrt(dx * dx + dy * dy);
                lastInfiniteX = targetCenterX + dx / n * 10;
                lastInfiniteY = targetCenterY + dy / n * 10;
            } else {
                descriminant = Math.sqrt(descriminant);
                double x1 = lastDy < 0 ? det * lastDy - lastDx * descriminant : det * lastDy + lastDx * descriminant;
                double x2 = lastDy < 0 ? det * lastDy + lastDx * descriminant : det * lastDy - lastDx * descriminant;
                double y1 = -det * lastDx + Math.abs(lastDy) * descriminant;
                double y2 = -det * lastDx - Math.abs(lastDy) * descriminant;
                if (lastDx * x1 + lastDy * y1 > lastDx * x2 + lastDy * y2) {
                    lastInfiniteX = x1 + targetCenterX;
                    lastInfiniteY = y1 + targetCenterY;
                } else {
                    lastInfiniteX = x2 + targetCenterX;
                    lastInfiniteY = y2 + targetCenterY;
                }
            }
        }

        // trace the circle
        boolean clockwise = firstDx * lastDy < firstDy * lastDx;
        double firstAngle = Math.atan2(firstInfiniteY - targetCenterY, firstInfiniteX - targetCenterX);
        double targetAngle = Math.atan2(lastInfiniteY - targetCenterY, lastInfiniteX - targetCenterX);
        double angleDifference = (clockwise ? firstAngle - targetAngle : targetAngle - firstAngle) % Math.PI * 2;
        if (angleDifference < 0) angleDifference += Math.PI * 2;
        for (double dTheta = 0; dTheta < angleDifference; dTheta += Math.PI / 3) {
            double theta = clockwise ? firstAngle - dTheta : firstAngle + dTheta;
            path.moveTo(10 * Math.cos(theta) + targetCenterX, 10 * Math.sin(theta) + targetCenterY);
        }
        path.moveTo(10 * Math.cos(targetAngle) + targetCenterX, 10 * Math.sin(targetAngle + targetCenterY));
        path.closePath();

        return path;
    }

    private static double getComponent(Vec3 vec, Direction.Axis axis) {
        return getComponent(vec.x, vec.y, vec.z, axis);
    }

    private static double getComponent(double x, double y, double z, Direction.Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    /** @noinspection DuplicatedCode */
    private static Vec3 createFromComponents(double a, Direction.Axis aAxis, double b, Direction.Axis bAxis, double c, Direction.Axis cAxis) {
        double x = 0, y = 0, z = 0;
        switch (aAxis) {
            case X -> x = a;
            case Y -> y = a;
            case Z -> z = a;
        }
        switch (bAxis) {
            case X -> x = b;
            case Y -> y = b;
            case Z -> z = b;
        }
        switch (cAxis) {
            case X -> x = c;
            case Y -> y = c;
            case Z -> z = c;
        }
        return new Vec3(x, y, z);
    }

    private static AABB getFace(AABB box, Direction dir) {
        return switch (dir) {
            case WEST -> new AABB(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
            case EAST -> new AABB(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            case DOWN -> new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
            case UP -> new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
            case NORTH -> new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
            case SOUTH -> new AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
        };
    }

    public static Direction rotateClockwise(Direction dir, Direction.Axis axis) {
        switch (axis) {
            case X -> {
                if (dir != Direction.WEST && dir != Direction.EAST) {
                    return rotateXClockwise(dir);
                }
                return dir;
            }
            case Y -> {
                if (dir != Direction.UP && dir != Direction.DOWN) {
                    return dir.getClockWise();
                }
                return dir;
            }
            case Z -> {
                if (dir != Direction.NORTH && dir != Direction.SOUTH) {
                    return rotateZClockwise(dir);
                }
                return dir;
            }
            default -> throw new IllegalStateException("Unable to get CW facing for axis " + axis);
        }
    }

    public static Direction rotateXClockwise(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.DOWN;
            case SOUTH -> Direction.UP;
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> throw new IllegalStateException("Unable to get X-rotated facing of " + dir);
        };
    }

    public static Direction rotateZClockwise(Direction dir) {
        return switch (dir) {
            case EAST -> Direction.DOWN;
            case WEST -> Direction.UP;
            case UP -> Direction.EAST;
            case DOWN -> Direction.WEST;
            default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + dir);
        };
    }

    private static class ClosestPosResult {
        Vec3 val;
        double distanceSq = Double.POSITIVE_INFINITY;
    }

}
