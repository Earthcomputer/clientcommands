package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.script.ScriptManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;

import java.io.File;

public class ClientCommands implements ClientModInitializer {

    public static File configDir;

    @Override
    public void onInitializeClient() {
        configDir = new File(FabricLoader.getInstance().getConfigDirectory(), "clientcommands");
        //noinspection ResultOfMethodCallIgnored
        configDir.mkdirs();

        ScriptManager.reloadScripts();
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        ClientCommandManager.clearClientSideCommands();
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
        CHelpCommand.register(dispatcher);
        WikiCommand.register(dispatcher);
        CEnchantCommand.register(dispatcher);
        GlowCommand.register(dispatcher);
        GetDataCommand.register(dispatcher);
        ScriptCommand.register(dispatcher);
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
        AreaStatsCommand.register(dispatcher);

        CrackRNGCommand.register(dispatcher);

        if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
            CheatCrackRNGCommand.register(dispatcher);
        }
    }
}
