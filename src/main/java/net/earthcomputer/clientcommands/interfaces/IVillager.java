package net.earthcomputer.clientcommands.interfaces;

import net.earthcomputer.clientcommands.features.VillagerRngSimulator;
import net.minecraft.util.RandomSource;

public interface IVillager {
    void clientcommands_setCrackedRandom(RandomSource random);

    VillagerRngSimulator clientcommands_getCrackedRandom();

    void clientcommands_onAmbientSoundPlayed();

    void clientcommands_onServerTick();
}
