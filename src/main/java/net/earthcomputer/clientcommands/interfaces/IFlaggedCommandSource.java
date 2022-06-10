package net.earthcomputer.clientcommands.interfaces;

public interface IFlaggedCommandSource {

    int getFlags();

    IFlaggedCommandSource withFlags(int flags);

}
