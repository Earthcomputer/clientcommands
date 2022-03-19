package net.earthcomputer.clientcommands;

import net.minecraft.util.math.Vec3d;

public class TntFinderManager {
    private static Vec3d x1 = null;
    private static Vec3d v1 = null;
    private static Vec3d x2 = null;
    private static Vec3d v2 = null;
    private static Vec3d x3 = null;
    private static Vec3d v3 = null;

    public static boolean set(Vec3d x, Vec3d v) {
        if (x1 == null || v1 == null) {
            x1 = x;
            v1 = v;
            return false;
        } else if (x2 == null || v2 == null) {
            x2 = x;
            v2 = v;
            return false;
        } else {
            x3 = x;
            v3 = v;
            return true;
        }
    }

    public static Vec3d triangulate() {
        Vec3d est1 = intersect(x1, v1, x2, v2);
        if (est1 == null) {
            reset();
            return null;
        }
        Vec3d est2 = intersect(x1, v1, x3, v3);
        if (est2 == null) {
            reset();
            return null;
        }
        Vec3d est3 = intersect(x2, v2, x3, v3);
        if (est3 == null) {
            reset();
            return null;
        }
        reset();
        return est1.add(est2).add(est3).multiply(1.0 / 3.0);
    }

    private static Vec3d intersect(Vec3d x1, Vec3d v1, Vec3d x2, Vec3d v2) {
        final double slopeDiff = v1.x * v2.z - v1.z * v2.x;
        if (-10e-10 <= slopeDiff && slopeDiff <= 10e-10) {
            return null;
        }
        double x = (x1.z * v1.x * v2.x - x1.x * v1.z * v2.x - x2.z * v1.x * v2.x + x2.x * v1.x * v2.z) / slopeDiff;
        double z = (v1.z / v1.x) * (x - x1.x) + x1.z;
        return new Vec3d(x, 0, z);
    }

    public static void reset() {
        x1 = x2 = v1 = v2 = x3 = v3 = null;
    }
}
