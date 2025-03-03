package net.earthcomputer.clientcommands.buildscript;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// Based on https://github.com/Earthcomputer/clientcommands/blob/bcdc53c55676bad96e907ac82542e0c9f2e7f832/buildSrc/src/main/kotlin/net/earthcomputer/clientcommands/buildscript/GenerateBuildInfoTask.kt
public abstract class GenerateBuildInfoTask extends DefaultTask {

    private static final Gson GSON = new Gson();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Inject
    protected abstract ExecOperations getExecOperations();

    {
        // never reuse previous outputs
        this.getOutputs().upToDateWhen(task -> false);
    }

    @TaskAction
    protected void run() {
        String version = this.getProject().getVersion().toString();
        String branch = this.executeCommand("git", "branch", "--show-current");
        String shortCommitHash = this.executeCommand("git", "rev-parse", "--short", "HEAD");
        String commitHash = this.executeCommand("git", "rev-parse", "HEAD");

        JsonObject object = new JsonObject();
        object.addProperty("version", version);
        object.addProperty("branch", branch);
        object.addProperty("shortCommitHash", shortCommitHash);
        object.addProperty("commitHash", commitHash);

        try (BufferedWriter writer = Files.newBufferedWriter(this.getOutputFile().getAsFile().get().toPath())) {
            GSON.toJson(object, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String executeCommand(Object... args) {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        this.getExecOperations().exec(execSpec -> {
            execSpec.setStandardOutput(outputBytes);
            execSpec.commandLine(args);
        }).rethrowFailure();
        return outputBytes.toString(StandardCharsets.UTF_8).trim();
    }
}
