package net.earthcomputer.clientcommands.interfaces;

import net.earthcomputer.clientcommands.util.RoundTripFence;

public interface IClientPacketListener {
    RoundTripFence clientcommands_getRoundTripFence();
}
