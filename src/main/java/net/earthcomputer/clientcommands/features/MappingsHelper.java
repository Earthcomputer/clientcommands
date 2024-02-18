package net.earthcomputer.clientcommands.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.command.ListenCommand;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

public class MappingsHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Path MAPPINGS_DIR = ClientCommands.configDir.resolve("mappings");

    private static final MemoryMappingTree mojmapOfficial = new MemoryMappingTree();
    private static final int SRC_OFFICIAL = 0;
    private static final int DEST_OFFICIAL = 0;

    private static final MemoryMappingTree officialIntermediaryNamed = new MemoryMappingTree();
    private static final int SRC_INTERMEDIARY = 0;
    private static final int DEST_INTERMEDIARY = 0;
    private static final int SRC_NAMED = 1;
    private static final int DEST_NAMED = 1;

    private static final boolean IS_DEV_ENV = FabricLoader.getInstance().isDevelopmentEnvironment();

    public static void initMappings(String version) {
        try {
            Files.createDirectories(MAPPINGS_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create mappings dir", e);
        }
        try (BufferedReader reader = Files.newBufferedReader(MAPPINGS_DIR.resolve(version + ".txt"))) {
            MappingReader.read(reader, MappingFormat.PROGUARD_FILE, mojmapOfficial);
        } catch (IOException e) {
            mojmapOfficial.reset();
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest versionsRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            httpClient.sendAsync(versionsRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(versionsBody -> {
                    JsonObject versionsJson = JsonParser.parseString(versionsBody).getAsJsonObject();
                    String versionUrl = versionsJson.getAsJsonArray("versions").asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .filter(v -> v.get("id").getAsString().equals(version))
                        .map(v -> v.get("url").getAsString())
                        .findAny().orElseThrow();

                    HttpRequest versionRequest = HttpRequest.newBuilder()
                        .uri(URI.create(versionUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();
                    httpClient.sendAsync(versionRequest, HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .thenAccept(versionBody -> {
                            JsonObject versionJson = JsonParser.parseString(versionBody).getAsJsonObject();
                            String mappingsUrl = versionJson
                                .getAsJsonObject("downloads")
                                .getAsJsonObject("client_mappings")
                                .get("url").getAsString();

                            HttpRequest mappingsRequest = HttpRequest.newBuilder()
                                .uri(URI.create(mappingsUrl))
                                .GET()
                                .timeout(Duration.ofSeconds(5))
                                .build();
                            httpClient.sendAsync(mappingsRequest, HttpResponse.BodyHandlers.ofString())
                                .thenApply(HttpResponse::body)
                                .thenAccept(body -> {
                                    try (StringReader reader = new StringReader(body)) {
                                        MappingReader.read(reader, MappingFormat.PROGUARD_FILE, mojmapOfficial);
                                    } catch (IOException ex) {
                                        LOGGER.error("Could not read ProGuard mappings file", ex);
                                        ListenCommand.isEnabled = false;
                                    }
                                    try (BufferedWriter writer = Files.newBufferedWriter(MAPPINGS_DIR.resolve(version + ".txt"), StandardOpenOption.CREATE)) {
                                        writer.write(body);
                                    } catch (IOException ex) {
                                        LOGGER.error("Could not write ProGuard mappings file", ex);
                                    }
                                });
                        });
                });
        }

        try (InputStream stream = FabricLoader.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny")) {
            if (stream == null) {
                throw new IOException("Could not find mappings.tiny");
            }
            MappingReader.read(new InputStreamReader(stream), IS_DEV_ENV ? MappingFormat.TINY_2_FILE : MappingFormat.TINY_FILE, officialIntermediaryNamed);
        } catch (IOException e) {
            LOGGER.error("Could not read mappings.tiny", e);
            ListenCommand.isEnabled = false;
        }
    }

    public static Collection<? extends MappingTree.ClassMapping> mojmapClasses() {
        return mojmapOfficial.getClasses();
    }

    public static Optional<String> mojmapToOfficial_class(String mojmapClass) {
        MappingTree.ClassMapping officialClass = mojmapOfficial.getClass(mojmapClass);
        if (officialClass == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(officialClass.getDstName(DEST_OFFICIAL));
    }

    public static Optional<String> officialToMojmap_class(String officialClass) {
        MappingTree.ClassMapping mojmapClass = mojmapOfficial.getClass(officialClass, SRC_OFFICIAL);
        if (mojmapClass == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mojmapClass.getSrcName());
    }

    public static Optional<String> mojmapToNamed_class(String mojmapClass) {
        Optional<String> officialClass = mojmapToOfficial_class(mojmapClass);
        if (officialClass.isEmpty()) {
            return Optional.empty();
        }
        MappingTree.ClassMapping namedClass = officialIntermediaryNamed.getClass(officialClass.get());
        if (namedClass == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(namedClass.getDstName(DEST_NAMED));
    }

    public static Optional<String> namedToMojmap_class(String namedClass) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(namedClass, SRC_NAMED);
        if (officialClass == null) {
            return Optional.empty();
        }
        MappingTree.ClassMapping mojmapClass = mojmapOfficial.getClass(officialClass.getSrcName(), SRC_OFFICIAL);
        if (mojmapClass == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mojmapClass.getSrcName());
    }

    public static Optional<String> mojmapToIntermediary_class(String mojmapClass) {
        Optional<String> officialClass = mojmapToOfficial_class(mojmapClass);
        if (officialClass.isEmpty()) {
            return Optional.empty();
        }
        MappingTree.ClassMapping intermediaryClass = officialIntermediaryNamed.getClass(officialClass.get());
        if (intermediaryClass == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(intermediaryClass.getDstName(DEST_INTERMEDIARY));
    }

    public static Optional<String> intermediaryToMojmap_class(String intermediaryClass) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(intermediaryClass, SRC_INTERMEDIARY);
        if (officialClass == null) {
            return Optional.empty();
        }
        MappingTree.ClassMapping mojmapClass = mojmapOfficial.getClass(officialClass.getSrcName(), SRC_OFFICIAL);
        if (mojmapClass == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mojmapClass.getSrcName());
    }

    public static Optional<String> namedOrIntermediaryToMojmap_class(String namedOrIntermediaryClass) {
        if (IS_DEV_ENV) {
            return MappingsHelper.namedToMojmap_class(namedOrIntermediaryClass);
        }
        return MappingsHelper.intermediaryToMojmap_class(namedOrIntermediaryClass);
    }

    public static Optional<String> mojmapToNamedOrIntermediary_class(String mojmapClass) {
        if (IS_DEV_ENV) {
            return MappingsHelper.mojmapToNamed_class(mojmapClass);
        }
        return MappingsHelper.mojmapToIntermediary_class(mojmapClass);
    }

    public static Optional<String> officialToMojmap_field(String officialClass, String officialField) {
        MappingTree.FieldMapping mojmapField = mojmapOfficial.getField(officialClass, officialField, null);
        if (mojmapField == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mojmapField.getSrcName());
    }

    public static Optional<String> namedToMojmap_field(String namedClass, String namedField) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(namedClass, SRC_NAMED);
        if (officialClass == null) {
            return Optional.empty();
        }
        MappingTree.FieldMapping officialField = officialIntermediaryNamed.getField(namedClass, namedField, null, SRC_NAMED);
        if (officialField == null) {
            return Optional.empty();
        }
        MappingTree.FieldMapping mojmapField = mojmapOfficial.getField(officialClass.getSrcName(), officialField.getSrcName(), null, SRC_OFFICIAL);
        if (mojmapField == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mojmapField.getSrcName());
    }

    public static Optional<String> intermediaryToMojmap_field(String intermediaryClass, String intermediaryField) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(intermediaryClass, SRC_INTERMEDIARY);
        if (officialClass == null) {
            return Optional.empty();
        }
        MappingTree.FieldMapping officialField = officialIntermediaryNamed.getField(intermediaryClass, intermediaryField, null, SRC_INTERMEDIARY);
        if (officialField == null) {
            return Optional.empty();
        }
        MappingTree.FieldMapping mojmapField = mojmapOfficial.getField(officialClass.getSrcName(), officialField.getSrcName(), null, SRC_OFFICIAL);
        if (mojmapField == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mojmapField.getSrcName());
    }

    public static Optional<String> namedOrIntermediaryToMojmap_field(String namedOrIntermediaryClass, String namedOrIntermediaryField) {
        if (IS_DEV_ENV) {
            return namedToMojmap_field(namedOrIntermediaryClass, namedOrIntermediaryField);
        }
        return intermediaryToMojmap_field(namedOrIntermediaryClass, namedOrIntermediaryField);
    }
}
