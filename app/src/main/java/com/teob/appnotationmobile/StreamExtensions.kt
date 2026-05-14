package com.teob.appnotationmobile

import java.io.InputStream
import java.io.OutputStream

inline fun <T> InputStream?.useRequired(block: (InputStream) -> T): T {
    val stream = this ?: throw IllegalArgumentException("fichier illisible")
    return stream.use(block)
}

inline fun <T> OutputStream?.useRequired(block: (OutputStream) -> T): T {
    val stream = this ?: throw IllegalArgumentException("fichier illisible")
    return stream.use(block)
}
