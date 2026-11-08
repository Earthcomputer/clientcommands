package net.earthcomputer.clientcommands.oldservergametest;

import com.mojang.logging.LogUtils;
import com.sun.jdi.ObjectReference;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.earthcomputer.clientcommands.oldservergametest.ServerMappings.MappedClass;
import net.earthcomputer.clientcommands.oldservergametest.ServerVersions.ServerDownload;
import net.earthcomputer.clientcommands.oldservergametest.ServerVersions.ServerVersion;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import org.apache.commons.io.file.PathUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A vanilla server of an old version running in a separate process.
 */
@SuppressWarnings("UnstableApiUsage")
final class OldServer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int STARTUP_TIMEOUT = 5 * SharedConstants.TICKS_PER_MINUTE;

    private static final MappedClass SERVER_PLAYER = MappedClass.of("net.minecraft.server.level.ServerPlayer")
        .until(ProtocolVersion.v1_14_3, "net.minecraft.entity.player.ServerPlayerEntity")
        .until(ProtocolVersion.v1_13_2, "net.minecraft.entity.player.EntityPlayerMP");

    private final ServerVersion version;
    private final String playerName;
    private final int port;
    private final Process process;
    private final Writer stdin;
    private final Thread shutdownHook;
    private volatile boolean started = false;
    private @Nullable ServerDebugger debugger;
    private @Nullable CompletableFuture<ObjectReference> serverPlayer;

    private OldServer(ServerVersion version, String playerName, int port, Process process) {
        this.version = version;
        this.playerName = playerName;
        this.port = port;
        this.process = process;
        this.stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        // don't leave servers running if the client exits unexpectedly
        this.shutdownHook = new Thread(process::destroyForcibly, "Old server shutdown hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        Thread outputThread = new Thread(this::readOutput, "Old server " + version + " output");
        outputThread.setDaemon(true);
        outputThread.start();
    }

    static OldServer start(ClientGameTestContext context, ServerVersion version) {
        ServerDownload download = version.download();
        String playerName = context.computeOnClient(client -> client.getUser().getName());

        Path serverDir = context.computeOnClient(client -> client.gameDirectory.toPath()).resolve("servers").resolve(version.id());
        int port;
        int debugPort;
        Process process;
        try {
            // start each server with a fresh world so that the tests are consistent
            if (Files.exists(serverDir)) {
                PathUtils.delete(serverDir);
            }
            Files.createDirectories(serverDir);

            // keep both sockets open until both ports are chosen, so that the OS doesn't choose the same port twice
            try (ServerSocket gameSocket = new ServerSocket(0); ServerSocket debugSocket = new ServerSocket(0)) {
                port = gameSocket.getLocalPort();
                debugPort = debugSocket.getLocalPort();
            }

            Files.writeString(serverDir.resolve("eula.txt"), "eula=true\n");
            Files.writeString(serverDir.resolve("server.properties"), String.join("\n",
                // the client isn't logged in to a real account
                "online-mode=false",
                // a free port, so that tests don't fail because something else is using the default port
                "server-port=" + port,
                // superflat, so that the tests don't depend on the terrain
                "level-type=flat",
                // no villages, whose villagers throw items that the player picks up, interfering with the tests
                "generate-structures=false",
                // the epoll transport in old versions of Netty doesn't work on the modern Java versions we run them on
                "use-native-transport=false",
                // sync chunk writes slow world saving down a lot on unix systems
                "sync-chunk-writes=" + (Util.getPlatform() == Util.OS.WINDOWS),
                // allow the player to place blocks at spawn without being opped
                "spawn-protection=0",
                // stop other players from joining the server and interfering with the tests
                "max-players=1",
                ""
            ));

            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-Xmx1G");
            // lets the tests inspect the server's memory
            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:" + debugPort);
            if (download.log4ShellFix() != null) {
                if (download.log4jConfig() != null) {
                    Files.copy(download.log4jConfig(), serverDir.resolve(download.log4jConfig().getFileName()));
                }
                command.addAll(download.log4ShellFix().jvmArgs());
            }
            command.add("-jar");
            command.add(download.serverJar().toAbsolutePath().toString());
            command.add("nogui");

            LOGGER.info("Starting {} server: {}", version, command);
            process = new ProcessBuilder(command)
                .directory(serverDir.toFile())
                .redirectErrorStream(true)
                .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        OldServer server = new OldServer(version, playerName, port, process);
        try {
            context.waitFor(_ -> server.started || !process.isAlive(), STARTUP_TIMEOUT);
            if (!server.started) {
                throw new AssertionError("Server " + version + " exited with code " + process.exitValue() + " before it started");
            }

            boolean snakeCaseGameRules = version.protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_11);
            server.runCommand("gamerule " + (snakeCaseGameRules ? "spawn_mobs" : "doMobSpawning") + " false");
            server.runCommand("gamerule " + (snakeCaseGameRules ? "send_command_feedback" : "sendCommandFeedback") + " false");
            server.runCommand("gamerule " + (snakeCaseGameRules ? "log_admin_commands" : "logAdminCommands") + " false");

            server.debugger = ServerDebugger.attach(debugPort, download.mappings());
            // capture the player when it joins
            server.serverPlayer = server.debugger.captureNextInstance(SERVER_PLAYER);
        } catch (Throwable e) {
            server.close();
            throw e;
        }
        return server;
    }

    private void readOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info("[{} server] {}", version, line);
                // e.g. [12:34:56] [Server thread/INFO]: Done (1.234s)! For help, type "help"
                if (!started && line.contains("]: Done (")) {
                    started = true;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error reading output of {} server", version, e);
        }
    }

    ServerVersion version() {
        return version;
    }

    String playerName() {
        return playerName;
    }

    String address() {
        return "127.0.0.1:" + port;
    }

    ServerDebugger debugger() {
        return Objects.requireNonNull(debugger);
    }

    /**
     * Returns the player on the server. This is captured when the player joins, so if the server replaces the player
     * object (e.g. when respawning in old versions), this will be the old player.
     */
    ObjectReference serverPlayer() {
        try {
            return Objects.requireNonNull(serverPlayer).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            debugger().logCaptureDiagnostics(SERVER_PLAYER);
            throw new AssertionError("Failed to get the server player", e);
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("Failed to get the server player", e);
        }
    }

    /**
     * Runs a command in the server console. Commands are run in the order they're sent, but this method doesn't wait
     * for the command to be run.
     */
    void runCommand(String command) {
        try {
            stdin.write(command + "\n");
            stdin.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (debugger != null) {
            debugger.close();
        }
        try {
            if (process.isAlive()) {
                runCommand("stop");
            }
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                LOGGER.warn("{} server didn't stop in time, killing it", version);
                process.destroyForcibly().waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } catch (UncheckedIOException e) {
            // the process has exited between checking and sending the command
            process.destroyForcibly();
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }
}
