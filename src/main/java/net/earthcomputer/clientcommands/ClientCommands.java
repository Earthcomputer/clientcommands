// CHECKSTYLE:OFF: AvoidStarImport allow commands to be wildcard imported
package net.earthcomputer.clientcommands;

import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import dev.xpple.betterconfig.api.BetterConfigAPI;
import dev.xpple.betterconfig.api.ModConfigBuilder;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.earthcomputer.clientcommands.features.CommandExecutionCustomPayload;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.ServerBrandManager;
import net.earthcomputer.clientcommands.util.MappingsHelper;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.Relogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector2d;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientCommands implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Path configDir;
    private static final Set<String> clientcommandsCommands = new HashSet<>();
    private static final Set<String> COMMANDS_TO_NOT_SEND_TO_SERVER = Set.of("cwe", "cnote"); // could contain private information

    public static final boolean SCRAMBLE_WINDOW_TITLE = Util.make(() -> {
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

        return victims.contains(playerUUID) || Boolean.getBoolean("clientcommands.scrambleWindowTitle");
    });

    private static final Set<String> CHAT_COMMAND_USERS = Set.of(
        "b793c3b9-425f-4dd8-a056-9dec4d835e24", // wsb
        "0071ccd7-467f-4e71-8237-cb15f229a1ff", // 8YX
        "c3bca648-b8ce-491d-bf6a-36bb42c5a70b" // Y99
    );

    @Override
    public void onInitializeClient() {
        // Config
        configDir = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config dir", e);
        }

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
        FishingCracker.registerEvents();
        PlayerRandCracker.registerEvents();
        ServerBrandManager.registerEvents();

        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            String worldIdentifier = WaypointCommand.getWorldIdentifier(Minecraft.getInstance());
            Map<String, Pair<BlockPos, ResourceKey<Level>>> waypoints = WaypointCommand.waypoints.get(worldIdentifier);
            if (waypoints == null) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            GameRenderer gameRenderer = minecraft.gameRenderer;
            Camera camera = gameRenderer.getMainCamera();
            Entity cameraEntity = camera.getEntity();
            float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
            double verticalFovRad = Math.toRadians(gameRenderer.getFov(camera, partialTicks, false));
            Window window = minecraft.getWindow();
            double aspectRatio = (double) window.getGuiScaledWidth() / window.getGuiScaledHeight();
            double horizontalFovRad = 2 * Math.atan(Math.tan(verticalFovRad / 2) * aspectRatio);

            Vec3 viewVector3 = cameraEntity.getViewVector(1.0f);
            Vector2d viewVector = new Vector2d(viewVector3.x, viewVector3.z);
            Vector2d position = new Vector2d(cameraEntity.getEyePosition().x, cameraEntity.getEyePosition().z);

            PriorityQueue<Pair<Component, Integer>> xPositionsBuilder = new PriorityQueue<>(Comparator.comparingInt(Pair::getRight));
            waypoints.forEach((waypointName, waypoint) -> {
                if (!waypoint.getRight().location().equals(minecraft.level.dimension().location())) {
                    return;
                }

                long offset = Math.round(waypoint.getLeft().getY() - cameraEntity.position().y);
                ChatFormatting colour;
                String symbol;
                if (offset >= 0) {
                    colour = ChatFormatting.GREEN;
                    symbol = "+";
                }
                else {
                    colour = ChatFormatting.RED;
                    symbol = "-";
                }

                MutableComponent waypointComponent = Component.literal(waypointName).append(Component.literal(' ' + symbol + Math.abs(offset)).withStyle(colour));

                Vector2d waypointLocation = new Vector2d(waypoint.getLeft().getX(), waypoint.getLeft().getZ());
                double angleRad = viewVector.angle(waypointLocation.sub(position, new Vector2d()));
                boolean right = angleRad > 0;
                angleRad = Math.abs(angleRad);

                int x;
                if (angleRad > horizontalFovRad / 2) {
                    int width = minecraft.font.width(waypointComponent);
                    x = right ? guiGraphics.guiWidth() - width / 2 : width / 2;
                } else {
                    // V is the view vector
                    // A is the leftmost visible direction
                    // B is the rightmost visible direction
                    // M is the intersection of the waypoint ray with AB
                    double mv = Math.tan(angleRad) * GameRenderer.PROJECTION_Z_NEAR;
                    double av = Math.tan(horizontalFovRad / 2) * GameRenderer.PROJECTION_Z_NEAR;
                    double ab = 2 * av;
                    double am = right ? mv + av : ab - (mv + av);
                    double perc = am / ab;
                    x = (int) (perc * guiGraphics.guiWidth());
                }
                xPositionsBuilder.offer(Pair.of(waypointComponent, x));
            });

            List<Pair<Component, Integer>> xPositions = new ArrayList<>();
            int waypointAmount = xPositionsBuilder.size();
            for (int i = 0; i < waypointAmount; i++) {
                xPositions.add(xPositionsBuilder.poll());
            }

            int yOffset = 1;
            Map<Integer, List<Pair<Component, Integer>>> positions = new HashMap<>();
            positions.put(yOffset, xPositions);

            while (true) {
                List<Pair<Component, Integer>> pairs = positions.get(yOffset);
                if (pairs == null) {
                    break;
                }
                int i = 0;
                while (i < pairs.size() - 1) {
                    Pair<Component, Integer> leftPair = pairs.get(i);
                    Pair<Component, Integer> rightPair = pairs.get(i + 1);
                    Integer leftX = leftPair.getRight();
                    Integer rightX = rightPair.getRight();
                    int leftWidth = minecraft.font.width(leftPair.getLeft());
                    int rightWidth = minecraft.font.width(rightPair.getLeft());
                    if (leftWidth / 2 + rightWidth / 2 > rightX - leftX) {
                        List<Pair<Component, Integer>> nextLevel = positions.computeIfAbsent(yOffset + minecraft.font.lineHeight, k -> new ArrayList<>());
                        Pair<Component, Integer> removed = pairs.remove(i + 1);
                        nextLevel.add(removed);
                    } else {
                        i++;
                    }
                }
                yOffset += minecraft.font.lineHeight;
            }

            positions.forEach((y, w) -> w.forEach(waypoint -> guiGraphics.drawCenteredString(minecraft.font, waypoint.getLeft(), waypoint.getRight(), y, 0xFFFFFF)));
        });
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
        CalcCommand.register(dispatcher);
        CalcStackCommand.register(dispatcher, context);
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
        GammaCommand.register(dispatcher);
        GetDataCommand.register(dispatcher);
        GhostBlockCommand.register(dispatcher, context);
        GlowCommand.register(dispatcher);
        HotbarCommand.register(dispatcher);
        KitCommand.register(dispatcher);
        ListenCommand.register(dispatcher);
        LookCommand.register(dispatcher);
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
        WaypointCommand.register(dispatcher);
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
