package net.earthcomputer.clientcommands.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;

@Disabled("Disabled until mixin audit doesn't run the initializer of classes")
public final class MixinApplyTest {
    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void auditMixins() {
        MixinEnvironment.getCurrentEnvironment().audit();
    }
}
