package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IHasPrivateKey;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.security.PrivateKey;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(AccountProfileKeyPairManager.class)
public class AccountProfileKeyPairManagerMixin implements IHasPrivateKey {
    @Shadow private CompletableFuture<Optional<ProfileKeyPair>> keyPair;

    @Override
    public Optional<PrivateKey> getPrivateKey() {
        return this.keyPair.join().map(ProfileKeyPair::privateKey);
    }
}
