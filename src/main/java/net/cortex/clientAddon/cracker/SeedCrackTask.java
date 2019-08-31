package net.cortex.clientAddon.cracker;

import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.task.LongTask;

public class SeedCrackTask extends LongTask {
    @Override
    public void initialize() {
    }

    @Override
    public boolean condition() {
        return TempRules.playerCrackState == PlayerRandCracker.CrackState.CRACKING;
    }

    @Override
    public void increment() {
    }

    @Override
    public void body() {
        scheduleDelay();
    }

    @Override
    public void onCompleted() {
        if (condition())
            TempRules.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
        SeedCracker.currentTask = null;
    }
}
