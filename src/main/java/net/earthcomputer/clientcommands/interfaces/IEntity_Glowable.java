package net.earthcomputer.clientcommands.interfaces;

public interface IEntity_Glowable {

    void clientcommands_addGlowingTicket(int ticks, int color);

    boolean clientcommands_hasGlowingTicket();

    void clientcommands_tickGlowingTickets();
}
