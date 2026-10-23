package app.mongle

import app.mongle.l10n.AppLocale
import app.mongle.l10n.En
import app.mongle.l10n.L10n
import app.mongle.model.Catalog
import app.mongle.model.SpeciesCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

/** No Hangul may reach the English UI: every catalog name/description and every EN table value. */
class EnglishCatalogTest {
    private val hangul = Regex("[\\uAC00-\\uD7A3\\u1100-\\u11FF\\u3130-\\u318F]")
    // Language names shown in their own script (e.g. "한국어" in the language picker) are intentional.
    private val ownScript = setOf("lang_ko", "language_ko", "lang_korean", "date_ko") // date_ko: Korean-only date format, used only when lang == ko

    @Test fun everyCatalogItemNameAndBlurbIsEnglish() {
        val prev = AppLocale.preference
        AppLocale.preference = "en"
        try {
            val bad = Catalog.all.flatMap { d -> listOf(d.id to L10n.item(d.id), d.id to L10n.itemBlurb(d.id)) }
                .filter { hangul.containsMatchIn(it.second) } +
                SpeciesCatalog.all.flatMap { s -> listOf(s.id to L10n.species(s.id), s.id to L10n.speciesBlurb(s.id)) }
                    .filter { hangul.containsMatchIn(it.second) }
            assertTrue(bad.isEmpty(), "Korean in EN catalog: $bad")
        } finally { AppLocale.preference = prev }
    }

    @Test fun englishTableHasNoHangul() {
        val bad = En.filter { (k, v) -> k !in ownScript && hangul.containsMatchIn(v) }
        assertTrue(bad.isEmpty(), "Korean in EN strings: ${bad.keys}")
    }
}
