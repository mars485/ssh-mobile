package dev.sshmobile.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.sshmobile.data.repository.AppLanguage
import java.util.Locale

/**
 * Applies the selected UI language to the whole Compose tree.
 *
 * A configuration context is used instead of forcing an Activity recreation, so switching the
 * language in Settings updates every `stringResource` immediately and still works on API 29-32.
 */
@Composable
fun AppLocalization(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val locale = when (language) {
        AppLanguage.SYSTEM -> Locale.getDefault()
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.RUSSIAN -> Locale.forLanguageTag("ru")
    }
    val localizedContext = remember(language, baseContext) {
        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        baseContext.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content,
    )
}
