package net.earthcomputer.clientcommands.render;


import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Copyright (c) 2020 KaptainWutax
 */
public abstract class Shape {

    protected MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * The life in number of ticks
     */
    protected int life = -1;
    private long prevTick;

    public void render() {
        if (this.life > 0) {
            this.life -= this.mc.world.getTime() - this.prevTick;
        }
        this.prevTick = this.mc.world.getTime();
    }

    public abstract BlockPos getPos();

    public int getLife() {
        return this.life;
    }

    protected Vec3d toVec3d(BlockPos pos) {
        return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
    }

}