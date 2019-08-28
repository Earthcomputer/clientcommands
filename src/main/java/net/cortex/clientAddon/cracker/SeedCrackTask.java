package net.cortex.clientAddon.cracker;

import net.earthcomputer.clientcommands.task.LongTask;

public class SeedCrackTask extends LongTask {
    @Override
    public void initialize() {
    }

    @Override
    public boolean condition() {
        return SeedCracker.cracking;
    }

    @Override
    public void increment() {
    }

    @Override
    public void body() {
        scheduleDelay();
    }

    @Override
    public void onCompleted() {
        SeedCracker.cracking = false;
        SeedCracker.currentTask = null;
    }
}
