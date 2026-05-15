package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record ColoredTriangleRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2fc pose,
    int x0,
    int y0,
    int x1,
    int y1,
    int x2,
    int y2,
    int color,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    public ColoredTriangleRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2fc pose,
        int x0,
        int y0,
        int x1,
        int y1,
        int x2,
        int y2,
        int color,
        @Nullable ScreenRectangle scissorArea
    ) {
        this(pipeline, textureSetup, pose, x0, y0, x1, y1, x2, y2, color, scissorArea, getBounds(x0, y0, x1, y1, x2, y2, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(pose, x0, y0).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y1).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y1).setColor(color);
        consumer.addVertexWith2DPose(pose, x2, y2).setColor(color);
    }

    @Nullable
    private static ScreenRectangle getBounds(int x0, int y0, int x1, int y1, int x2, int y2, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea) {
        int minX = Math.min(x0, Math.min(x1, x2));
        int minY = Math.min(y0, Math.min(y1, y2));
        int maxX = Math.max(x0, Math.max(x1, x2));
        int maxY = Math.max(y0, Math.max(y1, y2));
        ScreenRectangle bounds = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }
}
