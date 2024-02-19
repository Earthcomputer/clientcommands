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
import net.minecraft.DetectedVersion;
import net.minecraft.Optionull;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MappingsHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Path MAPPINGS_DIR = ClientCommands.configDir.resolve("mappings");

    private static final boolean IS_DEV_ENV = FabricLoader.getInstance().isDevelopmentEnvironment();

    private static final CompletableFuture<MemoryMappingTree> mojmapOfficial = Util.make(() -> {
        String version = DetectedVersion.BUILT_IN.getName();
        try (BufferedReader reader = Files.newBufferedReader(MAPPINGS_DIR.resolve(version + ".txt"))) {
            MemoryMappingTree tree = new MemoryMappingTree();
            MappingReader.read(reader, MappingFormat.PROGUARD_FILE, tree);
            return CompletableFuture.completedFuture(tree);
        } catch (IOException e) {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest versionsRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            return httpClient.sendAsync(versionsRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenCompose(versionsBody -> {
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
                    return httpClient.sendAsync(versionRequest, HttpResponse.BodyHandlers.ofString());
                })
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        ListenCommand.isEnabled = false;
                    }
                })
                .thenApply(HttpResponse::body)
                .thenCompose(versionBody -> {
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
                    return httpClient.sendAsync(mappingsRequest, HttpResponse.BodyHandlers.ofString());
                })
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    try (StringReader reader = new StringReader(body)) {
                        MemoryMappingTree tree = new MemoryMappingTree();
                        MappingReader.read(reader, MappingFormat.PROGUARD_FILE, tree);
                        return tree;
                    } catch (IOException ex) {
                        LOGGER.error("Could not read ProGuard mappings file", ex);
                        ListenCommand.isEnabled = false;
                        throw new UncheckedIOException(ex);
                    } finally {
                        try (BufferedWriter writer = Files.newBufferedWriter(MAPPINGS_DIR.resolve(version + ".txt"), StandardOpenOption.CREATE)) {
                            writer.write(body);
                        } catch (IOException ex) {
                            LOGGER.error("Could not write ProGuard mappings file", ex);
                        }
                    }
                });
        }
    });
    private static final int SRC_OFFICIAL = 0;
    private static final int DEST_OFFICIAL = 0;

    private static final MemoryMappingTree officialIntermediaryNamed = Util.make(() -> {
        try (InputStream stream = FabricLoader.class.getClassLoader().getResourceAsStream("mappings/mappings.tiny")) {
            if (stream == null) {
                throw new IOException("Could not find mappings.tiny");
            }
            MemoryMappingTree tree = new MemoryMappingTree();
            MappingReader.read(new InputStreamReader(stream), IS_DEV_ENV ? MappingFormat.TINY_2_FILE : MappingFormat.TINY_FILE, tree);
            return tree;
        } catch (IOException e) {
            LOGGER.error("Could not read mappings.tiny", e);
            ListenCommand.isEnabled = false;
            return null;
        }
    });
    private static final int SRC_INTERMEDIARY = 0;
    private static final int DEST_INTERMEDIARY = 0;
    private static final int SRC_NAMED = 1;
    private static final int DEST_NAMED = 1;

    public static void createMappingsDir() {
        try {
            Files.createDirectories(MAPPINGS_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create mappings dir", e);
        }
    }

    public static @Nullable Collection<? extends MappingTree.ClassMapping> mojmapClasses() {
        return Optionull.map(getMojmapOfficial(), MemoryMappingTree::getClasses);
    }

    public static @Nullable String mojmapToOfficial_class(String mojmapClass) {
        MappingTree.ClassMapping officialClass = Optionull.map(getMojmapOfficial(), tree -> tree.getClass(mojmapClass));
        if (officialClass == null) {
            return null;
        }
        return officialClass.getDstName(DEST_OFFICIAL);
    }

    public static @Nullable String officialToMojmap_class(String officialClass) {
        MappingTree.ClassMapping mojmapClass = Optionull.map(getMojmapOfficial(), tree -> tree.getClass(officialClass, SRC_OFFICIAL));
        if (mojmapClass == null) {
            return null;
        }
        return mojmapClass.getSrcName();
    }

    public static @Nullable String mojmapToNamed_class(String mojmapClass) {
        String officialClass = mojmapToOfficial_class(mojmapClass);
        if (officialClass == null) {
            return null;
        }
        MappingTree.ClassMapping namedClass = officialIntermediaryNamed.getClass(officialClass);
        if (namedClass == null) {
            return null;
        }
        return namedClass.getDstName(DEST_NAMED);
    }

    public static @Nullable String namedToMojmap_class(String namedClass) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(namedClass, SRC_NAMED);
        if (officialClass == null) {
            return null;
        }
        MappingTree.ClassMapping mojmapClass = Optionull.map(getMojmapOfficial(), tree -> tree.getClass(officialClass.getSrcName(), SRC_OFFICIAL));
        if (mojmapClass == null) {
            return null;
        }
        return mojmapClass.getSrcName();
    }

    public static @Nullable String mojmapToIntermediary_class(String mojmapClass) {
        String officialClass = mojmapToOfficial_class(mojmapClass);
        if (officialClass == null) {
            return null;
        }
        MappingTree.ClassMapping intermediaryClass = officialIntermediaryNamed.getClass(officialClass);
        if (intermediaryClass == null) {
            return null;
        }
        return intermediaryClass.getDstName(DEST_INTERMEDIARY);
    }

    public static @Nullable String intermediaryToMojmap_class(String intermediaryClass) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(intermediaryClass, SRC_INTERMEDIARY);
        if (officialClass == null) {
            return null;
        }
        MappingTree.ClassMapping mojmapClass = Optionull.map(getMojmapOfficial(), tree -> tree.getClass(officialClass.getSrcName(), SRC_OFFICIAL));
        if (mojmapClass == null) {
            return null;
        }
        return mojmapClass.getSrcName();
    }

    public static @Nullable String namedOrIntermediaryToMojmap_class(String namedOrIntermediaryClass) {
        if (IS_DEV_ENV) {
            return MappingsHelper.namedToMojmap_class(namedOrIntermediaryClass);
        }
        return MappingsHelper.intermediaryToMojmap_class(namedOrIntermediaryClass);
    }

    public static @Nullable String mojmapToNamedOrIntermediary_class(String mojmapClass) {
        if (IS_DEV_ENV) {
            return MappingsHelper.mojmapToNamed_class(mojmapClass);
        }
        return MappingsHelper.mojmapToIntermediary_class(mojmapClass);
    }

    public static @Nullable String officialToMojmap_field(String officialClass, String officialField) {
        MappingTree.FieldMapping mojmapField = Optionull.map(getMojmapOfficial(), tree -> tree.getField(officialClass, officialField, null));
        if (mojmapField == null) {
            return null;
        }
        return mojmapField.getSrcName();
    }

    public static @Nullable String namedToMojmap_field(String namedClass, String namedField) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(namedClass, SRC_NAMED);
        if (officialClass == null) {
            return null;
        }
        MappingTree.FieldMapping officialField = officialIntermediaryNamed.getField(namedClass, namedField, null, SRC_NAMED);
        if (officialField == null) {
            return null;
        }
        MappingTree.FieldMapping mojmapField = Optionull.map(getMojmapOfficial(), tree -> tree.getField(officialClass.getSrcName(), officialField.getSrcName(), null, SRC_OFFICIAL));
        if (mojmapField == null) {
            return null;
        }
        return mojmapField.getSrcName();
    }

    public static @Nullable String intermediaryToMojmap_field(String intermediaryClass, String intermediaryField) {
        MappingTree.ClassMapping officialClass = officialIntermediaryNamed.getClass(intermediaryClass, SRC_INTERMEDIARY);
        if (officialClass == null) {
            return null;
        }
        MappingTree.FieldMapping officialField = officialIntermediaryNamed.getField(intermediaryClass, intermediaryField, null, SRC_INTERMEDIARY);
        if (officialField == null) {
            return null;
        }
        MappingTree.FieldMapping mojmapField = Optionull.map(getMojmapOfficial(), tree -> tree.getField(officialClass.getSrcName(), officialField.getSrcName(), null, SRC_OFFICIAL));
        if (mojmapField == null) {
            return null;
        }
        return mojmapField.getSrcName();
    }

    public static @Nullable String namedOrIntermediaryToMojmap_field(String namedOrIntermediaryClass, String namedOrIntermediaryField) {
        if (IS_DEV_ENV) {
            return namedToMojmap_field(namedOrIntermediaryClass, namedOrIntermediaryField);
        }
        return intermediaryToMojmap_field(namedOrIntermediaryClass, namedOrIntermediaryField);
    }

    private static MemoryMappingTree getMojmapOfficial() {
        try {
            return mojmapOfficial.get();
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.error("mojmap mappings were not available", e);
            ListenCommand.isEnabled = false;
            return null;
        }
    }
}
