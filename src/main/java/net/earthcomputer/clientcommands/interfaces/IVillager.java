package net.earthcomputer.clientcommands.interfaces;

import com.seedfinding.mcseed.rand.JRand;
import net.earthcomputer.clientcommands.features.VillagerRngSimulator;
import org.jetbrains.annotations.Nullable;

public interface IVillager {
    void clientcommands_setRandom(@Nullable JRand random);

    VillagerRngSimulator clientcommands_getVillagerRngSimulator();

    void clientcommands_onAmbientSoundPlayed(float pitch);

    void clientcommands_onNoSoundPlayed(float pitch);

    void clientcommands_onYesSoundPlayed(float pitch);

    void clientcommands_onSplashSoundPlayed(float pitch);

    void clientcommands_onXpOrbSpawned(int value);

    void clientcommands_onServerTick();
}
