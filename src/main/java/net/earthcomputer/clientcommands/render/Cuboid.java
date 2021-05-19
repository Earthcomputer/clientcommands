package net.earthcomputer.clientcommands.render;


import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Cuboid extends Shape {

    private final Line[] edges = new Line[12];
    public final Vec3d start;
    public final Vec3d size;

    public Cuboid(Box box, int color) {
        this(new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ), color);
    }

    public Cuboid(Vec3d start, Vec3d end, int color) {
        this.start = start;
        this.size = new Vec3d(end.getX() - start.getX(), end.getY() - start.getY(), end.getZ() - start.getZ());
        this.edges[0] = new Line(this.start, this.start.add(this.size.getX(), 0, 0), color);
        this.edges[1] = new Line(this.start, this.start.add(0, this.size.getY(), 0), color);
        this.edges[2] = new Line(this.start, this.start.add(0, 0, this.size.getZ()), color);
        this.edges[3] = new Line(this.start.add(this.size.getX(), 0, this.size.getZ()), this.start.add(this.size.getX(), 0, 0), color);
        this.edges[4] = new Line(this.start.add(this.size.getX(), 0, this.size.getZ()), this.start.add(this.size.getX(), this.size.getY(), this.size.getZ()), color);
        this.edges[5] = new Line(this.start.add(this.size.getX(), 0, this.size.getZ()), this.start.add(0, 0, this.size.getZ()), color);
        this.edges[6] = new Line(this.start.add(this.size.getX(), this.size.getY(), 0), this.start.add(this.size.getX(), 0, 0), color);
        this.edges[7] = new Line(this.start.add(this.size.getX(), this.size.getY(), 0), this.start.add(0, this.size.getY(), 0), color);
        this.edges[8] = new Line(this.start.add(this.size.getX(), this.size.getY(), 0), this.start.add(this.size.getX(), this.size.getY(), this.size.getZ()), color);
        this.edges[9] = new Line(this.start.add(0, this.size.getY(), this.size.getZ()), this.start.add(0, 0, this.size.getZ()), color);
        this.edges[10] = new Line(this.start.add(0, this.size.getY(), this.size.getZ()), this.start.add(0, this.size.getY(), 0), color);
        this.edges[11] = new Line(this.start.add(0, this.size.getY(), this.size.getZ()), this.start.add(this.size.getX(), this.size.getY(), this.size.getZ()), color);
    }

    @Override
    public void render(MatrixStack matrixStack, VertexConsumerProvider.Immediate vertexConsumerProvider, float delta) {
        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getLines());
        for (Line edge : this.edges) {
            edge.renderLine(matrixStack, vertexConsumer, delta, prevPos.subtract(getPos()));
        }
        vertexConsumerProvider.draw(RenderLayer.getLines());
    }

    @Override
    public Vec3d getPos() {
        return start;
    }

}
