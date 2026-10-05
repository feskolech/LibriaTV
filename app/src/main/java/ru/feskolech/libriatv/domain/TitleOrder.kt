package ru.feskolech.libriatv.domain

import java.text.Collator
import java.util.Locale

fun Release.titleLetter(): String {
    val first = title.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return when (first) {
        in 'А'..'Я', 'Ё', in 'A'..'Z' -> first.toString()
        else -> "#"
    }
}

fun List<Release>.sortedByTitle(): List<Release> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru"))
    fun rank(release: Release): Int = when (release.titleLetter().first()) {
        in 'А'..'Я', 'Ё' -> 0
        in 'A'..'Z' -> 1
        else -> 2
    }
    return sortedWith { a, b ->
        val group = rank(a).compareTo(rank(b))
        if (group != 0) group else collator.compare(a.title, b.title)
    }
}
