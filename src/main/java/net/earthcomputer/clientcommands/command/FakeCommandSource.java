package net.earthcomputer.clientcommands.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Collection;
import java.util.stream.Collectors;

public class FakeCommandSource extends ServerCommandSource {
    public FakeCommandSource(ClientPlayerEntity player) {
        super(player, player.getPos(), player.getRotationClient(), null, 314159265, player.getEntityName(), player.getName(), null, player);
    }

    @Override
    public Collection<String> getPlayerNames() {
        return MinecraftClient.getInstance().getNetworkHandler().getPlayerList()
                .stream().map(e -> e.getProfile().getName()).collect(Collectors.toList());
    }

    @Override
    public DynamicRegistryManager getRegistryManager() {
        return MinecraftClient.getInstance().getNetworkHandler().getRegistryManager();
    }
}
