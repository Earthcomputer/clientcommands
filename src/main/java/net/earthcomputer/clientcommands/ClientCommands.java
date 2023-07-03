package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import dev.xpple.betterconfig.api.ModConfigBuilder;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public class ClientCommands implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Path configDir;

    public static final boolean SCRAMBLE_WINDOW_TITLE = Util.make(() -> {
        String playerUUID = MinecraftClient.getInstance().getSession().getProfile().getId().toString();

        Set<String> victims = Set.of(
                "fa68270b-1071-46c6-ac5c-6c4a0b777a96", // Earthcomputer
                "d4557649-e553-413e-a019-56d14548df96", // Azteched
                "8dc3d945-cf90-47c1-a122-a576319d05a7", // samnrad
                "c5d72740-cabc-42d1-b789-27859041d553", // allocator
                "e4093360-a200-4f99-aa13-be420b8d9a79", // Rybot666
                "083fb87e-c9e4-4489-8fb7-a45b06bfca90", // Kerbaras
                "973e8f6e-2f51-4307-97dc-56fdc71d194f" // KatieTheQt
        );

        return victims.contains(playerUUID);
    });

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(ClientCommands::registerCommands);

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            context.matrixStack().push();

            Vec3d cameraPos = context.camera().getPos();
            context.matrixStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            RenderQueue.render(RenderQueue.Layer.ON_TOP, Objects.requireNonNull(context.consumers()).getBuffer(RenderQueue.NO_DEPTH_LAYER), context.matrixStack(), context.tickDelta());

            context.matrixStack().pop();
        });

        configDir = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config dir", e);
        }

        new ModConfigBuilder("clientcommands", Configs.class).build();

        ItemGroupCommand.registerItemGroups();
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
