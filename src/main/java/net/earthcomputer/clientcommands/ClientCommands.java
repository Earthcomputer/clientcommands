package net.earthcomputer.clientcommands;

import net.fabricmc.api.ClientModInitializer;

public class ClientCommands implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("===== CLIENTCOMMANDS INITIALIZING =====");
    }
}
