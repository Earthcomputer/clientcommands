package net.earthcomputer.clientcommands.render;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Cuboid extends Shape {

    private final Line[] edges = new Line[12];
    public final Vec3 start;
    public final Vec3 size;

    public Cuboid(AABB box, int color) {
        this(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), color);
    }

    public Cuboid(Vec3 start, Vec3 end, int color) {
        this.start = start;
        this.size = new Vec3(end.x() - start.x(), end.y() - start.y(), end.z() - start.z());
        this.edges[0] = new Line(this.start, this.start.add(this.size.x(), 0, 0), color);
        this.edges[1] = new Line(this.start, this.start.add(0, this.size.y(), 0), color);
        this.edges[2] = new Line(this.start, this.start.add(0, 0, this.size.z()), color);
        this.edges[3] = new Line(this.start.add(this.size.x(), 0, this.size.z()), this.start.add(this.size.x(), 0, 0), color);
        this.edges[4] = new Line(this.start.add(this.size.x(), 0, this.size.z()), this.start.add(this.size.x(), this.size.y(), this.size.z()), color);
        this.edges[5] = new Line(this.start.add(this.size.x(), 0, this.size.z()), this.start.add(0, 0, this.size.z()), color);
        this.edges[6] = new Line(this.start.add(this.size.x(), this.size.y(), 0), this.start.add(this.size.x(), 0, 0), color);
        this.edges[7] = new Line(this.start.add(this.size.x(), this.size.y(), 0), this.start.add(0, this.size.y(), 0), color);
        this.edges[8] = new Line(this.start.add(this.size.x(), this.size.y(), 0), this.start.add(this.size.x(), this.size.y(), this.size.z()), color);
        this.edges[9] = new Line(this.start.add(0, this.size.y(), this.size.z()), this.start.add(0, 0, this.size.z()), color);
        this.edges[10] = new Line(this.start.add(0, this.size.y(), this.size.z()), this.start.add(0, this.size.y(), 0), color);
        this.edges[11] = new Line(this.start.add(0, this.size.y(), this.size.z()), this.start.add(this.size.x(), this.size.y(), this.size.z()), color);
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, float delta) {
        for (Line edge : this.edges) {
            edge.renderLine(poseStack, vertexConsumer, delta, prevPos.subtract(getPos()));
        }
    }

    @Override
    public Vec3 getPos() {
        return start;
    }

}
