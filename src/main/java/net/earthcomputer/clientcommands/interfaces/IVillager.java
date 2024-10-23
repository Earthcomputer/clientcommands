package net.earthcomputer.clientcommands.interfaces;

import net.earthcomputer.clientcommands.features.VillagerRngSimulator;

public interface IVillager {
    VillagerRngSimulator clientcommands_getVillagerRngSimulator();

    void clientcommands_onAmbientSoundPlayed(float pitch);

    void clientcommands_onNoSoundPlayed(float pitch);

    void clientcommands_onYesSoundPlayed(float pitch);

    void clientcommands_onSplashSoundPlayed(float pitch);

    void clientcommands_onXpOrbSpawned(int value);

    void clientcommands_onServerTick();
}
