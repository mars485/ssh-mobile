package dev.sshmobile

import android.app.Application
import dev.sshmobile.core.security.CryptoProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SshMobileApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CryptoProviders.install()
        container = AppContainer(this, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
