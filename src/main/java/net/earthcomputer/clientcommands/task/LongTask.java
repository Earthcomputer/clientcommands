package net.earthcomputer.clientcommands.task;

import java.util.Set;

/**
 * Acts like a for loop that can delay between iterations to allow the game to continue ticking
 */
public abstract class LongTask {

    boolean isInitialized = false;
    private boolean delayScheduled;
    private boolean broken = false;

    public abstract void initialize();

    public abstract boolean condition();

    public abstract void increment();

    public abstract void body();

    public final void _break() {
        broken = true;
    }

    public final boolean isCompleted() {
        return broken || !condition();
    }

    public void onCompleted() {}

    public final void scheduleDelay() {
        delayScheduled = true;
    }

    public final void unscheduleDelay() {
        delayScheduled = false;
    }

    public final boolean isDelayScheduled() {
        return delayScheduled;
    }

    public boolean stopOnLevelUnload(boolean isDisconnect) {
        return true;
    }

    public Set<Object> getMutexKeys() {
        return Set.of();
    }

    public final boolean conflictsWith(LongTask other) {
        return getMutexKeys().stream().anyMatch(other.getMutexKeys()::contains);
    }
}
