# Third-party licenses

SSH Mobile includes the following open-source components. Full license texts are available
in the respective upstream repositories.

| Component | Version | License | Use |
|---|---|---|---|
| [SSHJ](https://github.com/hierynomus/sshj) | 0.41.2 | Apache License 2.0 | SSH transport, PTY, key parsing |
| [Bouncy Castle](https://www.bouncycastle.org/) | 1.84 | Bouncy Castle License (MIT-style) | Cryptography provider, key formats |
| [AndroidX / Jetpack Compose](https://developer.android.com/jetpack) | BOM 2025.04.01 | Apache License 2.0 | UI, lifecycle, navigation |
| [Room](https://developer.android.com/training/data-storage/room) | 2.7.1 | Apache License 2.0 | Local database |
| [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | 1.1.4 | Apache License 2.0 | Settings persistence |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.10.2 | Apache License 2.0 | Async/concurrency |
| [SLF4J API](https://www.slf4j.org/) | 2.0.17 | MIT License | Logging facade (no-op binding) |
| [Apache MINA SSHD](https://mina.apache.org/sshd-project/) | 2.15.0 | Apache License 2.0 | **Test-only** in-process SSH server |
| [Robolectric](https://robolectric.org/) | 4.14.1 | MIT License | **Test-only** Android runtime |
| [JUnit 4](https://junit.org/junit4/) | 4.13.2 | Eclipse Public License 1.0 | **Test-only** |

The VT100/xterm terminal emulator, SSH session manager, UI, cryptography integration and
repository layer are original code of this project. No GPL terminal-emulator code is used.

The Apache License 2.0 text for this project is in [LICENSE](../LICENSE).
