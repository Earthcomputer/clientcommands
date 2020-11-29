package net.earthcomputer.clientcommands.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestRandom extends Random {
    private int count;
    private final List<StackTraceElement[]> stackTraces = new ArrayList<>();
    private final String name;

    public TestRandom(String name) {
        this.name = name;
    }

    @Override
    protected int next(int bits) {
        count++;
        stackTraces.add(Thread.currentThread().getStackTrace());
        return super.next(bits);
    }

    @Override
    public synchronized void setSeed(long seed) {
        System.out.println("Setting the " + name + " fishing bobber seed");
        super.setSeed(seed);
    }

    public void dump() {
        System.out.println("Called " + name + " fishing bobber Random " + count + " times since last tick");
        count = 0;
        stackTraces.clear();
    }
}
