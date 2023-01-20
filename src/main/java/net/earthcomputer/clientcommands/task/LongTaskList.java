package net.earthcomputer.clientcommands.task;

import java.util.ArrayList;
import java.util.List;

public class LongTaskList extends LongTask {

    private List<LongTask> children = new ArrayList<>();

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

}
