package dev.sshmobile.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import dev.sshmobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizationResourcesTest {

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    @Test
    fun `english and russian resources resolve`() {
        val en = context(Locale.ENGLISH)
        val ru = context(Locale.forLanguageTag("ru"))

        assertEquals("Settings", en.getString(R.string.settings_title))
        assertEquals("Настройки", ru.getString(R.string.settings_title))

        assertEquals("Cancel", en.getString(R.string.action_cancel))
        assertEquals("Отмена", ru.getString(R.string.action_cancel))

        assertTrue(ru.getString(R.string.error_auth_failed).contains("аутентификации"))
        assertTrue(en.getString(R.string.terminal_unknown_host_warning).contains("fingerprint"))
    }

    @Test
    fun `russian plurals use correct forms`() {
        val ru = context(Locale.forLanguageTag("ru")).resources

        assertEquals(
            "1 сохранённый сервер",
            ru.getQuantityString(R.plurals.dashboard_saved_servers, 1, 1),
        )
        assertEquals(
            "2 сохранённых сервера",
            ru.getQuantityString(R.plurals.dashboard_saved_servers, 2, 2),
        )
        assertEquals(
            "5 сохранённых серверов",
            ru.getQuantityString(R.plurals.dashboard_saved_servers, 5, 5),
        )
        assertEquals(
            "1 открытое соединение",
            ru.getQuantityString(R.plurals.sessions_open_connections, 1, 1),
        )
        assertEquals(
            "5 открытых соединений",
            ru.getQuantityString(R.plurals.sessions_open_connections, 5, 5),
        )
    }
}
