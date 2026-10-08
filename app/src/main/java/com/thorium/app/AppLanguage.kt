package com.thorium.app

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.thorium.app.ui.main.LanguageChoice
import com.thorium.app.ui.main.LanguageController

/**
 * Per-app language through the system's [LocaleManager] (Android 13+). The system remembers the
 * choice, restarts the app's activities with the new language, and lists it in Android's own
 * per-app language settings, so Thorium keeps no copy of it.
 */
class AppLanguage(context: Context) : LanguageController {

    private val manager = context.getSystemService(LocaleManager::class.java)

    override fun current(): LanguageChoice {
        val locales = manager.applicationLocales
        if (locales.isEmpty) return LanguageChoice.System
        return when (locales[0].language) {
            "es" -> LanguageChoice.Spanish
            "en" -> LanguageChoice.English
            else -> LanguageChoice.System
        }
    }

    override fun set(choice: LanguageChoice) {
        manager.applicationLocales = when (choice) {
            LanguageChoice.System -> LocaleList.getEmptyLocaleList()
            LanguageChoice.English -> LocaleList.forLanguageTags("en")
            LanguageChoice.Spanish -> LocaleList.forLanguageTags("es")
        }
    }
}
