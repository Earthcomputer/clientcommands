package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public class Line extends Shape {
    public final Vec3d start;
    public final Vec3d end;
    public final int color;
    public final float thickness;

    public Line(Vec3d start, Vec3d end, int color) {
        this(start, end, color, 2.0F);
    }

    public Line(Vec3d start, Vec3d end, int color, float thickness) {
        this.start = start;
        this.end = end;
        this.color = color;
        this.thickness = thickness;
    }

    @Override
    public void render(MatrixStack matrixStack, VertexConsumerProvider.Immediate vertexConsumerProvider, float delta) {
        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getLines());
        renderLine(matrixStack, vertexConsumer, delta, prevPos.subtract(getPos()));
        vertexConsumerProvider.draw(RenderLayer.getLines());
    }

    public void renderLine(MatrixStack matrixStack, VertexConsumer vertexConsumer, float delta, Vec3d prevPosOffset) {
        RenderSystem.lineWidth(thickness);

        putVertex(matrixStack, vertexConsumer, this.start.add(prevPosOffset.multiply(1 - delta)));
        putVertex(matrixStack, vertexConsumer, this.end.add(prevPosOffset.multiply(1 - delta)));
    }

    private void putVertex(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d pos) {
        vertexConsumer.vertex(
                matrixStack.peek().getModel(),
                (float) pos.getX(),
                (float) pos.getY(),
                (float) pos.getZ()
        ).color(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                1.0F
        ).normal(
                1, 0, 0
        ).next();
    }

    @Override
    public Vec3d getPos() {
        return start;
    }

}
