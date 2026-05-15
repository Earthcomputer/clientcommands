package net.earthcomputer.clientcommands.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public abstract class Shape {
    int deathTime;
    protected Vec3 prevPos;

    public void tick() {
    }

    public abstract void addLines(Consumer<Line> lines, Camera camera, DeltaTracker deltaTracker);

    public abstract Vec3 getPos();

}
