package net.cortex.clientAddon.cracker;

import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.task.SimpleTask;

public class SeedCrackTask extends SimpleTask {
    @Override
    public boolean condition() {
        return TempRules.playerCrackState == PlayerRandCracker.CrackState.CRACKING;
    }

    @Override
    protected void onTick() {
    }

    @Override
    public void onCompleted() {
        if (condition())
            TempRules.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
        SeedCracker.currentTask = null;
    }
}
