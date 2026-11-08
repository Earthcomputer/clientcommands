package net.earthcomputer.clientcommands.oldservergametest;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.ChorusManipulation;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedClass;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedField;
import net.earthcomputer.clientcommands.oldservergametest.ServerVersions.ServerVersion;
import net.earthcomputer.clientcommands.oldservergametest.mixin.ChatComponentAccessor;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public class OldServerClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONNECT_TIMEOUT = SharedConstants.TICKS_PER_MINUTE;
    private static final List<String> failedTests = new ArrayList<>();
    private static final List<Class<?>> TEST_CLASSES = List.of(
        CrackRngTests.class,
        EnchantmentCrackingTests.class,
        InfiniteToolsTests.class
    );

    private static final MappedClass ENTITY = MappedClass.of("net.minecraft.world.entity.Entity")
        .until(ProtocolVersion.v1_14_3, "net.minecraft.entity.Entity");
    private static final MappedField ENTITY_RANDOM = MappedField.of(ENTITY, "random")
        .until(ProtocolVersion.v1_14_3, "field_70146_Z");
    // the entity random is a LegacyRandomSource in 1.19+, and a java.util.Random before that
    private static final MappedClass LEGACY_RANDOM_SOURCE = MappedClass.of("net.minecraft.world.level.levelgen.LegacyRandomSource");
    private static final MappedField LEGACY_RANDOM_SOURCE_SEED = MappedField.of(LEGACY_RANDOM_SOURCE, "seed");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));

        List<Method> testMethods = findTestMethods();
        List<ServerVersion> versions = ServerVersions.getVersionsToTest();
        LOGGER.info("Testing against server versions {}", versions);

        failedTests.clear();
        for (ServerVersion version : versions) {
            try (OldServer server = OldServer.start(context, version)) {
                connect(context, server);
                try {
                    for (Method method : testMethods) {
                        invokeTestMethod(context, server, method);
                    }
                } finally {
                    disconnect(context);
                }
            } catch (Throwable e) {
                LOGGER.error("Failed to test against server {}", version, e);
                failedTests.add("server[" + version + "]");
                context.runOnClient(client -> {
                    if (client.level != null) {
                        client.disconnectWithSavingScreen();
                    }
                });
                context.waitFor(client -> client.level == null);
                context.setScreen(TitleScreen::new);
            }
        }

        if (!failedTests.isEmpty()) {
            throw new IllegalStateException("There were failing tests: " + failedTests);
        }
    }

    private static List<Method> findTestMethods() {
        List<Method> testMethods = new ArrayList<>();
        boolean seenOnlyMe = false;

        for (Method method : TEST_CLASSES.stream().flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods())).toList()) {
            if (method.isAnnotationPresent(TestCase.class)) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("Test methods must be static");
                }
                if (!Arrays.equals(method.getParameterTypes(), new Class<?>[]{ClientGameTestContext.class, OldServer.class})) {
                    throw new IllegalStateException("Test methods must take a ClientGameTestContext and an OldServer: " + method);
                }

                TestCase testCase = method.getAnnotation(TestCase.class);

                if (seenOnlyMe) {
                    if (!testCase.onlyMe()) {
                        continue;
                    }
                } else if (testCase.onlyMe()) {
                    seenOnlyMe = true;
                    testMethods.clear();
                }

                method.setAccessible(true);
                testMethods.add(method);
            }
        }

        return testMethods;
    }

    private static void invokeTestMethod(ClientGameTestContext context, OldServer server, Method method) {
        TestCase testCase = method.getAnnotation(TestCase.class);
        int protocolVersion = server.version().protocolVersion().getVersion();
        if (protocolVersion < testCase.minVersion() || protocolVersion > testCase.maxVersion()) {
            LOGGER.info("Skipping test {} on server {}", method.getName(), server.version());
            return;
        }

        try {
            method.invoke(null, context, server);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    static void runTest(ClientGameTestContext context, OldServer server, Runnable testRunner) {
        runTest(context, server, null, testRunner);
    }

    static void runTest(ClientGameTestContext context, OldServer server, @Nullable String variant, Runnable testRunner) {
        commonSetup(context, server);

        boolean testFailed = false;
        try {
            testRunner.run();
        } catch (Throwable e) {
            testFailed = true;
            LOGGER.error("Test failed", e);
        }

        context.waitTick();
        String testName = getCallingTest().getName() + "[" + server.version() + "]" + (variant == null ? "" : "(" + variant + ")");
        reportTestResult(context, testName, testFailed);
    }

    private static Method getCallingTest() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames ->
            frames.map(frame -> {
                Class<?> clazz = frame.getDeclaringClass();
                try {
                    return clazz.getDeclaredMethod(frame.getMethodName(), frame.getMethodType().parameterArray());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }).filter(method -> method.isAnnotationPresent(TestCase.class)).findFirst().orElseThrow()
        );
    }

    private static void commonSetup(ClientGameTestContext context, OldServer server) {
        context.runOnClient(client -> {
            client.gui.setScreen(null);
            for (String taskName : Lists.newArrayList(TaskManager.getTaskNames())) {
                TaskManager.removeTask(taskName);
            }
            Configs.playerRNGMaintenance = true;
            Configs.enchantingPrediction = true;
            Configs.chorusManipulation = false;
            Configs.infiniteTools = false;
            ChorusManipulation.chorusGoalFrom = null;
            ChorusManipulation.chorusGoalTo = null;
            Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
            EnchantmentCracker.resetCracker();
            EnchantmentCracker.enchantingTablePos = null;
        });

        fence(context);

        // remove items dropped by previous tests (e.g. thrown by ccrackrng), so that the player doesn't pick them up later.
        boolean oldEntityNames = server.version().protocolVersion().olderThan(ProtocolVersion.v1_11);
        server.runCommand("kill @e[type=" + (oldEntityNames ? "Item" : "item") + "]");
        // these commands have the same syntax in every version
        server.runCommand("gamemode survival " + server.playerName());
        server.runCommand("clear " + server.playerName());
        context.waitFor(client -> Objects.requireNonNull(client.gameMode).getPlayerMode() == GameType.SURVIVAL && Objects.requireNonNull(client.player).getInventory().isEmpty());
    }

    private static void connect(ClientGameTestContext context, OldServer server) {
        context.runOnClient(client -> {
            ViaFabricPlus.api().setTargetVersion(server.version().protocolVersion());
            ServerData serverData = new ServerData("Old server " + server.version(), server.address(), ServerData.Type.OTHER);
            ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(server.address()), serverData, false, null);
        });
        context.waitFor(client -> {
            if (client.gui.screen() instanceof DisconnectedScreen) {
                throw new AssertionError("Failed to connect to " + server.version() + " server");
            }
            return client.level != null && client.player != null && !(client.gui.screen() instanceof LevelLoadingScreen);
        }, CONNECT_TIMEOUT);
        context.waitFor(client -> Objects.requireNonNull(client.level).hasChunkAt(Objects.requireNonNull(client.player).blockPosition()));

        // op the player to turn off chat spam protection
        server.runCommand("op " + server.playerName());
    }

    private static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.level == null) {
                throw new AssertionError("Disconnected from server before the tests finished");
            }
            client.level.disconnect(Component.literal("Disconnecting"));
            client.disconnectWithSavingScreen();
        });
        context.waitFor(client -> client.level == null);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
    }

    private static void reportTestResult(ClientGameTestContext context, String testName, boolean testFailed) {
        if (testFailed) {
            failedTests.add(testName);
            context.runOnClient(client -> client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test failed: " + testName).withStyle(ChatFormatting.RED)));
        } else {
            context.runOnClient(client -> client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test passed: " + testName).withStyle(ChatFormatting.GREEN)));
        }
    }

    // waits for a client-server-client packet round trip, so that the server has processed everything sent before
    static void fence(ClientGameTestContext context) {
        String chatMessage = "fence: " + UUID.randomUUID();
        context.runOnClient(client -> Objects.requireNonNull(client.player).connection.sendChat(chatMessage));
        context.waitFor(client -> getChatMessages(client).anyMatch(message -> message.getString().contains(chatMessage)));
    }

    static void runClientCommand(ClientGameTestContext context, String command) {
        context.runOnClient(client -> Objects.requireNonNull(client.player).connection.sendCommand(command));
    }

    static Stream<Component> getChatMessages(Minecraft client) {
        return ((ChatComponentAccessor) client.gui.hud.getChat()).getAllMessages().stream().map(GuiMessage::content);
    }

    static long getServerPlayerSeed(OldServer server) {
        ServerDebugger debugger = server.debugger();
        ObjectReference player = server.serverPlayer();
        return debugger.computeAtNextTick(() -> {
            ObjectReference random = (ObjectReference) debugger.getField(player, ENTITY_RANDOM);
            ObjectReference seed = random.referenceType().name().equals("java.util.Random")
                ? (ObjectReference) ServerDebugger.getUnmappedField(random, "seed")
                : (ObjectReference) debugger.getField(random, LEGACY_RANDOM_SOURCE_SEED);
            return ((LongValue) ServerDebugger.getUnmappedField(seed, "value")).value();
        });
    }

    // gives the player items for player RNG manipulation to throw
    static void giveItemsToThrow(ClientGameTestContext context, OldServer server) {
        server.runCommand("give " + server.playerName() + " minecraft:cobblestone 64");
        context.waitFor(client -> Objects.requireNonNull(client.player).getInventory().countItem(Items.COBBLESTONE) == 64);
    }

    static void assertPlayerCracked(ClientGameTestContext context, String after) {
        PlayerRandCracker.CrackState crackState = context.computeOnClient(_ -> Configs.playerCrackState);
        assertTrue(crackState == PlayerRandCracker.CrackState.CRACKED, () -> "Expected player crack state CRACKED after " + after + ", got " + crackState);
    }

    static void assertPlayerSeedMatchesServer(ClientGameTestContext context, OldServer server) {
        long serverSeed = getServerPlayerSeed(server);
        long clientSeed = context.computeOnClient(_ -> PlayerRandCracker.getSeed());
        assertTrue(serverSeed == clientSeed, () -> "Client player seed doesn't match the server: server=0x" + Long.toHexString(serverSeed) + ", client=0x" + Long.toHexString(clientSeed));
    }

    static void assertTrue(boolean condition, Supplier<String> message) {
        if (!condition) {
            throw new AssertionError(message.get());
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TestCase {
        boolean onlyMe() default false;

        int minVersion() default Integer.MIN_VALUE;

        int maxVersion() default Integer.MAX_VALUE;
    }
}
