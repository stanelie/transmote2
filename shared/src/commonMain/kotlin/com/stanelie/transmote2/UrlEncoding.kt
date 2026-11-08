package com.stanelie.transmote2

/**
 * Percent-encodes one path segment exactly as the previous Android code did
 * with `URLEncoder.encode(s, "UTF-8").replace("+", "%20")`.
 *
 * URLEncoder leaves A-Z a-z 0-9 and ". - * _" alone and turns a space into
 * '+', which that call site then rewrote to %20 - so here a space simply
 * falls through to %20. Kept byte-for-byte rather than switching to Ktor's
 * encodeURLPathPart, which preserves a different set of sub-delimiters and
 * would quietly change the download URLs this builds.
 */
fun encodeUrlPathSegment(segment: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.-*_"
    val sb = StringBuilder()
    for (byte in segment.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (value < 0x80 && char in unreserved) {
            sb.append(char)
        } else {
            sb.append('%').append(value.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return sb.toString()
}
