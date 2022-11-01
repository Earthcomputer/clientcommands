package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IProfileKeys;
import net.minecraft.client.util.ProfileKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.security.PrivateKey;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ProfileKeys.class)
public class MixinProfileKeys implements IProfileKeys {
    @Shadow private CompletableFuture<Optional<ProfileKeys.SignableKey>> keyFuture;

    @Override
    public Optional<PrivateKey> getPrivateKey() {
        return this.keyFuture.join().map(arg -> arg.keyPair().privateKey());
    }
}
