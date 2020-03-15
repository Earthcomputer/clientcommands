package net.earthcomputer.clientcommands.features;

import com.google.common.collect.Sets;
import net.earthcomputer.clientcommands.mixin.LandPathNodeMakerAccessor;
import net.minecraft.block.BlockPlacementEnvironment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.ChunkCache;

import java.util.EnumSet;
import java.util.Set;

public class PlayerPathNodeMaker extends LandPathNodeMaker {

    private PlayerEntity player;
    private PathfindingHints hints;

    public void initFromPlayer(PlayerEntity player, PathfindingHints hints) {
        this.player = player;
        this.hints = hints;
        field_31 = MathHelper.floor(player.getWidth() + 1);
        field_30 = MathHelper.floor(player.getHeight() + 1);
        field_28 = MathHelper.floor(player.getWidth() + 1);
    }

    @Override
    public void init(ChunkCache chunkCache, MobEntity mobEntity) {
        field_20622 = chunkCache;
        pathNodeCache.clear();
    }

    @Override
    public void clear() {
        field_20622 = null;
        player = null;
        hints = null;
    }

    @Override
    public PathNode getStart() {
        int feetY;
        if (canSwim() && player.isTouchingWater()) {
            feetY = MathHelper.floor(player.getY());
            BlockPos.Mutable mutable3 = new BlockPos.Mutable(player.getX(), feetY, player.getZ());
            BlockState lv2 = field_20622.getBlockState(mutable3);
            while (lv2.getBlock() == Blocks.WATER || lv2.getFluidState() == Fluids.WATER.getStill(false)) {
                feetY++;
                mutable3.set(player.getX(), feetY, player.getZ());
                lv2 = field_20622.getBlockState(mutable3);
            }
            feetY--;
        }
        else if (player.onGround) {
            feetY = MathHelper.floor(player.getY() + 0.5);
        }
        else {
            BlockPos pos = new BlockPos(player);
            while ((field_20622.getBlockState(pos).isAir() || field_20622.getBlockState(pos).canPlaceAtSide(field_20622, pos, BlockPlacementEnvironment.LAND)) && pos.getY() > 0) {
                pos = pos.down();
            }
            feetY = pos.up().getY();
        }
        BlockPos entityPos = new BlockPos(player);
        PathNodeType pathNodeType4 = getNodeType(player, entityPos.getX(), feetY, entityPos.getZ());
        if (hints.getPathfindingPenalty(pathNodeType4) < 0) {
            Set<BlockPos> cornerPositions = Sets.newHashSet();
            cornerPositions.add(new BlockPos(player.getBoundingBox().x1, feetY, player.getBoundingBox().z1));
            cornerPositions.add(new BlockPos(player.getBoundingBox().x1, feetY, player.getBoundingBox().z2));
            cornerPositions.add(new BlockPos(player.getBoundingBox().x2, feetY, player.getBoundingBox().z1));
            cornerPositions.add(new BlockPos(player.getBoundingBox().x2, feetY, player.getBoundingBox().z2));
            for (final BlockPos corner : cornerPositions) {
                PathNodeType pathNodeType8 = getNodeType(player, corner);
                if (hints.getPathfindingPenalty(pathNodeType8) >= 0) {
                    return getNode(corner.getX(), corner.getY(), corner.getZ());
                }
            }
        }
        return getNode(entityPos.getX(), feetY, entityPos.getZ());
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public int getSuccessors(PathNode[] successors, PathNode node) {
        int successorCount = 0;
        int maxYStep = 0;
        PathNodeType pathNodeType6 = getNodeType(player, node.x, node.y + 1, node.z);
        if (hints.getPathfindingPenalty(pathNodeType6) >= 0) {
            PathNodeType pathNodeType7 = getNodeType(player, node.x, node.y, node.z);
            if (pathNodeType7 == PathNodeType.STICKY_HONEY) {
                maxYStep = 0;
            }
            else {
                maxYStep = MathHelper.floor(Math.max(1, player.stepHeight));
            }
        }
        double feetY = getHeight(field_20622, new BlockPos(node.x, node.y, node.z));
        PathNode successor = getPathNode(node.x, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH);
        if (successor != null && !successor.visited && successor.penalty >= 0) {
            successors[successorCount++] = successor;
        }
        successor = getPathNode(node.x - 1, node.y, node.z, maxYStep, feetY, Direction.WEST);
        if (successor != null && !successor.visited && successor.penalty >= 0) {
            successors[successorCount++] = successor;
        }
        successor = getPathNode(node.x + 1, node.y, node.z, maxYStep, feetY, Direction.EAST);
        if (successor != null && !successor.visited && successor.penalty >= 0) {
            successors[successorCount++] = successor;
        }
        successor = getPathNode(node.x, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH);
        if (successor != null && !successor.visited && successor.penalty >= 0) {
            successors[successorCount++] = successor;
        }
        successor = getPathNode(node.x - 1, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH);
        if (((LandPathNodeMakerAccessor) this).callIsValidDiagonalSuccessor(node, successor, successor, successor)) {
            successors[successorCount++] = successor;
        }
        successor = getPathNode(node.x + 1, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH);
        if (((LandPathNodeMakerAccessor) this).callIsValidDiagonalSuccessor(node, successor, successor, successor)) {
            successors[successorCount++] = successor;
        }
        successor = getPathNode(node.x - 1, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH);
        if (((LandPathNodeMakerAccessor) this).callIsValidDiagonalSuccessor(node, successor, successor, successor)) {
            successors[successorCount++] = successor;
        }
        successor = getPathNode(node.x + 1, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH);
        if (((LandPathNodeMakerAccessor) this).callIsValidDiagonalSuccessor(node, successor, successor, successor)) {
            successors[successorCount++] = successor;
        }
        return successorCount;
    }

    private PathNode getPathNode(int x, int y, int z, int maxYStep, double height, Direction direction) {
        PathNode node = null;
        BlockPos pos = new BlockPos(x, y, z);
        double feetY = getHeight(field_20622, pos);
        if (feetY - height > 1.125) {
            return null;
        }

        PathNodeType type = getNodeType(player, x, y, z);
        float penalty = hints.getPathfindingPenalty(type);
        double radius = (double)player.getWidth() / 2;
        if (penalty >= 0) {
            node = getNode(x, y, z);
            node.type = type;
            node.penalty = Math.max(node.penalty, penalty);
        }

        if (type == PathNodeType.WALKABLE) {
            return node;
        }

        if ((node == null || node.penalty < 0) && maxYStep > 0 && type != PathNodeType.FENCE && type != PathNodeType.TRAPDOOR) {
            node = getPathNode(x, y + 1, z, maxYStep - 1, height, direction);
            if (node != null && (node.type == PathNodeType.OPEN || node.type == PathNodeType.WALKABLE) && player.getWidth() < 1) {
                double otherX = (double)(x - direction.getOffsetX()) + 0.5;
                double otherZ = (double)(z - direction.getOffsetZ()) + 0.5;
                Box box = new Box(
                        otherX - radius,
                        getHeight(field_20622, new BlockPos(otherX, y + 1, otherZ)) + 0.001,
                        otherZ - radius,
                        otherX + radius,
                        player.getHeight() + getHeight(field_20622, new BlockPos(node.x, node.y, node.z)) - 0.002,
                        otherZ + radius
                );
                if (!field_20622.doesNotCollide(player, box)) {
                    node = null;
                }
            }
        }

        if (type == PathNodeType.WATER && !canSwim()) {
            if (getNodeType(player, x, y - 1, z) != PathNodeType.WATER) {
                return node;
            }

            while (y > 0) {
                y--;
                type = getNodeType(player, x, y, z);
                if (type != PathNodeType.WATER) {
                    return node;
                }

                node = getNode(x, y, z);
                node.type = type;
                node.penalty = Math.max(node.penalty, hints.getPathfindingPenalty(type));
            }
        }

        if (type == PathNodeType.OPEN) {
            Box box = new Box(
                    x - radius + 0.5,
                    y + 0.001,
                    z - radius + 0.5,
                    x + radius + 0.5,
                    y + player.getHeight(),
                    z + radius + 0.5
            );
            if (!field_20622.doesNotCollide(player, box)) {
                return null;
            }

            if (player.getWidth() >= 1) {
                PathNodeType typeBelow = getNodeType(player, x, y - 1, z);
                if (typeBelow == PathNodeType.BLOCKED) {
                    node = getNode(x, y, z);
                    node.type = PathNodeType.WALKABLE;
                    node.penalty = Math.max(node.penalty, penalty);
                    return node;
                }
            }

            int fallHeight = 0;
            int originalY = y;

            while (type == PathNodeType.OPEN) {
                y--;
                PathNode nodeBelow;
                if (y < 0) {
                    nodeBelow = getNode(x, originalY, z);
                    nodeBelow.type = PathNodeType.BLOCKED;
                    nodeBelow.penalty = -1;
                    return nodeBelow;
                }

                nodeBelow = getNode(x, y, z);
                if (fallHeight >= player.getSafeFallDistance()) {
                    nodeBelow.type = PathNodeType.BLOCKED;
                    nodeBelow.penalty = -1;
                    return nodeBelow;
                }
                fallHeight++;

                type = getNodeType(player, x, y, z);
                penalty = hints.getPathfindingPenalty(type);
                if (type != PathNodeType.OPEN && penalty >= 0) {
                    node = nodeBelow;
                    nodeBelow.type = type;
                    nodeBelow.penalty = Math.max(nodeBelow.penalty, penalty);
                    break;
                }

                if (penalty < 0) {
                    nodeBelow.type = PathNodeType.BLOCKED;
                    nodeBelow.penalty = -1;
                    return nodeBelow;
                }
            }
        }

        return node;
    }

    @Override
    public PathNodeType getNodeType(BlockView world, int x, int y, int z, MobEntity unusedMob, int sizeX, int sizeY, int sizeZ, boolean canOpenDoors, boolean canEnterOpenDoors) {
        PathNodeType customType = hints.getNodeType(world, new BlockPos(x, y, z));
        if (customType != null)
            return customType;

        EnumSet<PathNodeType> nodesInBounds = EnumSet.noneOf(PathNodeType.class);
        PathNodeType type = PathNodeType.BLOCKED;
        BlockPos entityPos = new BlockPos(player);
        type = getNodeType(world, x, y, z, sizeX, sizeY, sizeZ, canOpenDoors, canEnterOpenDoors, nodesInBounds, type, entityPos);
        if (nodesInBounds.contains(PathNodeType.FENCE)) {
            return PathNodeType.FENCE;
        }
        PathNodeType newType = PathNodeType.BLOCKED;
        for (final PathNodeType typeInBounds : nodesInBounds) {
            if (hints.getPathfindingPenalty(typeInBounds) < 0) {
                return typeInBounds;
            }
            if (hints.getPathfindingPenalty(typeInBounds) < hints.getPathfindingPenalty(newType)) {
                continue;
            }
            newType = typeInBounds;
        }
        if (type == PathNodeType.OPEN && hints.getPathfindingPenalty(newType) == 0) {
            return PathNodeType.OPEN;
        }
        return newType;
    }

    private PathNodeType getNodeType(PlayerEntity entity, BlockPos pos) {
        return getNodeType(entity, pos.getX(), pos.getY(), pos.getZ());
    }

    private PathNodeType getNodeType(PlayerEntity entity, int x, int y, int z) {
        return getNodeType(field_20622, x, y, z, null, field_31, field_30, field_28, canOpenDoors(), canEnterOpenDoors());
    }
}
