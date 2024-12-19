package net.earthcomputer.clientcommands.buildscript

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.BufferedReader
import java.io.InputStreamReader

abstract class CheckLanguageFilesTask : DefaultTask() {
    companion object {
        private val formatSpecifierRegex = "%((?<argIndex>\\d+)\\$)?[-#+ 0,(<]*\\d*(\\.\\d+)?[tT]?[a-zA-Z%]".toRegex()
        private val allowedFormatSpecifierRegex = "%(%|(\\d+\\$)?s)".toRegex()

        private val nonSpaceWhitespaceRegex = "[^\\S ]".toRegex()
    }

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @TaskAction
    fun run() {
        var errored = false

        val enUs = runCatching {
            Gson().fromJson(inputDir.file("en_us.json").get().asFile.reader(), JsonObject::class.java)
        }.getOrNull()

        inputDir.asFileTree.visit {
            if (name.endsWith(".json")) {
                val lines = open().use { BufferedReader(InputStreamReader(it)).readLines() }
                if (!checkJson(name, lines, enUs)) {
                    errored = true
                }
            }
        }

        if (errored) {
            throw IllegalStateException("There were language file check failures")
        }
    }

    private fun checkJson(filename: String, jsonLines: List<String>, enUs: JsonObject?): Boolean {
        if (jsonLines.firstOrNull() != "{") {
            logger.error("$filename:1: expected json object, found ${jsonLines.firstOrNull()}")
            return false
        }
        val closeBraceIndex = jsonLines.indexOf("}")
        if (closeBraceIndex == -1) {
            logger.error("$filename:${jsonLines.size}: unclosed json object")
            return false
        }

        var errored = false

        var isFirstValue = true
        var seenBlankLine = false
        var prevValueHasComma: Boolean? = false
        var prevValueLineNumber = -1
        var prevValueName: String? = null
        var lineNumber = 1

        for (line in jsonLines.subList(1, closeBraceIndex)) {
            lineNumber++

            if (line.isBlank()) {
                if (line.isNotEmpty()) {
                    logger.error("$filename:$lineNumber: unexpected trailing whitespace")
                    errored = true
                }
                if (seenBlankLine) {
                    logger.error("$filename:${lineNumber - 1}: found multiple blank lines")
                    errored = true
                } else if (isFirstValue) {
                    logger.error("$filename:$lineNumber: json file cannot start with blank line")
                    errored = true
                }
                seenBlankLine = true
                continue
            }

            var trimmedLine = line
            var trailingWhitespaceLen = 0
            while (trimmedLine.last().isWhitespace()) {
                trailingWhitespaceLen++
                trimmedLine = trimmedLine.substring(0, trimmedLine.length - 1)
            }

            if (trailingWhitespaceLen != 0) {
                logger.error("$filename:$lineNumber:${trimmedLine.length}: unexpected trailing whitespace")
                errored = true
            }

            val lineCheckResult = checkLine(filename, lineNumber, trimmedLine, enUs, isFirstValue, seenBlankLine, prevValueLineNumber, prevValueName, prevValueHasComma)
            errored = errored || lineCheckResult.errored

            isFirstValue = false
            seenBlankLine = false
            prevValueLineNumber = lineNumber
            prevValueName = lineCheckResult.name
            prevValueHasComma = lineCheckResult.hasComma
        }

        if (prevValueHasComma == true) {
            logger.error("$filename:$prevValueLineNumber: unexpected trailing comma")
            errored = true
        }

        if (seenBlankLine) {
            logger.error("$filename:${prevValueLineNumber + 1}: json file cannot end with blank line")
            errored = true
        }

        lineNumber = closeBraceIndex + 1
        for (line in jsonLines.subList(closeBraceIndex + 1, jsonLines.size)) {
            lineNumber++
            if (line.isNotEmpty()) {
                logger.error("$filename:$lineNumber: unexpected trailing data")
                return false
            }
        }

        return !errored
    }

    private fun checkLine(filename: String, lineNumber: Int, line: String, enUs: JsonObject?, isFirstValue: Boolean, seenBlankLine: Boolean, prevValueLineNumber: Int, prevValueName: String?, prevValueHasComma: Boolean?): LineCheckResult {
        var errored = false

        if (!isFirstValue && prevValueHasComma == false) {
            logger.error("$filename:$prevValueLineNumber: line does not end with a comma")
            errored = true
        }

        val indent = line.takeWhile(Char::isWhitespace)
        if (indent.any { it != ' ' }) {
            logger.error("$filename:$lineNumber: indent contains non-space characters")
            errored = true
        } else if (indent.length != 2) {
            logger.error("$filename:$lineNumber: expected indent of 2, found ${indent.length}")
            errored = true
        }

        val (name, nameEnd) = line.parseQuotedString(filename, lineNumber, indent.length) ?: return LineCheckResult(true, null, null)

        if (prevValueName != null) {
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            val cmp = (name as java.lang.String).compareToIgnoreCase(prevValueName)
            if (cmp < 0) {
                logger.error("$filename:$lineNumber: json is not in alphabetical order ('$name' should be before '$prevValueName')")
                errored = true
            } else if (cmp == 0) {
                logger.error("$filename:$lineNumber: json has duplicate keys '$name'")
                errored = true
            }

            val gapExpected = isGapExpected(prevValueName, name)
            if (gapExpected && !seenBlankLine) {
                logger.error("$filename:$lineNumber: expected gap between '$prevValueName' and '$name'")
                errored = true
            } else if (!gapExpected && seenBlankLine) {
                logger.error("$filename:${prevValueLineNumber + 1}: unexpected gap between '$prevValueName' and '$name'")
                errored = true
            }

            if (enUs != null && !enUs.has(name)) {
                logger.error("$filename:$lineNumber: key '$name' is not present in en_us.json")
            }
        }

        if (nameEnd >= line.length) {
            logger.error("$filename:$lineNumber:${line.length}: expected ':'")
            return LineCheckResult(true, name, null)
        }
        if (line[nameEnd] != ':') {
            logger.error("$filename:$lineNumber:$nameEnd: expected ':', found '${line[nameEnd]}'")
            return LineCheckResult(true, name, null)
        }
        if (nameEnd + 1 >= line.length) {
            logger.error("$filename:$lineNumber:${line.length}: expected ' '")
            return LineCheckResult(true, name, null)
        }
        if (line[nameEnd + 1] != ' ') {
            logger.error("$filename:$lineNumber:$nameEnd: expected ' ', found '${line[nameEnd]}'")
            return LineCheckResult(true, name, null)
        }

        val (value, valueEnd) = line.parseQuotedString(filename, lineNumber, nameEnd + 2) ?: return LineCheckResult(true, name, null)
        if (!validateValue(filename, lineNumber, name, value, enUs)) {
            errored = true
        }

        val hasComma = when (line.getOrNull(valueEnd)) {
            ',' -> {
                if (line.length > valueEnd + 1) {
                    logger.error("$filename:$lineNumber:${valueEnd + 1}: unexpected trailing data")
                    return LineCheckResult(true, name, null)
                }
                true
            }
            null -> false
            else -> {
                logger.error("$filename:$lineNumber:$valueEnd: unexpected trailing data")
                return LineCheckResult(true, name, null)
            }
        }

        return LineCheckResult(errored, name, hasComma)
    }

    private class LineCheckResult(val errored: Boolean, val name: String?, val hasComma: Boolean?)

    private fun String.parseQuotedString(filename: String, lineNumber: Int, start: Int): Pair<String, Int>? {
        if (start >= length) {
            logger.error("$filename:$lineNumber:$length: expected string")
            return null
        }
        if (this[start] != '"') {
            logger.error("$filename:$lineNumber:$start: expected string, found ${this[start]}")
            return null
        }

        val str = this
        var index = start + 1
        return buildString {
            while (true) {
                if (index >= str.length) {
                    logger.error("$filename:$lineNumber${str.length}: unclosed string")
                    return null
                }
                when (val c = str[index++]) {
                    '"' -> break
                    '\\' -> {
                        if (index >= str.length) {
                            logger.error("$filename:$lineNumber${str.length}: unclosed string")
                            return null
                        }
                        when (val c2 = str[index++]) {
                            '"', '\\' -> append(c2)
                            'n' -> append('\n')
                            't' -> append('\t')
                            'r' -> append('\r')
                            else -> {
                                logger.error("$filename:$lineNumber${index - 2}: invalid escape sequence '\\$c2'")
                                append(c2)
                            }
                        }
                    }
                    else -> append(c)
                }
            }
        } to index
    }

    private fun isGapExpected(first: String, second: String): Boolean {
        val parts1 = first.split('.')
        val parts2 = second.split('.')
        if (parts1[0] != parts2[0]) {
            return true
        }
        return parts1[0] == "commands" && parts1.getOrNull(1) != parts2.getOrNull(1)
    }

    private fun validateValue(filename: String, lineNumber: Int, key: String, value: String, enUs: JsonObject?): Boolean {
        var errored = false

        val englishValue = enUs?.get(key)?.takeIf { it is JsonPrimitive && it.isString }?.let(JsonElement::getAsString)

        if (!checkFormattingCodes(filename, lineNumber, key, value)) {
            errored = true
        }

        if (!checkZeroWidthSpace(filename, lineNumber, key, value)) {
            errored = true
        }

        if (!checkStrayWhitespace(filename, lineNumber, key, value)) {
            errored = true
        }

        if (!checkNotEndWithPeriod(filename, lineNumber, key, value)) {
            errored = true
        }

        if (!checkFormatSpecifiers(filename, lineNumber, key, value, englishValue)) {
            errored = true
        }

        return !errored
    }

    private fun checkFormattingCodes(filename: String, lineNumber: Int, key: String, value: String): Boolean {
        if (value.contains('§')) {
            logger.error("$filename:$lineNumber: translation '$key' contains legacy formatting code ('§'). Use formatting in code instead")
            return false
        }

        return true
    }

    private fun checkZeroWidthSpace(filename: String, lineNumber: Int, key: String, value: String): Boolean {
        if (value.contains('\u200b')) {
            logger.error("$filename:$lineNumber: translation '$key' contains zero width space")
            return false
        }

        return true
    }

    private fun checkStrayWhitespace(filename: String, lineNumber: Int, key: String, value: String): Boolean {
        var errored = false

        if (nonSpaceWhitespaceRegex.find(value) != null) {
            logger.error("$filename:$lineNumber: translation '$key' contains a whitespace character which isn't a space")
            errored = true
        }

        if (value.startsWith(' ')) {
            logger.error("$filename:$lineNumber: translation '$key' starts with a space")
            errored = true
        }

        if (value.endsWith(' ')) {
            logger.error("$filename:$lineNumber: translation '$key' ends with a space")
            errored = true
        }

        if (value.contains("  ")) {
            logger.error("$filename:$lineNumber: translation '$key' contains duplicate space")
            errored = true
        }

        return !errored
    }

    private fun checkNotEndWithPeriod(filename: String, lineNumber: Int, key: String, value: String): Boolean {
        // only check English, it's a mess otherwise with other languages' weird rules
        if (filename == "en_us.json" && value.endsWith('.') && !value.endsWith("...")) {
            logger.error("$filename:$lineNumber: translation '$key' ends with a period")
            return false
        }

        return true
    }

    private fun checkFormatSpecifiers(filename: String, lineNumber: Int, key: String, value: String, englishValue: String?): Boolean {
        var errored = false

        val formatSpecifiers = formatSpecifierRegex.findAll(value)
        val (legalFormatSpecifiers, illegalFormatSpecifiers) = formatSpecifiers.partition { allowedFormatSpecifierRegex.matches(it.value) }

        for (formatSpecifier in illegalFormatSpecifiers) {
            val replacement = "%${formatSpecifier.groups["argIndex"]?.let { "$it\$" } ?: ""}s"
            logger.error("$filename:$lineNumber: translation '$key' contains unsupported format specifier '${formatSpecifier.value}'. Try using '$replacement' instead")
            errored = true
        }

        // validate legal format specifiers
        if (filename == "en_us.json") {
            for (formatSpecifier in legalFormatSpecifiers) {
                if (formatSpecifier.groups["argIndex"] != null) {
                    logger.error("$filename:$lineNumber: translation key '$key' contains indexed format specifier '${formatSpecifier.value}', which is not allowed in en_us.json. Try using '%s' instead")
                    errored = true
                }
            }
        } else {
            val usedIndexes = mutableSetOf<Int>()
            val warnedDuplicates = mutableSetOf<Int>()

            var allowNonIndexed = true
            var allowIndexed = true
            var mixedIndexed = false
            var nextSpecifierIndex = 0
            for (formatSpecifier in legalFormatSpecifiers) {
                if (!formatSpecifier.value.endsWith('s')) {
                    continue
                }

                val explicitIndex = formatSpecifier.groups["argIndex"]?.value?.toIntOrNull()
                val index = if (explicitIndex != null) {
                    if (!allowIndexed) {
                        logger.error("$filename:$lineNumber: translation key '$key' mixes indexed and non-indexed format specifiers. Please use one or the other")
                        errored = true
                        mixedIndexed = true
                        break
                    }
                    allowNonIndexed = false
                    explicitIndex - 1
                } else {
                    if (!allowNonIndexed) {
                        logger.error("$filename:$lineNumber: translation key '$key' mixes indexed and non-indexed format specifiers. Please use one or the other")
                        errored = true
                        mixedIndexed = true
                        break
                    }
                    allowIndexed = false
                    nextSpecifierIndex++
                }

                if (!usedIndexes.add(index) && warnedDuplicates.add(index)) {
                    logger.error("$filename:$lineNumber: translation key '$key' specifies '${formatSpecifier.value}' multiple times")
                }
            }

            if (!mixedIndexed && englishValue != null) {
                val (englishLegalFormatSpecifiers, englishIllegalFormatSpecifiers) = formatSpecifierRegex.findAll(englishValue).partition { allowedFormatSpecifierRegex.matches(it.value) }
                if (englishIllegalFormatSpecifiers.isEmpty() && englishLegalFormatSpecifiers.all { it.groups["argIndex"] == null }) {
                    val numSpecifiers = englishLegalFormatSpecifiers.count { it.value.endsWith('s') }
                    if (allowNonIndexed) {
                        if (usedIndexes.size < numSpecifiers) {
                            logger.error("$filename:$lineNumber: translation key '$key' does not have enough format specifiers. It only has ${usedIndexes.size} while the English has $numSpecifiers")
                            errored = true
                        } else if (usedIndexes.size > numSpecifiers) {
                            logger.error("$filename:$lineNumber: translation key '$key' has extra format specifiers. It has ${usedIndexes.size} while the English only ha $numSpecifiers")
                            errored = true
                        }
                    } else {
                        for (i in 0 until numSpecifiers) {
                            if (i !in usedIndexes) {
                                logger.error("$filename:$lineNumber: translation key '$key' does not specify '%${i + 1}\$s' which is required because the English has $numSpecifiers format specifiers")
                                errored = true
                            }
                        }
                        for (i in usedIndexes) {
                            if (i >= numSpecifiers) {
                                logger.error("$filename:$lineNumber: translation key '$key' specifies '%${i + 1}\$s' which is out of bounds for $numSpecifiers format specifiers existing in the English")
                                errored = true
                            }
                        }
                    }
                }
            }
        }

        for ((index, char) in value.asSequence().withIndex()) {
            if (char == '%' && formatSpecifiers.none { index in it.range }) {
                logger.error("$filename:$lineNumber: translation key '$key' contains an unescaped '%'. Try escaping it with '%%'")
                errored = true
            }
        }

        return !errored
    }
}