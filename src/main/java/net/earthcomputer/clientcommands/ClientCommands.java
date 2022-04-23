package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.command.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        BookCommand.register(dispatcher);
        LookCommand.register(dispatcher);
        NoteCommand.register(dispatcher);
        ShrugCommand.register(dispatcher);
        FindCommand.register(dispatcher);
        FindBlockCommand.register(dispatcher);
        FindItemCommand.register(dispatcher);
        TaskCommand.register(dispatcher);
        CalcCommand.register(dispatcher);
        TempRuleCommand.register(dispatcher);
        RenderCommand.register(dispatcher);
        UsageTreeCommand.register(dispatcher);
        WikiCommand.register(dispatcher);
        CEnchantCommand.register(dispatcher);
        GlowCommand.register(dispatcher);
        GetDataCommand.register(dispatcher);
        CalcStackCommand.register(dispatcher);
        GammaCommand.register(dispatcher);
        MoteCommand.register(dispatcher);
        ChorusCommand.register(dispatcher);
        FishCommand.register(dispatcher);
        SignSearchCommand.register(dispatcher);
        GhostBlockCommand.register(dispatcher);
        RelogCommand.register(dispatcher);
        CGiveCommand.register(dispatcher);
        CPlaySoundCommand.register(dispatcher);
        CStopSoundCommand.register(dispatcher);
        FovCommand.register(dispatcher);
        HotbarCommand.register(dispatcher);
        KitCommand.register(dispatcher);
        ItemGroupCommand.register(dispatcher);
        CParticleCommand.register(dispatcher);
        PermissionLevelCommand.register(dispatcher);
        CTellRawCommand.register(dispatcher);
        CTimeCommand.register(dispatcher);
        AliasCommand.register(dispatcher);
        AreaStatsCommand.register(dispatcher);
        CTeleportCommand.register(dispatcher);
        PlayerInfoCommand.register(dispatcher);
        PingCommand.register(dispatcher);
        CrackRNGCommand.register(dispatcher);
    }
}
