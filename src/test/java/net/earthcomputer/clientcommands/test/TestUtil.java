package net.earthcomputer.clientcommands.test;

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class TestUtil {
    private static final Path REGRESSION_TESTS_DIR = Path.of(System.getProperty("clientcommands.regressionTestDir"));

    private TestUtil() {
    }

    public static void regressionTest(String name, String value) {
        if (!System.lineSeparator().equals("\n")) {
            value = value.replace(System.lineSeparator(), "\n");
        }

        Path file = REGRESSION_TESTS_DIR.resolve(name + ".regressiontest");
        String expectedValue;
        try {
            expectedValue = Files.readString(file);
        } catch (NoSuchFileException e) {
            try {
                Files.writeString(file, value);
            } catch (IOException e1) {
                throw new AssertionError("Failed to write regression test file", e1);
            }
            return;
        } catch (IOException e) {
            throw new AssertionError("Failed to read regression test file", e);
        }

        if (!System.lineSeparator().equals("\n")) {
            expectedValue = expectedValue.replace(System.lineSeparator(), "\n");
        }
        Assertions.assertEquals(expectedValue, value);
    }

    public static void regressionTest(String name, Consumer<PrintWriter> test) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        test.accept(pw);
        pw.flush();
        regressionTest(name, sw.toString());
    }
}
