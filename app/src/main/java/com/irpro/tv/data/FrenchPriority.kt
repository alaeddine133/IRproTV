package com.irpro.tv.data

import java.text.Normalizer

/** Repère le contenu français à partir des noms donnés par les fournisseurs (FR, France, VF, VOSTFR, TNT…). */
object FrenchPriority {
    private val patterns = listOf(
        Regex("""(^|[^A-Z0-9])FR([^A-Z0-9]|$)"""),     // "FR | Sport", "[FR] Ciné", "FR: TNT", "Sport FR"
        Regex("""(^|[^A-Z0-9])FRA([^A-Z0-9]|$)"""),
        Regex("""FRANCE|FRANCAIS|FRANCAISE|FRENCH"""),
        Regex("""(^|[^A-Z0-9])VF([^A-Z0-9]|$)"""),
        Regex("""(^|[^A-Z0-9])VFF([^A-Z0-9]|$)"""),
        Regex("""(^|[^A-Z0-9])VOSTFR([^A-Z0-9]|$)"""),
        Regex("""(^|[^A-Z0-9])TNT([^A-Z0-9]|$)"""),
    )

    private fun clean(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").uppercase()

    fun isFrench(name: String): Boolean {
        val n = clean(name)
        return patterns.any { it.containsMatchIn(n) }
    }

    fun sortCategories(list: List<Category>): List<Category> {
        val (fr, other) = list.partition { it.isFrench }
        return fr + other
    }
}
