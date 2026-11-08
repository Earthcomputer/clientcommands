package net.earthcomputer.clientcommands.oldservergametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ServerVersions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final URI VERSION_MANIFEST_URL = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final ProtocolVersion OLDEST_VERSION = ProtocolVersion.v1_8;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private ServerVersions() {
    }

    static Path getCacheDir() {
        String cacheDir = System.getProperty("clientcommands.oldServerTest.cacheDir");
        if (cacheDir == null) {
            throw new IllegalStateException("clientcommands.oldServerTest.cacheDir is not set");
        }
        return Path.of(cacheDir);
    }

    static List<ServerVersion> getVersionsToTest() {
        ProtocolVersion currentVersion = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion());

        JsonObject manifest = JsonParser.parseString(new String(download(VERSION_MANIFEST_URL), StandardCharsets.UTF_8)).getAsJsonObject();
        // the manifest lists the newest versions first, so the first version we see for each protocol is the newest one
        Map<ProtocolVersion, ServerVersion> versionsByProtocol = new LinkedHashMap<>();
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject version = element.getAsJsonObject();
            if (!"release".equals(version.get("type").getAsString())) {
                continue;
            }
            String id = version.get("id").getAsString();
            ProtocolVersion protocolVersion = ProtocolVersion.getClosest(id);
            if (protocolVersion == null) {
                throw new IllegalStateException("Unknown protocol version for release " + id + ", is ViaFabricPlus up to date?");
            }
            if (protocolVersion.olderThan(OLDEST_VERSION) || protocolVersion.newerThanOrEqualTo(currentVersion)) {
                continue;
            }
            versionsByProtocol.putIfAbsent(protocolVersion, new ServerVersion(
                id,
                protocolVersion,
                URI.create(version.get("url").getAsString()),
                version.get("sha1").getAsString()
            ));
        }

        List<ServerVersion> versions = new ArrayList<>(versionsByProtocol.values());
        versions.sort(Comparator.comparing(ServerVersion::protocolVersion));

        String versionFilter = System.getProperty("clientcommands.oldServerTest.versions");
        if (versionFilter != null && !versionFilter.isBlank()) {
            Set<String> filter = Arrays.stream(versionFilter.split(",")).map(String::strip).collect(Collectors.toSet());
            versions.removeIf(version -> !filter.contains(version.id()));
            Set<String> knownIds = versions.stream().map(ServerVersion::id).collect(Collectors.toSet());
            for (String id : filter) {
                if (!knownIds.contains(id)) {
                    throw new IllegalArgumentException("Version " + id + " is not one of the versions tested");
                }
            }
        }

        return versions;
    }

    static byte[] download(URI url) {
        try {
            HttpResponse<byte[]> response = HTTP_CLIENT.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("Got status code " + response.statusCode() + " downloading " + url);
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static Path downloadCached(URI url, String sha1, Path path) {
        try {
            if (Files.exists(path) && sha1(Files.readAllBytes(path)).equals(sha1)) {
                return path;
            }

            LOGGER.info("Downloading {}", url);
            byte[] bytes = download(url);
            String actualSha1 = sha1(bytes);
            if (!actualSha1.equals(sha1)) {
                throw new IOException("Hash mismatch downloading " + url + ": expected " + sha1 + ", got " + actualSha1);
            }

            Files.createDirectories(path.getParent());
            Path tempFile = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            Files.write(tempFile, bytes);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    record ServerVersion(String id, ProtocolVersion protocolVersion, URI metadataUrl, String metadataSha1) {
        ServerDownload download() {
            Path versionDir = getCacheDir().resolve("versions").resolve(id);
            JsonObject metadata;
            try (InputStream in = Files.newInputStream(downloadCached(metadataUrl, metadataSha1, versionDir.resolve(id + ".json")))) {
                metadata = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            JsonObject serverDownload = metadata.getAsJsonObject("downloads").getAsJsonObject("server");
            Path serverJar = downloadCached(
                URI.create(serverDownload.get("url").getAsString()),
                serverDownload.get("sha1").getAsString(),
                versionDir.resolve("server.jar")
            );

            Log4ShellFix log4ShellFix = Log4ShellFix.forVersion(protocolVersion);
            Path log4jConfig = log4ShellFix == null ? null : log4ShellFix.downloadConfig();

            ServerMappings mappings = ServerMappings.forVersion(this, metadata, versionDir);

            return new ServerDownload(serverJar, log4ShellFix, log4jConfig, mappings);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    record ServerDownload(Path serverJar, @Nullable Log4ShellFix log4ShellFix, @Nullable Path log4jConfig, ServerMappings mappings) {
    }

    // The fixes recommended by Mojang in https://www.minecraft.net/en-us/article/important-message--security-vulnerability-java-edition
    enum Log4ShellFix {
        CONFIG_1_7_TO_1_11("https://launcher.mojang.com/v1/objects/dd2b723346a8dcd48e7f4d245f6bf09e98db9696/log4j2_17-111.xml", "dd2b723346a8dcd48e7f4d245f6bf09e98db9696", "log4j2_17-111.xml"),
        CONFIG_1_12_TO_1_16("https://launcher.mojang.com/v1/objects/02937d122c86ce73319ef9975b58896fc1b491d1/log4j2_112-116.xml", "02937d122c86ce73319ef9975b58896fc1b491d1", "log4j2_112-116.xml"),
        FORMAT_MSG_NO_LOOKUPS(null, null, null),
        ;

        private final @Nullable URI configUrl;
        private final @Nullable String configSha1;
        final @Nullable String configFileName;

        Log4ShellFix(@Nullable String configUrl, @Nullable String configSha1, @Nullable String configFileName) {
            this.configUrl = configUrl == null ? null : URI.create(configUrl);
            this.configSha1 = configSha1;
            this.configFileName = configFileName;
        }

        static @Nullable Log4ShellFix forVersion(ProtocolVersion version) {
            if (version.olderThanOrEqualTo(ProtocolVersion.v1_11_1)) {
                return CONFIG_1_7_TO_1_11;
            } else if (version.olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {
                return CONFIG_1_12_TO_1_16;
            } else if (version.olderThanOrEqualTo(ProtocolVersion.v1_17_1)) {
                return FORMAT_MSG_NO_LOOKUPS;
            } else {
                // 1.18 shares its protocol version with 1.18.1, which has the fix, so 1.18 itself is never tested
                return null;
            }
        }

        private @Nullable Path downloadConfig() {
            if (configUrl == null || configSha1 == null || configFileName == null) {
                return null;
            }
            return downloadCached(configUrl, configSha1, getCacheDir().resolve("log4j").resolve(configFileName));
        }

        List<String> jvmArgs() {
            return configFileName != null
                ? List.of("-Dlog4j.configurationFile=" + configFileName)
                : List.of("-Dlog4j2.formatMsgNoLookups=true");
        }
    }
}
