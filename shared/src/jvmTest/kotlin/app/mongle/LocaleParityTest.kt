package app.mongle

import app.mongle.l10n.AppLocale
import app.mongle.l10n.L10n
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class LocaleParityTest {
    @Test
    fun catalogsShareIdenticalKeysAndValues() {
        val sharedEn = parseXml(file("src/commonMain/resources/l10n/values/strings.xml"))
        val sharedKo = parseXml(file("src/commonMain/resources/l10n/values-ko/strings.xml"))
        assertEquals(sharedEn.keys, sharedKo.keys)
        assertEquals(sharedEn, L10n.table("en"))
        assertEquals(sharedKo, L10n.table("ko"))

        val composeEn = parseXml(file("../composeApp/src/commonMain/composeResources/values/strings.xml"))
        val composeKo = parseXml(file("../composeApp/src/commonMain/composeResources/values-ko/strings.xml"))
        assertEquals(sharedEn, composeEn)
        assertEquals(sharedKo, composeKo)

        val androidEn = parseXml(file("../composeApp/src/androidMain/res/values/strings.xml"))
        val androidKo = parseXml(file("../composeApp/src/androidMain/res/values-ko/strings.xml"))
        assertEquals(androidEn.keys, androidKo.keys)
        androidEn.forEach { (key, value) -> assertEquals(sharedEn[key], value, key) }
        androidKo.forEach { (key, value) -> assertEquals(sharedKo[key], value, key) }

        val iosEn = parseApple(file("../iosApp/PetWidget/en.lproj/Localizable.strings"))
        val iosKo = parseApple(file("../iosApp/PetWidget/ko.lproj/Localizable.strings"))
        assertEquals(iosEn.keys, iosKo.keys)
        assertEquals(androidEn.keys, iosEn.keys)
        iosEn.forEach { (key, value) -> assertEquals(sharedEn[key], value, key) }
        iosKo.forEach { (key, value) -> assertEquals(sharedKo[key], value, key) }

        val appEn = parseApple(file("../iosApp/iosApp/en.lproj/Localizable.strings"))
        val appKo = parseApple(file("../iosApp/iosApp/ko.lproj/Localizable.strings"))
        assertEquals(iosEn, appEn)
        assertEquals(iosKo, appKo)
    }

    @Test
    fun languagePreferenceChangesNamesAndDates() {
        AppLocale.preference = "en"
        assertEquals("Dingrow", L10n.t("app_name"))
        assertEquals("Mongi", L10n.t("pet_default_name"))
        assertEquals("Mongle", L10n.species("base"))
        val date = LocalDate(2026, 10, 21)
        assertTrue(L10n.formatDate(date).contains("Oct"))
        assertTrue(L10n.clock(0L, kotlinx.datetime.TimeZone.UTC).contains("AM") || L10n.clock(0L, kotlinx.datetime.TimeZone.UTC).contains("PM"))

        AppLocale.preference = "ko"
        assertEquals("딩그로", L10n.t("app_name"))
        assertEquals("몽이", L10n.t("pet_default_name"))
        assertEquals("몽글", L10n.species("base"))
        assertTrue(L10n.formatDate(date).contains("10월"))
        assertEquals("보리가 불러요", L10n.calling("보리"))
        AppLocale.preference = "en"
        assertEquals("보리 is calling", L10n.calling("보리"))
        AppLocale.preference = "system"
    }

    private fun file(path: String): File = File(path)

    private fun parseXml(source: File): Map<String, String> {
        val pattern = Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
        return pattern.findAll(source.readText()).associate { match ->
            match.groupValues[1] to unescapeXml(match.groupValues[2])
        }
    }

    private fun parseApple(source: File): Map<String, String> {
        val pattern = Regex("""^"([^"]+)" = "(.*)";$""", RegexOption.MULTILINE)
        return pattern.findAll(source.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        }
    }

    private fun unescapeXml(raw: String): String = raw
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
