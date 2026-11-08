package com.prism.music

/**
 * The Veloura build shows its own name where Prism's interface text says "Prism", in every language: the word on its
 * own ("Signing you in to Prism…", "Prism's", "Prism Connect"), never inside a file name, link or identifier
 * ("Prism-1.8.apk", "github.com/Yigder/Prism", "prism.music"). Prism's license and its credit stay as written: Veloura
 * is built on Prism, and the GPL requires keeping that notice ([KEEP]).
 *
 * The build applies the same rule to the few labels Android reads from the resources itself (widgets, the tile), in
 * app/build.gradle.kts (SplitTranslations' `brand`); keep the two patterns the same.
 */
object VelouraBranding {
    const val NAME = "Veloura"

    /** "Prism" as a word: not after a letter, digit, slash, @, dot or dash, nor before one (or before a dot and a letter). */
    private val WORD = Regex("(?<![\\w/@.-])Prism(?![\\w/-]|\\.\\w)")

    /** Strings that keep "Prism": its license and the credit to it. */
    val KEEP = setOf("prism_is_free_software_under_the", "prism_s_license")

    fun apply(text: String): String = if ("Prism" in text) WORD.replace(text, NAME) else text
}
