package com.empathie.energyaccounting.update

/**
 * A strictly numeric `major.minor.patch` version, as published in release tags.
 *
 * Pre-release suffixes, build metadata and partial versions are deliberately rejected: the
 * updater only ever compares full stable releases, so a tag it cannot understand is treated as
 * "unknown" rather than guessed at.
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        /** Parses `v0.4.0` or `0.4.0`; returns `null` for anything else. */
        fun parse(raw: String?): SemanticVersion? {
            val match = PATTERN.matchEntire(raw ?: return null) ?: return null
            val (major, minor, patch) = match.destructured
            return SemanticVersion(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
            )
        }
    }
}
