package io.genai.php.sdk

enum class OsFamily { WINDOWS, MAC, LINUX }

enum class ArchiveKind { ZIP, TAR_GZ, RAW_BINARY }

/** A single downloadable portable PHP build. */
data class PhpRelease(
    val version: String,
    val os: OsFamily,
    val arch: String,
    val url: String,
    val kind: ArchiveKind,
) {
    val label: String get() = "PHP $version  ·  ${os.name.lowercase()}/$arch"
    override fun toString(): String = label
}
