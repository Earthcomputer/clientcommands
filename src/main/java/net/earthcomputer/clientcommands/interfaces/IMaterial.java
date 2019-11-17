package net.earthcomputer.clientcommands.interfaces;

import java.util.concurrent.atomic.AtomicInteger;

public interface IMaterial {

    AtomicInteger nextId = new AtomicInteger();

    int clientcommands_getId();

}
