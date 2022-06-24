package net.earthcomputer.clientcommands.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.earthcomputer.clientcommands.interfaces.IProfileKeys;
import net.minecraft.client.util.ProfileKeys;
import net.minecraft.network.encryption.PlayerKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(ProfileKeys.class)
public class MixinProfileKeys implements IProfileKeys {
    @Unique
    private CompletableFuture<Optional<PrivateKey>> privateKey;

    @Inject(method = "<init>", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    public void exposePrivateKeyToMemory(UserApiService userApiService, UUID uuid, Path root, CallbackInfo ci, CompletableFuture<Optional<PlayerKeyPair>> completableFuture) {
        privateKey = completableFuture.thenApply(keyPair -> keyPair.map(PlayerKeyPair::privateKey));
    }

    @Override
    public Optional<PrivateKey> getPrivateKey() {
        return privateKey.join();
    }

}
