package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.strings
import app.luxion.shogunai.ui.theme.ShogunAiTheme

class Appearance(
    private val themeRepository: ThemePreferenceRepository,
    private val languageRepository: LanguagePreferenceRepository,
) {
    var themeMode by mutableStateOf(themeRepository.load())
        private set
    var language by mutableStateOf(languageRepository.load())
        private set

    val strings: Strings get() = language.strings()

    fun changeThemeMode(mode: ThemeMode) {
        themeMode = mode
        themeRepository.save(mode)
    }

    fun changeLanguage(selected: AppLanguage) {
        language = selected
        languageRepository.save(selected)
    }
}

@Composable
fun AppearanceScope(appearance: Appearance, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides appearance.strings) {
        ShogunAiTheme(appearance.themeMode, content)
    }
}
