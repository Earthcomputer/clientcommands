package net.earthcomputer.clientcommands.render;


import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Copyright (c) 2020 KaptainWutax
 */
public class Cuboid extends Shape {

    private final Line[] edges = new Line[12];
    public Vec3d start;
    public Vec3d size;

//    public Cuboid() {
//        this(BlockPos.ORIGIN, BlockPos.ORIGIN, Color.WHITE);
//    }

    public Cuboid(Vec3d start, Vec3d end) {
        this(start, end, 0xffffff);
    }

    public Cuboid(BlockBox box, int color) {
        this(new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ), color);
    }

    public Cuboid(Box box, int color, int life) {
        this(new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ), color, life);
    }

    public Cuboid(Vec3d start, Vec3d end, int color) {
        this(start, end, color, -1);
    }

    public Cuboid(Vec3d start, Vec3d end, int color, int life) {
        this.start = start;
        this.size = new Vec3d(end.getX() - start.getX(), end.getY() - start.getY(), end.getZ() - start.getZ());
        this.life = life;
        this.edges[0] = new Line(this.start, this.start.add(this.size.getX(), 0, 0), color, life);
        this.edges[1] = new Line(this.start, this.start.add(0, this.size.getY(), 0), color, life);
        this.edges[2] = new Line(this.start, this.start.add(0, 0, this.size.getZ()), color, life);
        this.edges[3] = new Line(this.start.add(this.size.getX(), 0, this.size.getZ()), this.start.add(this.size.getX(), 0, 0), color, life);
        this.edges[4] = new Line(this.start.add(this.size.getX(), 0, this.size.getZ()), this.start.add(this.size.getX(), this.size.getY(), this.size.getZ()), color, life);
        this.edges[5] = new Line(this.start.add(this.size.getX(), 0, this.size.getZ()), this.start.add(0, 0, this.size.getZ()), color, life);
        this.edges[6] = new Line(this.start.add(this.size.getX(), this.size.getY(), 0), this.start.add(this.size.getX(), 0, 0), color, life);
        this.edges[7] = new Line(this.start.add(this.size.getX(), this.size.getY(), 0), this.start.add(0, this.size.getY(), 0), color, life);
        this.edges[8] = new Line(this.start.add(this.size.getX(), this.size.getY(), 0), this.start.add(this.size.getX(), this.size.getY(), this.size.getZ()), color, life);
        this.edges[9] = new Line(this.start.add(0, this.size.getY(), this.size.getZ()), this.start.add(0, 0, this.size.getZ()), color, life);
        this.edges[10] = new Line(this.start.add(0, this.size.getY(), this.size.getZ()), this.start.add(0, this.size.getY(), 0), color, life);
        this.edges[11] = new Line(this.start.add(0, this.size.getY(), this.size.getZ()), this.start.add(this.size.getX(), this.size.getY(), this.size.getZ()), color, life);
    }

    @Override
    public void render() {
        super.render();

        if (this.start == null || this.size == null) return;

        for (Line edge : this.edges) {
            if (edge == null) continue;
            edge.render();
        }
    }

    @Override
    public BlockPos getPos() {
        return new BlockPos(this.size.getX() / 2, this.size.getY() / 2, this.size.getZ() / 2);
    }

}
