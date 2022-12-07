package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IHasPrivateKey;
import net.minecraft.client.util.ProfileKeysImpl;
import net.minecraft.network.encryption.PlayerKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.security.PrivateKey;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ProfileKeysImpl.class)
public class MixinProfileKeysImpl implements IHasPrivateKey {
    @Shadow private CompletableFuture<Optional<PlayerKeyPair>> keyFuture;

    @Override
    public Optional<PrivateKey> getPrivateKey() {
        return this.keyFuture.join().map(PlayerKeyPair::privateKey);
    }
}
