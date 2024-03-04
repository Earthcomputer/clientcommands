package net.earthcomputer.clientcommands.task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LongTaskList extends LongTask {

    private final List<LongTask> children = new ArrayList<>();

    public void addTask(LongTask task) {
        children.add(task);
    }

    @Override
    public void initialize() {
        if (!children.isEmpty()) {
            children.get(0).initialize();
        }
    }

    @Override
    public boolean condition() {
        return !children.isEmpty();
    }

    @Override
    public void increment() {
    }

    @Override
    public void body() {
        LongTask child = children.get(0);
        if (child.isCompleted()) {
            child.onCompleted();
            if (child.isDelayScheduled()) {
                scheduleDelay();
            }
            children.remove(0);
            if (!children.isEmpty()) {
                children.get(0).initialize();
            }
        } else {
            child.body();
            if (!child.isCompleted()) {
                child.increment();
            }
            if (child.isDelayScheduled()) {
                child.unscheduleDelay();
                scheduleDelay();
            }
        }
    }

    @Override
    public boolean stopOnLevelUnload(boolean isDisconnect) {
        boolean stop = false;
        for (LongTask child : children) {
            stop |= child.stopOnLevelUnload(isDisconnect);
        }
        return stop;
    }

    @Override
    public void onCompleted() {
        if (!children.isEmpty()) {
            children.get(0).onCompleted();
        }
    }

    @Override
    public Set<Object> getMutexKeys() {
        Set<Object> union = new HashSet<>();
        for (LongTask child : children) {
            union.addAll(child.getMutexKeys());
        }
        return union;
    }

    @Override
    public String toString() {
        Class<?> thisClass = getClass();
        String packageName = thisClass.getPackageName();
        String classDisplayName = packageName.isEmpty() ? thisClass.getName() : thisClass.getName().substring(packageName.length() + 1);
        return classDisplayName + children;
    }
}
