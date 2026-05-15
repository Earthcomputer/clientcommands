package net.earthcomputer.clientcommands.test;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class MixinApplyTest {
    @Test
    public void auditMixins() {
        MixinEnvironment.getCurrentEnvironment().audit();
    }
}
