// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.update

/**
 * Comparable semantic version, tolerant of what real tags look like.
 *
 * Accepts `v0.2.0`, `0.2`, `0.2.0-debug` and stray whitespace. A build suffix
 * after `-` is ignored for ordering: the debug variant appends `-debug` to
 * `versionName`, and a debug 0.2.0 must not read as older than a release 0.2.0
 * or the app would offer to "update" to the version it already runs.
 */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val mine = parts.getOrElse(index) { 0 }
            val theirs = other.parts.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /** Returns null for anything that has no leading numeric component. */
        fun parse(raw: String?): AppVersion? {
            if (raw.isNullOrBlank()) return null
            val core = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            val parts = core.split('.').map { segment ->
                segment.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            }
            return if (parts.isEmpty()) null else AppVersion(parts)
        }
    }
}

/**
 * True when [candidate] is a strictly newer release than [installed]. Anything
 * unparseable answers false: refusing to guess is better than nagging about an
 * update that may not exist.
 */
fun isNewerVersion(candidate: String?, installed: String?): Boolean {
    val remote = AppVersion.parse(candidate) ?: return false
    val local = AppVersion.parse(installed) ?: return false
    return remote > local
}
