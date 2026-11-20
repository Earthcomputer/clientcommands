package net.earthcomputer.clientcommands.oldservergametest;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.earthcomputer.clientcommands.oldservergametest.ServerVersions.ServerVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class WriteTestMatrix {
    private static final int VERSIONS_PER_JOB = 5;

    private WriteTestMatrix() {
    }

    static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: WriteTestMatrix <output file>");
        }

        List<ServerVersion> versionsToTest = ServerVersions.getVersionsToTest();
        String versionFilter = System.getProperty("clientcommands.oldServerTest.versions");
        boolean explicitVersions = versionFilter != null && !versionFilter.isBlank();

        JsonArray matrix = new JsonArray();
        if (explicitVersions) {
            // test every version that was asked for
            for (ServerVersion version : versionsToTest) {
                matrix.add(job(version.id(), version));
            }
        } else {
            // testing every version takes a long time, so each job tests one random version from its range instead
            for (List<ServerVersion> versions : Lists.partition(versionsToTest, VERSIONS_PER_JOB)) {
                ServerVersion version = versions.get(ThreadLocalRandom.current().nextInt(versions.size()));
                // e.g. "1.10.2 from 1.8.9-1.12.2", used in the job name
                matrix.add(job(version.id() + " from " + versions.getFirst().id() + "-" + versions.getLast().id(), version));
            }
        }

        Path outputFile = Path.of(args[0]);
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        Files.writeString(outputFile, new Gson().toJson(matrix));
    }

    private static JsonObject job(String name, ServerVersion version) {
        JsonObject job = new JsonObject();
        job.addProperty("name", name);
        job.addProperty("version", version.id());
        return job;
    }
}
