package art.yniyniyni.cliptic.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Per-app language selection backed by the platform [LocaleManager] (API 33+, minSdk is 34).
 * Setting a locale here persists it and recreates the activity so the new language applies
 * immediately. The selectable list mirrors the shipped `values-*` resource folders; each entry
 * is shown in its own script (autonym), which is intentionally not translated.
 */
object AppLanguages {
    /** Empty tag = follow the system default. */
    const val SYSTEM_DEFAULT = ""

    /** BCP-47 tags, in the order shown in the picker. Must match the `values-*` folders. */
    val tags: List<String> = listOf(
        SYSTEM_DEFAULT,
        "en-US",
        "ar",
        "de",
        "es",
        "fr",
        "hi",
        "ja",
        "pt-BR",
        "ru",
        "zh-CN",
    )

    /** Display name shown for each tag, in its own language. */
    val autonyms: Map<String, String> = mapOf(
        "en-US" to "English",
        "ar" to "العربية",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "hi" to "हिन्दी",
        "ja" to "日本語",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "zh-CN" to "中文（简体）",
    )

    private fun localeManager(context: Context): LocaleManager =
        context.getSystemService(LocaleManager::class.java)

    /** Currently applied app locale tag, or [SYSTEM_DEFAULT] when none is forced. */
    fun current(context: Context): String {
        val locales = localeManager(context).applicationLocales
        return if (locales.isEmpty) SYSTEM_DEFAULT else locales[0].toLanguageTag()
    }

    fun set(context: Context, tag: String) {
        localeManager(context).applicationLocales =
            if (tag == SYSTEM_DEFAULT) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
}
