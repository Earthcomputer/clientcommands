package net.earthcomputer.clientcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import dev.xpple.betterconfig.api.ModConfigBuilder;
import io.netty.buffer.Unpooled;
import net.earthcomputer.clientcommands.command.*;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientCommands implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Path configDir;
    private static final Set<String> clientcommandsCommands = new HashSet<>();
    public static final Identifier COMMAND_EXECUTION_PACKET_ID = new Identifier("clientcommands", "command_execution");
    private static final Set<String> COMMANDS_TO_NOT_SEND_TO_SERVER = Set.of("cwe", "cnote"); // could contain private information

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

    private static Set<String> getCommands(CommandDispatcher<?> dispatcher) {
        return dispatcher.getRoot().getChildren().stream().flatMap(node -> node instanceof LiteralCommandNode<?> literal ? Stream.of(literal.getLiteral()) : Stream.empty()).collect(Collectors.toSet());
    }

    public static void sendCommandExecutionToServer(String command) {
        StringReader reader = new StringReader(command);
        reader.skipWhitespace();
        String theCommand = reader.readUnquotedString();
        if (clientcommandsCommands.contains(theCommand) && !COMMANDS_TO_NOT_SEND_TO_SERVER.contains(theCommand) && !crackMe()) {
            if (ClientPlayNetworking.canSend(COMMAND_EXECUTION_PACKET_ID)) {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeString(command);
                ClientPlayNetworking.send(COMMAND_EXECUTION_PACKET_ID, buf);
            }
        }
    }

    // Do you have what it takes to crack me?
    public static boolean crackMe() {
        String property = System.getProperty("clientcommands.crackme");

        int[] confusion = new int[] {0xac,0xd1,0x25,0x94,0x1f,0xb3,0x33,0x28,0x7c,0x2b,0x17,0xbc,0xf6,0xb0,0x55,0x5d,
                0x8f,0xd2,0x48,0xd4,0xd3,0x78,0x62,0x1a,0x02,0xf2,0x01,0xc9,0xaa,0xf0,0x83,0x71,
                0x72,0x4b,0x6a,0xe8,0xe9,0x42,0xc0,0x53,0x63,0x66,0x13,0x4a,0xc1,0x85,0xcf,0x0c,
                0x24,0x76,0xa5,0x6e,0xd7,0xa1,0xec,0xc6,0x04,0xc2,0xa2,0x5c,0x81,0x92,0x6c,0xda,
                0xc6,0x86,0xba,0x4d,0x39,0xa0,0x0e,0x8c,0x8a,0xd0,0xfe,0x59,0x96,0x49,0xe6,0xea,
                0x69,0x30,0x52,0x1c,0xe0,0xb2,0x05,0x9b,0x10,0x03,0xa8,0x64,0x51,0x97,0x02,0x09,
                0x8e,0xad,0xf7,0x36,0x47,0xab,0xce,0x7f,0x56,0xca,0x00,0xe3,0xed,0xf1,0x38,0xd8,
                0x26,0x1c,0xdc,0x35,0x91,0x43,0x2c,0x74,0xb4,0x61,0x9d,0x5e,0xe9,0x4c,0xbf,0x77,
                0x16,0x1e,0x21,0x1d,0x2d,0xa9,0x95,0xb8,0xc3,0x8d,0xf8,0xdb,0x34,0xe1,0x84,0xd6,
                0x0b,0x23,0x4e,0xff,0x3c,0x54,0xa7,0x78,0xa4,0x89,0x33,0x6d,0xfb,0x79,0x27,0xc4,
                0xf9,0x40,0x41,0xdf,0xc5,0x82,0x93,0xdd,0xa6,0xef,0xcd,0x8d,0xa3,0xae,0x7a,0xb6,
                0x2f,0xfd,0xbd,0xe5,0x98,0x66,0xf3,0x4f,0x57,0x88,0x90,0x9c,0x0a,0x50,0xe7,0x15,
                0x7b,0x58,0xbc,0x07,0x68,0x3a,0x5f,0xee,0x32,0x9f,0xeb,0xcc,0x18,0x8b,0xe2,0x57,
                0xb7,0x49,0x37,0xde,0xf5,0x99,0x67,0x5b,0x3b,0xbb,0x3d,0xb5,0x2d,0x19,0x2e,0x0d,
                0x93,0xfc,0x7e,0x06,0x08,0xbe,0x3f,0xd9,0x2a,0x70,0x9a,0xc8,0x7d,0xd8,0x46,0x65,
                0x22,0xf4,0xb9,0xa2,0x6f,0x12,0x1b,0x14,0x45,0xc7,0x87,0x31,0x60,0x29,0xf7,0x73,
                0x2c,0x97,0x72,0xcd,0x89,0xa6,0x88,0x4c,0xe8,0x83,0xeb,0x59,0xca,0x50,0x3f,0x27,
                0x4e,0xae,0x43,0xd5,0x6e,0xd0,0x99,0x7b,0x7c,0x40,0x0c,0x52,0x86,0xc1,0x46,0x12,
                0x5a,0x28,0xa8,0xbb,0xcb,0xf0,0x11,0x95,0x26,0x0d,0x34,0x66,0x22,0x18,0x6f,0x51,
                0x9b,0x3b,0xda,0xec,0x5e,0x00,0x2a,0xf5,0x8f,0x61,0xba,0x96,0xb3,0xd1,0x30,0xdc,
                0x33,0x75,0xe9,0x6d,0xc8,0xa1,0x3a,0x3e,0x5f,0x9d,0xfd,0xa9,0x31,0x9f,0xaa,0x85,
                0x2f,0x92,0xaf,0x67,0x78,0xa5,0xab,0x03,0x21,0x4f,0xb9,0xad,0xfe,0xf3,0x42,0xfc,
                0x17,0xd7,0xee,0xa3,0xd8,0x80,0x14,0x2e,0xa0,0x47,0x55,0xc4,0xff,0xe5,0x13,0x3f,
                0x81,0xb6,0x7a,0x94,0xd0,0xb5,0x54,0xbf,0x91,0xa7,0x37,0xf1,0x6b,0xc9,0x1b,0xb1,
                0x3c,0xb6,0xd9,0x32,0x24,0x8d,0xf2,0x82,0xb4,0xf9,0xdb,0x7d,0x44,0xfb,0x1e,0xd4,
                0xea,0x5d,0x35,0x69,0x23,0x71,0x57,0x01,0x06,0xe4,0x55,0x9a,0xa4,0x58,0x56,0xc7,
                0x4a,0x8c,0x8a,0xd6,0x6a,0x49,0x70,0xc5,0x8e,0x0a,0x62,0xdc,0x29,0x4b,0x42,0x41,
                0xcb,0x2b,0xb7,0xce,0x08,0xa1,0x76,0x1d,0x1a,0xb8,0xe3,0xcc,0x7e,0x48,0x20,0xe6,
                0xf8,0x45,0x93,0xde,0xc3,0x63,0x0f,0xb0,0xac,0x5c,0xba,0xdf,0x07,0x77,0xe7,0x4e,
                0x1f,0x28,0x10,0x6c,0x59,0xd3,0xdd,0x2d,0x65,0x39,0xb2,0x74,0x84,0x3d,0xf4,0xbd,
                0xc7,0x79,0x60,0x0b,0x4d,0x33,0x36,0x25,0xbc,0xe0,0x09,0xcf,0x5b,0xe2,0x38,0x9e,
                0xc0,0xef,0xd2,0x16,0x05,0xbe,0x53,0xf7,0xc2,0xc6,0xa2,0x24,0x98,0x1c,0xad,0x04
        };
        int[] diffusion = new int[] {
                0xf26cb481,0x16a5dc92,0x3c5ba924,0x79b65248,0x2fc64b18,0x615acd29,0xc3b59a42,0x976b2584,
                0x6cf281b4,0xa51692dc,0x5b3c24a9,0xb6794852,0xc62f184b,0x5a6129cd,0xb5c3429a,0x6b978425,
                0xb481f26c,0xdc9216a5,0xa9243c5b,0x524879b6,0x4b182fc6,0xcd29615a,0x9a42c3b5,0x2584976b,
                0x81b46cf2,0x92dca516,0x24a95b3c,0x4852b679,0x184bc62f,0x29cd5a61,0x429ab5c3,0x84256b97
        };

        int[] input = new int[32];

        for (int i = 0; i < property.getBytes().length; i++) {
            input[i] = property.getBytes()[i];
        }

        int[] output = new int[32];
        for(int i = 0; i < 256; i++)  {
            for(int j = 0; j < 32; j++) {
                output[j] = confusion[input[j]];
                input[j]=0;
            }

            for(int j = 0; j < 32; j++) {
                for (int k = 0; k < 32; k++) {
                    input[j] ^= output[k] * ((diffusion[j] >> k) & 1);
                }
            }
        }
        for(int i = 0; i < 16; i++) {
            output[i] = confusion[input[i * 2]] ^ confusion[input[i * 2 + 1] + 256];
        }

        byte[] target = "Hire me!!!!!!!!".getBytes();

        for (int i = 0; i < 16; i++) {
            byte expected = target[i];
            byte got = (byte) output[i];
            if (expected != got) {
                return false;
            }
        }
        return true;
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        Set<String> existingCommands = getCommands(dispatcher);

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

        clientcommandsCommands.clear();
        for (String command : getCommands(dispatcher)) {
            if (!existingCommands.contains(command)) {
                clientcommandsCommands.add(command);
            }
        }
    }
}
