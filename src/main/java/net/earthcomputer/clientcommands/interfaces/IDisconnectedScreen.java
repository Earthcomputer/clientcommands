package net.earthcomputer.clientcommands.interfaces;

public interface IDisconnectedScreen {

    void clientcommands_setCountdownMs(long ms);

    long clientcommands_getRemainingMs();
}
