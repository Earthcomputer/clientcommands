package net.earthcomputer.clientcommands.features;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

public class VillagerRngSimulator {
    @Nullable
    private final LegacyRandomSource random;
    private int ambientSoundTime;

    public VillagerRngSimulator(@Nullable LegacyRandomSource random, int ambientSoundTime) {
        this.random = random;
        this.ambientSoundTime = ambientSoundTime;
    }

    @Override
    protected Object clone() {
        return new VillagerRngSimulator(random == null ? null : new LegacyRandomSource(random.seed.get() ^ 0x5deece66dL), ambientSoundTime);
    }

    public boolean simulateTick() {
        boolean madeSound;

        if (random == null) {
            return false;
        }

        if (random.nextInt(1000) < ambientSoundTime++) {
            random.nextFloat();
            random.nextFloat();
            ambientSoundTime = -80;
            madeSound = true;
        } else {
            madeSound = false;
        }

        return madeSound;
    }

    public LegacyRandomSource random() {
        return random;
    }

    public int getAmbientSoundTime() {
        return ambientSoundTime;
    }

    @Override
    public String toString() {
        return "VillagerRngSimulator[" +
            "seed=" + (random == null ? "null" : random.seed.get()) + ", " +
            "ambientSoundTime=" + ambientSoundTime + ']';
    }

    public void onAmbientSoundPlayed() {
        ambientSoundTime = -80;
        if (random != null) {
            random.nextFloat();
            random.nextFloat();
        }
    }
}
