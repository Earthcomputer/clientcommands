package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.command.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientCommands implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Path configDir;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(ClientCommands::registerCommands);

        configDir = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config dir", e);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        BookCommand.register(dispatcher);
        LookCommand.register(dispatcher);
        NoteCommand.register(dispatcher);
        ShrugCommand.register(dispatcher);
        FindCommand.register(dispatcher);
        FindBlockCommand.register(dispatcher, registryAccess);
        FindItemCommand.register(dispatcher, registryAccess);
        TaskCommand.register(dispatcher);
        CalcCommand.register(dispatcher);
        TempRuleCommand.register(dispatcher);
        RenderCommand.register(dispatcher);
        UsageTreeCommand.register(dispatcher);
        WikiCommand.register(dispatcher);
        CEnchantCommand.register(dispatcher);
        GlowCommand.register(dispatcher);
        GetDataCommand.register(dispatcher);
        CalcStackCommand.register(dispatcher, registryAccess);
        GammaCommand.register(dispatcher);
        MoteCommand.register(dispatcher);
        ChorusCommand.register(dispatcher);
        FishCommand.register(dispatcher, registryAccess);
        SignSearchCommand.register(dispatcher);
        GhostBlockCommand.register(dispatcher, registryAccess);
        RelogCommand.register(dispatcher);
        CGiveCommand.register(dispatcher, registryAccess);
        CPlaySoundCommand.register(dispatcher);
        CStopSoundCommand.register(dispatcher);
        FovCommand.register(dispatcher);
        HotbarCommand.register(dispatcher);
        KitCommand.register(dispatcher);
        ItemGroupCommand.register(dispatcher, registryAccess);
        CParticleCommand.register(dispatcher);
        PermissionLevelCommand.register(dispatcher);
        CTellRawCommand.register(dispatcher);
        CTimeCommand.register(dispatcher);
        AliasCommand.register(dispatcher);
        AreaStatsCommand.register(dispatcher, registryAccess);
        CTeleportCommand.register(dispatcher);
        PlayerInfoCommand.register(dispatcher);
        PingCommand.register(dispatcher);
        UuidCommand.register(dispatcher);
        SnakeCommand.register(dispatcher);
        BlockStateCommand.register(dispatcher, registryAccess);

        CrackRNGCommand.register(dispatcher);
    }
}
