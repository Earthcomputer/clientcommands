package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.world.phys.Vec3;

public abstract class Shape {
    int deathTime;
    protected Vec3 prevPos;

    public void tick() {
    }

    public abstract void render(VertexConsumer vertexConsumer, WorldRenderContext context);

    public abstract Vec3 getPos();

}
