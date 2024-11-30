package net.earthcomputer.clientcommands.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public final class TranslationsTest {
    private static final Gson GSON = new Gson();

    @TestFactory
    public Iterable<DynamicTest> testTranslate() throws IOException {
        Path langDir = FabricLoader.getInstance().getModContainer("clientcommands").orElseThrow()
            .findPath("assets/clientcommands/lang").orElseThrow();

        JsonObject enUs;
        try (BufferedReader reader = Files.newBufferedReader(langDir.resolve("en_us.json"))) {
            enUs = GSON.fromJson(reader, JsonObject.class);
        }

        List<DynamicTest> tests = new ArrayList<>();

        try (Stream<Path> langFiles = Files.list(langDir)) {
            for (Path langFile : (Iterable<Path>) langFiles::iterator) {
                tests.add(DynamicTest.dynamicTest("Check " + langFile.getFileName().toString(), () -> checkLangFile(enUs, langFile)));
            }
        }

        return tests;
    }

    private static void checkLangFile(JsonObject enUs, Path langFile) throws IOException {
        String langFileName = langFile.getFileName().toString();

        JsonObject lang;
        try (BufferedReader reader = Files.newBufferedReader(langFile)) {
            lang = GSON.fromJson(reader, JsonObject.class);
        }

        for (String key : lang.keySet()) {
            assertTrue(enUs.has(key), () -> langFileName + " has key " + key + " not present in en_us.json");
        }
        for (var entry : lang.entrySet()) {
            assertTrue(entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString(), () -> langFileName + " has a non-string value associated with " + entry.getKey());
        }
        for (var entry : lang.entrySet()) {
            assertTrue(checkPercentS(entry.getValue().getAsString()), () -> langFileName + " has invalid formatting codes in " + entry.getKey() + ". Only %s, %n$s and %% are allowed");
        }
    }

    private static final Pattern NON_PERCENT_S_REGEX = Pattern.compile("%(?!(?:\\d+\\$)?s)");

    private static boolean checkPercentS(String value) {
        return !NON_PERCENT_S_REGEX.matcher(value.replace("%%", "")).find();
    }
}
