package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.util.math.Vec3d;
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

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            context.matrixStack().push();

            Vec3d cameraPos = context.camera().getPos();
            context.matrixStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            RenderQueue.render(RenderQueue.Layer.ON_TOP, context.consumers().getBuffer(RenderQueue.noDepthLayer()), context.matrixStack(), context.tickDelta());

            context.matrixStack().pop();
        });

        configDir = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config dir", e);
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        AuditMixinsCommand.register(dispatcher);
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
        // PlayerInfoCommand.register(dispatcher);
        PingCommand.register(dispatcher);
        UuidCommand.register(dispatcher);
        SnakeCommand.register(dispatcher);
        CTitleCommand.register(dispatcher);
        TooltipCommand.register(dispatcher, registryAccess);
        TranslateCommand.register(dispatcher);
        VarCommand.register(dispatcher);
        CFunctionCommand.register(dispatcher);
        StartupCommand.register(dispatcher);
        WhisperEncryptedCommand.register(dispatcher);
        PosCommand.register(dispatcher);
        CrackRNGCommand.register(dispatcher);
        WeatherCommand.register(dispatcher);
    }
}
