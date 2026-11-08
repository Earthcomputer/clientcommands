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
import java.util.stream.Collectors;

final class WriteTestMatrix {
    private static final int VERSIONS_PER_JOB = 5;

    private WriteTestMatrix() {
    }

    static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: WriteTestMatrix <output file>");
        }

        JsonArray matrix = new JsonArray();
        for (List<ServerVersion> versions : Lists.partition(ServerVersions.getVersionsToTest(), VERSIONS_PER_JOB)) {
            JsonObject job = new JsonObject();
            // e.g. "1.8.9-1.12.2", used in the job name and the cache key, which can't contain commas
            job.addProperty("name", versions.getFirst().id() + "-" + versions.getLast().id());
            job.addProperty("versions", versions.stream().map(ServerVersion::id).collect(Collectors.joining(",")));
            matrix.add(job);
        }

        Path outputFile = Path.of(args[0]);
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        Files.writeString(outputFile, new Gson().toJson(matrix));
    }
}
