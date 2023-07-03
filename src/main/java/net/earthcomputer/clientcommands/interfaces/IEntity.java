package net.earthcomputer.clientcommands.interfaces;

public interface IEntity {

    void addGlowingTicket(int ticks, int color);

    boolean hasGlowingTicket();

    void tickGlowingTickets();

    int callGetPermissionLevel();

    void tickDebugRandom();
}
