package com.teob.appnotationmobile

import java.text.Normalizer
import java.util.Locale

fun normalizeHeader(raw: String): String {
    return Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}

fun cleanStudentName(raw: String): String {
    return raw
        .trim()
        .trim('"')
        .trim()
        .replace(Regex("\\s+"), " ")
        .trim(';', ',', '\t', ' ')
}
