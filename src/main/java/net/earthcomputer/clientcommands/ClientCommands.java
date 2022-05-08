package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.command.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import org.reflections.Reflections;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class ClientCommands implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Path configDir;

    @Override
    public void onInitializeClient() {
        registerCommands(ClientCommandManager.DISPATCHER);

        configDir = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config dir", e);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        Reflections reflections = new Reflections("net.earthcomputer.clientcommands.command");
        reflections.getSubTypesOf(Object.class).forEach(clazz -> {
            try {
                clazz.getMethod("register", CommandDispatcher.class).invoke(null, dispatcher);
            } catch (Exception e) {
                LOGGER.error("Failed to register command", e);
            }
        });
    }
}
