package net.earthcomputer.clientcommands.render;


import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

/**
 * Copyright (c) 2020 KaptainWutax
 */
public class Cube extends Cuboid {

    public Cube() {
        this(BlockPos.ORIGIN, Color.WHITE);
    }

    public Cube(BlockPos pos) {
        this(pos, Color.WHITE);
    }

    public Cube(BlockPos pos, Color color) {
        super(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), new Vec3d(1, 1, 1), color);
    }

}


