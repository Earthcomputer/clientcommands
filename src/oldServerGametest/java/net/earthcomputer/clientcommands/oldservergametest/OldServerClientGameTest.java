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
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
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
import java.util.function.Supplier;

@SuppressWarnings("UnstableApiUsage")
public class OldServerClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONNECT_TIMEOUT = SharedConstants.TICKS_PER_MINUTE;
    private static final List<Class<?>> TEST_CLASSES = List.of(
        CrackRngTests.class,
        EnchantmentCrackingTests.class
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

        List<String> failedTests = new ArrayList<>();
        for (ServerVersion version : versions) {
            try (OldServer server = OldServer.start(context, version)) {
                connect(context, server);
                try {
                    for (Method method : testMethods) {
                        String testName = method.getName() + "[" + version + "]";
                        if (!runTest(context, server, method)) {
                            failedTests.add(testName);
                        }
                        reportTestResult(context, testName, failedTests.contains(testName));
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

    private static boolean runTest(ClientGameTestContext context, OldServer server, Method method) {
        try {
            commonSetup(context, server);
            method.invoke(null, context, server);
            return true;
        } catch (InvocationTargetException e) {
            LOGGER.error("Test failed", e.getCause());
            return false;
        } catch (Throwable e) {
            LOGGER.error("Test failed", e);
            return false;
        } finally {
            context.waitTick();
        }
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
            ChorusManipulation.chorusGoalFrom = null;
            ChorusManipulation.chorusGoalTo = null;
            Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
            EnchantmentCracker.resetCracker();
            EnchantmentCracker.enchantingTablePos = null;
        });
        clearChat(context);

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
            context.runOnClient(client -> client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test failed: " + testName).withStyle(ChatFormatting.RED)));
        } else {
            context.runOnClient(client -> client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test passed: " + testName).withStyle(ChatFormatting.GREEN)));
        }
    }

    static void runClientCommand(ClientGameTestContext context, String command) {
        context.runOnClient(client -> Objects.requireNonNull(client.player).connection.sendCommand(command));
    }

    static void clearChat(ClientGameTestContext context) {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
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

    static void assertTrue(boolean condition, Supplier<String> message) {
        if (!condition) {
            throw new AssertionError(message.get());
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TestCase {
        boolean onlyMe() default false;
    }
}
