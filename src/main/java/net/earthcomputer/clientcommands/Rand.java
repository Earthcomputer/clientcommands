package net.earthcomputer.clientcommands;

import java.util.Random;

public class Rand extends Random {
    private long seed;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian = false;

    public Rand(long seed) {
        this.seed = seed;
    }

    public Rand(Rand other) {
        this(other.seed);
        this.nextNextGaussian = other.nextNextGaussian;
        this.haveNextNextGaussian = other.haveNextNextGaussian;
    }

    public long getSeed() {
        return seed;
    }

    @Override
    protected int next(int bits) {
        return (int) ((seed = (seed * 0x5deece66dL + 0xb) & 0xffffffffffffL) >>> (48 - bits));
    }

    @Override
    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        } else {
            double v1, v2, s;
            do {
                v1 = 2 * nextDouble() - 1;
                v2 = 2 * nextDouble() - 1;
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);
            double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s)/s);
            nextNextGaussian = v2 * multiplier;
            haveNextNextGaussian = true;
            return v1 * multiplier;
        }
    }
}
