package com.teob.appnotationmobile

import java.io.InputStream
import java.util.Locale

object CsvStudentParser {
    fun parse(input: InputStream): List<Student> {
        val bytes = input.readBytes()
        val utf8 = bytes.toString(Charsets.UTF_8)
        val content = if (utf8.contains('\uFFFD')) bytes.toString(Charsets.ISO_8859_1) else utf8
        return content
            .lineSequence()
            .mapNotNull { rawLine -> extractName(rawLine) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .mapIndexed { index, name -> Student("student-$index-${name.lowercase(Locale.ROOT).hashCode()}", name) }
            .toList()
    }

    private fun extractName(rawLine: String): String? {
        val line = rawLine.trim().trimStart('\uFEFF')
        if (line.isBlank()) return null
        val separator = detectSeparator(line)
        val columns = splitCsvLine(line, separator).map { cleanStudentName(it) }
        val lowerColumns = columns.map { normalizeHeader(it) }
        if (lowerColumns.any { it == "nom" || it == "prenom" || it == "name" || it.startsWith("sp") }) return null
        val textColumns = columns.filter { candidate ->
            candidate.any { it.isLetter() } && !candidate.contains("@")
        }
        val name = textColumns.firstOrNull { it.contains(" ") } ?: textColumns.take(2).joinToString(" ")
        return name.takeIf { it.isNotBlank() }
    }

    private fun detectSeparator(line: String): Char {
        return listOf(';', ',', '\t')
            .maxByOrNull { separator -> line.count { it == separator } }
            ?.takeIf { line.contains(it) }
            ?: ';'
    }

    private fun splitCsvLine(line: String, separator: Char): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == separator && !quoted -> {
                    columns += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        columns += current.toString()
        return columns
    }
}
