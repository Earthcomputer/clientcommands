package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

public abstract class Shape {
    int deathTime;
    protected Vec3 prevPos;

    public void tick() {
    }

    public abstract void render(PoseStack matrixStack, VertexConsumer vertexConsumer, float delta);

    public abstract Vec3 getPos();

}
