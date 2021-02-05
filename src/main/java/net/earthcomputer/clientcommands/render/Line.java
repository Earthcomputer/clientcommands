package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Copyright (c) 2020 KaptainWutax
 */
public class Line extends Shape {

    public Vec3d start;
    public Vec3d end;
    public int color;
    public float thickness;

    public Line() {
        this(Vec3d.ZERO, Vec3d.ZERO, 0xffffff, -1);
    }

    public Line(Vec3d start, Vec3d end) {
        this(start, end, 0xffffff, -1);
    }

    public Line(Vec3d start, Vec3d end, int color, int life) {
        this(start, end, color, life, 2.0F);
    }

    public Line(Vec3d start, Vec3d end, int color, int life, float thickness) {
        this.start = start;
        this.end = end;
        this.color = color;
        this.life = life;
        this.thickness = thickness;
    }

    @Override
    public void render() {
        super.render();

        if (this.start == null || this.end == null) return;

        Vec3d camPos = this.mc.gameRenderer.getCamera().getPos();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        //This is how thick the line is.
        GlStateManager.lineWidth(thickness);
        buffer.begin(3, VertexFormats.POSITION_COLOR);

        //Put the start and end vertices in the buffer.
        this.putVertex(buffer, camPos, this.start);
        this.putVertex(buffer, camPos, this.end);

        //Draw it all.
        tessellator.draw();
    }

    protected void putVertex(BufferBuilder buffer, Vec3d camPos, Vec3d pos) {
        buffer.vertex(
                pos.getX() - camPos.x,
                pos.getY() - camPos.y,
                pos.getZ() - camPos.z
        ).color(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                1.0F
        ).next();
    }

    @Override
    public BlockPos getPos() {
        double x = (this.end.getX() - this.start.getX()) / 2 + this.start.getX();
        double y = (this.end.getY() - this.start.getY()) / 2 + this.start.getY();
        double z = (this.end.getZ() - this.start.getZ()) / 2 + this.start.getZ();
        return new BlockPos(x, y, z);
    }

}