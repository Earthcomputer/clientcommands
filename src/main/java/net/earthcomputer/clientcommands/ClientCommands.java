package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import dev.xpple.betterconfig.api.ModConfigBuilder;
import io.netty.buffer.Unpooled;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.features.MappingsHelper;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.DetectedVersion;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientCommands implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Path configDir;
    private static final Set<String> clientcommandsCommands = new HashSet<>();
    public static final ResourceLocation COMMAND_EXECUTION_PACKET_ID = new ResourceLocation("clientcommands", "command_execution");
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
        ClientCommandRegistrationCallback.EVENT.register(ClientCommands::registerCommands);

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            context.matrixStack().pushPose();

            Vec3 cameraPos = context.camera().getPosition();
            context.matrixStack().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            RenderQueue.render(RenderQueue.Layer.ON_TOP, Objects.requireNonNull(context.consumers()).getBuffer(RenderQueue.NO_DEPTH_LAYER), context.matrixStack(), context.tickDelta());

            context.matrixStack().popPose();
        });

        configDir = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config dir", e);
        }

        new ModConfigBuilder("clientcommands", Configs.class).build();

        ItemGroupCommand.registerItemGroups();

        MappingsHelper.initMappings(DetectedVersion.BUILT_IN.getName());
    }

    private static Set<String> getCommands(CommandDispatcher<?> dispatcher) {
        return dispatcher.getRoot().getChildren().stream().flatMap(node -> node instanceof LiteralCommandNode<?> literal ? Stream.of(literal.getLiteral()) : Stream.empty()).collect(Collectors.toSet());
    }

    public static void sendCommandExecutionToServer(String command) {
        StringReader reader = new StringReader(command);
        reader.skipWhitespace();
        String theCommand = reader.readUnquotedString();
        if (clientcommandsCommands.contains(theCommand) && !COMMANDS_TO_NOT_SEND_TO_SERVER.contains(theCommand)) {
            if (ClientPlayNetworking.canSend(COMMAND_EXECUTION_PACKET_ID)) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(command);
                ClientPlayNetworking.send(COMMAND_EXECUTION_PACKET_ID, buf);
            }
        }
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        Set<String> existingCommands = getCommands(dispatcher);

        AuditMixinsCommand.register(dispatcher);
        BookCommand.register(dispatcher);
        LookCommand.register(dispatcher);
        NoteCommand.register(dispatcher);
        ShrugCommand.register(dispatcher);
        FindCommand.register(dispatcher);
        FindBlockCommand.register(dispatcher, context);
        FindItemCommand.register(dispatcher, context);
        TaskCommand.register(dispatcher);
        CalcCommand.register(dispatcher);
        RenderCommand.register(dispatcher);
        UsageTreeCommand.register(dispatcher);
        WikiCommand.register(dispatcher);
        CEnchantCommand.register(dispatcher);
        GlowCommand.register(dispatcher);
        GetDataCommand.register(dispatcher);
        CalcStackCommand.register(dispatcher, context);
        GammaCommand.register(dispatcher);
        MoteCommand.register(dispatcher);
        ChorusCommand.register(dispatcher);
        FishCommand.register(dispatcher, context);
        SignSearchCommand.register(dispatcher);
        GhostBlockCommand.register(dispatcher, context);
        RelogCommand.register(dispatcher);
        CGiveCommand.register(dispatcher, context);
        CPlaySoundCommand.register(dispatcher);
        CStopSoundCommand.register(dispatcher);
        FovCommand.register(dispatcher);
        HotbarCommand.register(dispatcher);
        KitCommand.register(dispatcher);
        ItemGroupCommand.register(dispatcher, context);
        CParticleCommand.register(dispatcher);
        PermissionLevelCommand.register(dispatcher);
        CTellRawCommand.register(dispatcher);
        CTimeCommand.register(dispatcher);
        AliasCommand.register(dispatcher);
        AreaStatsCommand.register(dispatcher, context);
        CTeleportCommand.register(dispatcher);
        // PlayerInfoCommand.register(dispatcher);
        PingCommand.register(dispatcher);
        UuidCommand.register(dispatcher);
        SnakeCommand.register(dispatcher);
        CTitleCommand.register(dispatcher);
        TooltipCommand.register(dispatcher, context);
        TranslateCommand.register(dispatcher);
        VarCommand.register(dispatcher);
        CFunctionCommand.register(dispatcher);
        StartupCommand.register(dispatcher);
        WhisperEncryptedCommand.register(dispatcher);
        PosCommand.register(dispatcher);
        CrackRNGCommand.register(dispatcher);
        WeatherCommand.register(dispatcher);
        PluginsCommand.register(dispatcher);
        ListenCommand.register(dispatcher);

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
