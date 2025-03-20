package net.earthcomputer.clientcommands.buildscript

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject

abstract class GenerateBuildInfoTask : DefaultTask() {

    companion object {
        private val GSON = Gson()
    }

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    init {
        // never reuse previous outputs
        this.outputs.upToDateWhen { false }
    }

    @TaskAction
    protected fun run() {
        val version = this.project.version.toString()
        val branch = this.executeCommand("git", "branch", "--show-current")
        val shortCommitHash = this.executeCommand("git", "rev-parse", "--short", "HEAD")
        val commitHash = this.executeCommand("git", "rev-parse", "HEAD")

        val jsonObject = JsonObject().apply {
            addProperty("version", version)
            addProperty("branch", branch)
            addProperty("shortCommitHash", shortCommitHash)
            addProperty("commitHash", commitHash)
        }

        outputFile.asFile.get().writer().use {
            GSON.toJson(jsonObject, it)
        }
    }

    private fun executeCommand(vararg args: Any): String {
        val outputBytes = ByteArrayOutputStream()
        this.execOperations.exec {
            standardOutput = outputBytes
            commandLine(*args)
        }.rethrowFailure()
        return outputBytes.toString(StandardCharsets.UTF_8).trim()
    }
}
