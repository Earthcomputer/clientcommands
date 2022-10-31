package net.earthcomputer.clientcommands.interfaces;

import java.security.PrivateKey;
import java.util.Optional;

public interface IProfileKeys {
    Optional<PrivateKey> getPrivateKey();
}
