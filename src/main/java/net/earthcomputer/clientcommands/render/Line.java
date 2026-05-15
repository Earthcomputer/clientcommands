package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class Line extends Shape {
    public final Vec3 start;
    public final Vec3 end;
    public final int color;
    public static final float THICKNESS = 2f;

    public Line(Vec3 start, Vec3 end, int color) {
        this.start = start;
        this.end = end;
        this.color = color;
    }

    @Override
    public void addLines(Consumer<Line> lines, Camera camera, DeltaTracker deltaTracker) {
        lines.accept(toCameraView(camera, deltaTracker, prevPos.subtract(getPos())));
    }

    public Line toCameraView(Camera camera, DeltaTracker deltaTracker, Vec3 prevPosOffset) {
        float delta = deltaTracker.getRealtimeDeltaTicks();
        Vec3 cameraPos = camera.position();
        return new Line(
            start.add(prevPosOffset.scale(1 - delta)).subtract(cameraPos),
            end.add(prevPosOffset.scale(1 - delta).subtract(cameraPos)),
            color
        );
    }

    public void draw(VertexConsumer vertexConsumer, PoseStack poseStack) {
        Vec3 normal = this.end.subtract(this.start).normalize();
        putVertex(poseStack, vertexConsumer, this.start, normal);
        putVertex(poseStack, vertexConsumer, this.end, normal);
    }

    private void putVertex(PoseStack poseStack, VertexConsumer vertexConsumer, Vec3 pos, Vec3 normal) {
        vertexConsumer.addVertex(
                poseStack.last().pose(),
                (float) pos.x(),
                (float) pos.y(),
                (float) pos.z()
        ).setColor(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                1.0F
        ).setNormal(
                poseStack.last(),
                (float) normal.x(),
                (float) normal.y(),
                (float) normal.z()
        ).setLineWidth(2);
    }

    @Override
    public Vec3 getPos() {
        return start;
    }
}
