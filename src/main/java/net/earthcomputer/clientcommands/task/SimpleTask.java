package net.earthcomputer.clientcommands.task;

public abstract class SimpleTask extends LongTask {
    @Override
    public void initialize() {
    }

    @Override
    public void increment() {
    }

    @Override
    public void body() {
        onTick();
        scheduleDelay();
    }

    protected abstract void onTick();
}
