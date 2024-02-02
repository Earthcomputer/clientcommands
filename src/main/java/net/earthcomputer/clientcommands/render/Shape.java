package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

public abstract class Shape {
    int deathTime;
    protected Vec3 prevPos;

    public void tick() {
    }

    public abstract void render(PoseStack poseStack, VertexConsumer vertexConsumer, float delta);

    public abstract Vec3 getPos();

}
