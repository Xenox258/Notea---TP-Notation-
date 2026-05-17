package com.teob.appnotationmobile

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object XlsxGridParser {
    // Les grilles EP et ETLV n'ont pas la même structure; on détecte la feuille disponible.
    fun parse(bytes: ByteArray): GridImport {
        val entries = readZipEntries(ByteArrayInputStream(bytes))
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"] ?: ByteArray(0))
        val sheets = workbookSheets(entries)
        val grilleSheet = entries[sheets["Grille"] ?: "xl/worksheets/sheet2.xml"]
        if (grilleSheet == null) return parseEtlv(entries, sharedStrings, sheets)
        val descriptors = entries[sheets["Descripteurs"] ?: "xl/worksheets/sheet3.xml"]
            ?.let { parseDescriptors(it, sharedStrings) }
            .orEmpty()
        val cells = parseCells(grilleSheet, sharedStrings)
        val criteria = (8..21).mapNotNull { row ->
            val label = cells["D$row"]?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            val skill = cells["B$row"]?.trim().takeUnless { it.isNullOrBlank() }
                ?: when (row) {
                    in 8..9 -> "Analyser"
                    in 10..13 -> "Concevoir"
                    in 14..17 -> "Simuler"
                    else -> "Experimenter"
                }
            Criterion(
                id = "criterion-$row",
                skill = skill,
                label = label,
                weight = cells["M$row"]?.toDoubleOrNull() ?: 1.0,
                descriptors = descriptors[normalizeHeader(label)] ?: descriptors["criterion-$row"].orEmpty(),
            )
        }
        return GridImport(criteria, GridKind.EP_2I2D)
    }

    private fun parseEtlv(
        entries: Map<String, ByteArray>,
        sharedStrings: List<String>,
        sheets: Map<String, String>,
    ): GridImport {
        val sheet = entries[sheets["Candidat 1"] ?: "xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("Feuille de notation introuvable")
        val cells = parseCells(sheet, sharedStrings)
        val criteria = (1..80).mapNotNull { row ->
            val label = cells["A$row"]?.trim().orEmpty()
            val weight = cells["J$row"]?.toDoubleOrNull() ?: return@mapNotNull null
            if (!label.contains(" - ") || weight <= 0.0) return@mapNotNull null
            val code = label.substringBefore(" - ").trim()
            Criterion(
                id = "criterion-$row",
                skill = code,
                label = label,
                weight = weight,
            )
        }
        if (criteria.isEmpty()) throw IllegalArgumentException("Aucun critère reconnu")
        return GridImport(criteria, GridKind.ETLV)
    }

    private fun workbookSheets(entries: Map<String, ByteArray>): Map<String, String> {
        val workbook = entries["xl/workbook.xml"] ?: return emptyMap()
        val relationships = entries["xl/_rels/workbook.xml.rels"] ?: return emptyMap()
        val targetsById = workbookRelationships(relationships)
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(workbook), null)
        val sheets = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name").orEmpty()
                val id = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id").orEmpty()
                val target = targetsById[id].orEmpty()
                if (name.isNotBlank() && target.isNotBlank()) {
                    sheets[name] = if (target.startsWith("xl/")) target else "xl/$target"
                }
            }
            event = parser.next()
        }
        return sheets
    }

    private fun workbookRelationships(bytes: ByteArray): Map<String, String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val relationships = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id").orEmpty()
                val target = parser.getAttributeValue(null, "Target").orEmpty()
                if (id.isNotBlank() && target.isNotBlank()) relationships[id] = target
            }
            event = parser.next()
        }
        return relationships
    }

    // Les descripteurs sont indexés par libellé normalisé et par ligne pour rester robustes aux petites variations.
    private fun parseDescriptors(bytes: ByteArray, sharedStrings: List<String>): Map<String, Map<Int, String>> {
        val cells = parseCells(bytes, sharedStrings)
        val result = mutableMapOf<String, Map<Int, String>>()
        (1..80).forEach { row ->
            val label = cells["C$row"]?.trim().orEmpty()
            if (label.isBlank() || normalizeHeader(label).startsWith("criteres d'evaluation")) return@forEach
            val levels = mapOf(
                0 to cells["D$row"].orEmpty().trim(),
                1 to cells["E$row"].orEmpty().trim(),
                2 to cells["F$row"].orEmpty().trim(),
                3 to cells["G$row"].orEmpty().trim(),
            ).filterValues { it.isNotBlank() }
            if (levels.isNotEmpty()) {
                result[normalizeHeader(label)] = levels
                result["criterion-${row + 3}"] = levels
            }
        }
        return result
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = ByteArrayOutputStream()
                zip.copyTo(output)
                entries[entry.name] = output.toByteArray()
            }
        }
        return entries
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val strings = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "si") {
                strings += readSharedString(parser)
            }
            event = parser.next()
        }
        return strings
    }

    private fun readSharedString(parser: XmlPullParser): String {
        val builder = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") builder.append(parser.nextText())
                }

                XmlPullParser.END_TAG -> if (parser.name == "si") return builder.toString()
            }
        }
    }

    private fun parseCells(bytes: ByteArray, sharedStrings: List<String>): Map<String, String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val cells = mutableMapOf<String, String>()
        var ref = ""
        var type = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "c") {
                ref = parser.getAttributeValue(null, "r").orEmpty()
                type = parser.getAttributeValue(null, "t").orEmpty()
            }
            if (event == XmlPullParser.START_TAG && parser.name == "v" && ref.isNotBlank()) {
                val raw = parser.nextText()
                cells[ref] = if (type == "s") sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
            }
            event = parser.next()
        }
        return cells
    }
}
