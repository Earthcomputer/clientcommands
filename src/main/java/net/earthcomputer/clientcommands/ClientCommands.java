// CHECKSTYLE:OFF: AvoidStarImport allow commands to be wildcard imported
package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import dev.xpple.betterconfig.api.BetterConfigAPI;
import dev.xpple.betterconfig.api.ModConfigBuilder;
import dev.xpple.simplewaypoints.SimpleWaypoints;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.earthcomputer.clientcommands.features.CommandExecutionCustomPayload;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.Relogger;
import net.earthcomputer.clientcommands.features.ServerBrandManager;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.util.MappingsHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientCommands implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
    private static final Set<String> clientcommandsCommands = new HashSet<>();
    private static final Set<String> COMMANDS_TO_NOT_SEND_TO_SERVER = Set.of("cwe", "cnote"); // could contain private information

    public static boolean scrambleWindowTitle = false;

    private static final Set<String> CHAT_COMMAND_USERS = Set.of(
        "b793c3b9-425f-4dd8-a056-9dec4d835e24", // wsb
        "0071ccd7-467f-4e71-8237-cb15f229a1ff", // 8YX
        "c3bca648-b8ce-491d-bf6a-36bb42c5a70b" // Y99
    );

    @Override
    public void onInitializeClient() {
        RenderQueue.register();

        setupScrambleWindowTitle();

        // Config
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create config dir", e);
        }

        migrateWaypoints();

        new ModConfigBuilder<>("clientcommands", Configs.class).build();
        ClientConnectionEvents.DISCONNECT.register(() -> {
            if (!Relogger.isRelogging) {
                BetterConfigAPI.getInstance().getModConfig("clientcommands").resetTemporaryConfigs();
            }
        });

        MappingsHelper.load();

        // Registration
        PayloadTypeRegistry.playC2S().register(CommandExecutionCustomPayload.TYPE, CommandExecutionCustomPayload.CODEC);
        CreativeTabCommand.registerCreativeTabs();

        // Events
        ClientCommandRegistrationCallback.EVENT.register(ClientCommands::registerCommands);
        EnchantmentCracker.registerEvents();
        FishingCracker.registerEvents();
        PlayerRandCracker.registerEvents();
        ServerBrandManager.registerEvents();
    }

    private static void migrateWaypoints() {
        Path clientcommandsWaypointsPath = CONFIG_DIR.resolve("waypoints.dat");
        if (!Files.isRegularFile(clientcommandsWaypointsPath)) {
            return; // nothing to migrate, or migration already done
        }
        LOGGER.info("clientcommands' waypoints.dat file detected, will be migrated to SimpleWaypoints");
        Path clientcommandsOldWaypointsPath = CONFIG_DIR.resolve("waypoints.dat_old");
        boolean deleteOld;
        try {
            Files.copy(clientcommandsWaypointsPath, clientcommandsOldWaypointsPath, StandardCopyOption.REPLACE_EXISTING);
            deleteOld = true;
        } catch (IOException e) {
            LOGGER.error("Could not back up waypoints.dat file", e);
            deleteOld = false;
        }
        Path simplewaypointsWaypointsPath = SimpleWaypoints.MOD_CONFIG_PATH.resolve("waypoints.dat");
        if (Files.isRegularFile(simplewaypointsWaypointsPath)) {
            LOGGER.info("SimpleWaypoints' waypoints.dat file already exists, clientcommands' waypoints.dat file will be merged");
            try {
                CompoundTag clientcommandsCompoundTag = NbtIo.read(clientcommandsWaypointsPath);
                CompoundTag simplewaypointsCompoundTag = NbtIo.read(simplewaypointsWaypointsPath);
                if (clientcommandsCompoundTag == null || simplewaypointsCompoundTag == null) {
                    throw new IOException("Could not parse clientcommands' or SimpleWaypoints' waypoint.dat file");
                }
                simplewaypointsCompoundTag.merge(clientcommandsCompoundTag);
                NbtIo.write(simplewaypointsCompoundTag, simplewaypointsWaypointsPath);
            } catch (IOException e) {
                LOGGER.error("Could not merge waypoints.dat file", e);
            }
        } else {
            LOGGER.info("SimpleWaypoints' waypoints.dat does not exist yet, clientcommands' waypoints.dat file will be copied");
            try {
                Files.copy(clientcommandsWaypointsPath, simplewaypointsWaypointsPath);
            } catch (IOException e) {
                LOGGER.error("Could not migrate waypoints.dat file", e);
            }
        }
        if (deleteOld) {
            LOGGER.info("clientcommands's waypoint.dat will be deleted, waypoints.dat_old is kept");
            try {
                Files.delete(clientcommandsWaypointsPath);
            } catch (IOException e) {
                LOGGER.error("Could not delete waypoints.dat file during migration", e);
            }
        }
    }

    private static void setupScrambleWindowTitle() {
        // can't set this up during class initializer, because Minecraft.getInstance() is null during automated tests
        String playerUUID = String.valueOf(Minecraft.getInstance().getUser().getProfileId());

        Set<String> victims = Set.of(
            "fa68270b-1071-46c6-ac5c-6c4a0b777a96", // Earthcomputer
            "d4557649-e553-413e-a019-56d14548df96", // Azteched
            "8dc3d945-cf90-47c1-a122-a576319d05a7", // samnrad
            "c5d72740-cabc-42d1-b789-27859041d553", // allocator
            "e4093360-a200-4f99-aa13-be420b8d9a79", // Rybot666
            "083fb87e-c9e4-4489-8fb7-a45b06bfca90", // Kerbaras
            "973e8f6e-2f51-4307-97dc-56fdc71d194f" // KatieTheQt
        );

        scrambleWindowTitle = victims.contains(playerUUID) || Boolean.getBoolean("clientcommands.scrambleWindowTitle");
    }

    private static Set<String> getCommands(CommandDispatcher<?> dispatcher) {
        return dispatcher.getRoot().getChildren().stream().flatMap(node -> node instanceof LiteralCommandNode<?> literal ? Stream.of(literal.getLiteral()) : Stream.empty()).collect(Collectors.toSet());
    }

    public static void sendCommandExecutionToServer(String command) {
        StringReader reader = new StringReader(command);
        reader.skipWhitespace();
        String theCommand = reader.readUnquotedString();
        if (clientcommandsCommands.contains(theCommand) && !COMMANDS_TO_NOT_SEND_TO_SERVER.contains(theCommand)) {
            if (ClientPlayNetworking.canSend(CommandExecutionCustomPayload.TYPE)) {
                ClientPlayNetworking.send(new CommandExecutionCustomPayload(command));
            }
        }
    }

    public static boolean isClientcommandsCommand(String commandName) {
        return clientcommandsCommands.contains(commandName);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        Set<String> existingCommands = getCommands(dispatcher);

        AliasCommand.register(dispatcher);
        AreaStatsCommand.register(dispatcher, context);
        AuditMixinsCommand.register(dispatcher);
        BookCommand.register(dispatcher);
        BuildInfoCommand.register(dispatcher);
        CalcCommand.register(dispatcher);
        CalcStackCommand.register(dispatcher, context);
        CallbackCommand.register(dispatcher);
        CDebugCommand.register(dispatcher);
        CEnchantCommand.register(dispatcher, context);
        CFunctionCommand.register(dispatcher);
        CGameModeCommand.register(dispatcher);
        CGiveCommand.register(dispatcher, context);
        ChorusCommand.register(dispatcher);
        ConnectFourCommand.register(dispatcher);
        CParticleCommand.register(dispatcher, context);
        CPlaySoundCommand.register(dispatcher);
        CrackRNGCommand.register(dispatcher);
        CreativeTabCommand.register(dispatcher, context);
        CStopSoundCommand.register(dispatcher);
        CTeleportCommand.register(dispatcher);
        CTellRawCommand.register(dispatcher, context);
        CTimeCommand.register(dispatcher);
        CTitleCommand.register(dispatcher, context);
        FindBlockCommand.register(dispatcher, context);
        FindCommand.register(dispatcher);
        FindItemCommand.register(dispatcher, context);
        FishCommand.register(dispatcher, context);
        FovCommand.register(dispatcher);
        FramerateCommand.register(dispatcher);
        GammaCommand.register(dispatcher);
        GetDataCommand.register(dispatcher);
        GhostBlockCommand.register(dispatcher, context);
        GlowCommand.register(dispatcher);
        HotbarCommand.register(dispatcher);
        KitCommand.register(dispatcher);
        ListenCommand.register(dispatcher);
        LookCommand.register(dispatcher);
        MapCommand.register(dispatcher);
        MinesweeperCommand.register(dispatcher);
        MoteCommand.register(dispatcher);
        NoteCommand.register(dispatcher);
        PermissionLevelCommand.register(dispatcher);
        PingCommand.register(dispatcher);
        // PlayerInfoCommand.register(dispatcher);
        PluginsCommand.register(dispatcher);
        PosCommand.register(dispatcher);
        RelogCommand.register(dispatcher);
        RenderCommand.register(dispatcher);
        ReplyCommand.register(dispatcher);
        ShrugCommand.register(dispatcher);
        SignSearchCommand.register(dispatcher);
        SnakeCommand.register(dispatcher);
        SnapCommand.register(dispatcher);
        StartupCommand.register(dispatcher);
        TaskCommand.register(dispatcher);
        TicTacToeCommand.register(dispatcher);
        TooltipCommand.register(dispatcher, context);
        TranslateCommand.register(dispatcher);
        UsageTreeCommand.register(dispatcher);
        UuidCommand.register(dispatcher);
        VarCommand.register(dispatcher);
        WeatherCommand.register(dispatcher);
        WhisperEncryptedCommand.register(dispatcher);
        WikiCommand.register(dispatcher);

        Calendar calendar = Calendar.getInstance();
        boolean registerChatCommand = calendar.get(Calendar.MONTH) == Calendar.APRIL && calendar.get(Calendar.DAY_OF_MONTH) == 1;
        registerChatCommand |= CHAT_COMMAND_USERS.contains(String.valueOf(Minecraft.getInstance().getUser().getProfileId()));
        registerChatCommand |= Boolean.getBoolean("clientcommands.debugChatCommand");
        if (registerChatCommand) {
            ChatCommand.register(dispatcher);
        }

        clientcommandsCommands.clear();
        for (String command : getCommands(dispatcher)) {
            if (!existingCommands.contains(command)) {
                clientcommandsCommands.add(command);
            }
        }
    }
}
