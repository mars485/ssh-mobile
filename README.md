# SSH Mobile — SSH-клиент для Android (v0.1.2)

Нативный SSH-клиент для Android 10+ с интерактивным терминалом, менеджером серверов,
проверкой host key и шифрованием секретов через Android Keystore.

Стек: **Kotlin 2.1**, **Jetpack Compose + Material 3**, **MVVM + Repository**,
**SSHJ 0.41.2**, **Room 2.7.1**, **Android Keystore (AES-256-GCM)**, **Coroutines/StateFlow**,
**Gradle Kotlin DSL**.

| Dashboard | Терминал | Host key |
|---|---|---|
| ![](docs/screenshots/dashboard.png) | ![](docs/screenshots/terminal-key-auth.png) | ![](docs/screenshots/host-key.png) |

| htop | vim | SSH-ключ |
|---|---|---|
| ![](docs/screenshots/htop.png) | ![](docs/screenshots/vim.png) | ![](docs/screenshots/server-key.png) |

| Русский интерфейс | Настройки |
|---|---|
| ![](docs/screenshots/dashboard-ru.png) | ![](docs/screenshots/settings-ru.png) |

## Статус

- Исходный код **v0.1.2** готов, debug и release APK собраны.
- 42 автоматических теста (JVM + Robolectric) проходят, включая реальное SSH-рукопожатие
  с in-process сервером Apache MINA SSHD и проверку русских/английских ресурсов и плюрализации.
- Проверено на эмуляторе **Android 15 (API 35)**: установка, вход по паролю, вход по
  SSH-ключу (импорт из файла), host-key confirmation с совпадающим fingerprint,
  интерактивный bash, htop, vim, resize PTY, две одновременные сессии.
- Release APK установлен на физическое устройство **HONOR LLY-LX1 (Android 14)**;
  проверены запуск, отсутствие падений, русский интерфейс и системная локализация.
- Поддерживаются русский и английский языки; язык выбирается в настройках или берётся
  из системы.

## Реализованные функции

### Менеджер серверов
- Добавление, редактирование и удаление серверов.
- Имя, host/IP, порт, пользователь.
- Пароль или приватный ключ (PEM/OpenSSH), passphrase для ключа.
- Группы, избранное, поиск по имени/host/группе.
- Локальное хранение в Room; быстрый запуск сессии одним нажатием.
- Импорт приватного ключа через системный файловый picker (SAF) с предварительной проверкой
  формата/пароля и показом типа ключа и fingerprint.
- Хранимые секреты не показываются обратно в UI; при редактировании поле можно оставить пустым.
- Форма не перекрывается экранной клавиатурой: контент прокручивается и учитывает IME insets.

### Локализация
- Русский и английский интерфейс, тёмная/светлая тема.
- Выбор языка в Settings: «Системный», English, Русский; применяется сразу, сохраняется
  между запусками.
- Все строки вынесены в ресурсы (`values` / `values-ru`), включая ошибки, диалоги host key
  и валидацию формы.

### SSH-терминал
- Полноценная интерактивная shell-сессия с PTY (`xterm-256color`).
- Собственный VT100/xterm-эмулятор (без GPL-кода): UTF-8, ANSI/SGR 16/256/truecolor,
  альтернативный экран, scrollback, вставка/удаление, scroll regions, DEC line drawing,
  bracketed paste, ответы на DA/DSR, title через OSC.
- Работает с bash, htop, vim, nano, less.
- Экранные клавиши: Esc, Tab, Ctrl (sticky), Alt (sticky), стрелки, Home/End, PgUp/PgDn,
  `^C`, `^D`, `^Z`, Paste, Copy.
- Аппаратная клавиатура: стрелки, F1–F12, Home/End, Del/Ins, Ctrl/Alt-комбинации.
- Копирование: выделение строки долгим нажатием + drag, кнопка Copy; просмотр и копирование
  всего буфера через меню.
- Вставка из системного clipboard, с bracketed paste, если приложение его включило.
- Resize терминала отправляется на сервер как window-change; проверено `stty size`.
- Несколько одновременных сессий, экран Sessions, закрытие одной/всех, reconnect.

### Безопасность
- Пароли, приватные ключи и passphrase шифруются AES-256-GCM ключом, который генерируется
  в Android Keystore (StrongBox при наличии) и не экспортируется.
- Строгая проверка host key: новый ключ требует подтверждения с SHA-256 fingerprint;
  изменение известного ключа показывается отдельным предупреждением (возможен MITM).
- Host key хранится per host/port/algorithm в Room.
- Пароли и ключи не пишутся в логи; SLF4J в SSHJ отключён (`slf4j-nop`).
- Backup приложения отключён (`allowBackup=false` + data extraction rules).
- Опциональный `FLAG_SECURE` (запрет скриншотов и скрытие в Recent).
- Очистка known hosts и полное удаление локальных данных в Settings.

### UX
- Тёмная тема по умолчанию, зелёный акцент, моноширинный терминал; светлая тема опционально.
- Настройки шрифта терминала, scrollback, keep screen on, авто-reconnect, screenshots.
- Управление known hosts, просмотр fingerprint.
- Заглушка-раздел SFTP с планом v0.3 (сам SFTP в MVP не реализован).

## Быстрый старт

Требования:
- JDK 17 или 21;
- Android SDK Platform 35 и Build-Tools 35.0.0;
- Gradle-wrapper (Gradle 8.13 скачается автоматически) или локальный Gradle 8.13.

```bash
# local.properties
sdk.dir=/path/to/android-sdk

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew testDebugUnitTest
```

Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

### Release-подпись

Создайте keystore **вне репозитория** и файл `keystore.properties` в корне проекта
(оба игнорируются Git):

```properties
storeFile=C:/secure/sshmobile-release.jks
storePassword=...
keyAlias=sshmobile
keyPassword=...
```

```bash
keytool -genkeypair -keystore C:/secure/sshmobile-release.jks -alias sshmobile \
  -keyalg RSA -keysize 4096 -validity 10000
./gradlew assembleRelease
```

Без `keystore.properties` release-сборка останется неподписанной; debug подписывается
стандартным debug-key. Секреты подписи в репозиторий не коммитятся.

### Готовые артефакты

| Артефакт | Путь | Размер |
|---|---|---|
| Debug APK | `dist/ssh-mobile-0.1.2-debug.apk` | 66.95 МБ (debug, без shrink) |
| Release APK (подписан) | `dist/ssh-mobile-0.1.2-release.apk` | 9.66 МБ |

SHA-256:

```
release: A0A0A0303683297B278D8901744AC17AFC2C7DF6469EBDF3478E4D7ED31AF851
debug:   D66B6C93768510CC2AFB668FD448F965C150ADC843279D64B4EA73391E42DCBE
```

Копии также лежат в `dist/`; контрольные суммы — `dist/SHA256SUMS.txt`.
Сертификат release-сборки и подробный отчёт: [docs/TESTING.md](docs/TESTING.md).

### RuStore

Материалы для публикации: [docs/RUSTORE.md](docs/RUSTORE.md), политика
конфиденциальности: [docs/PRIVACY.md](docs/PRIVACY.md), иконка и скриншоты — в `store/`.
APK подписан release-ключом; keystore и пароли в репозиторий не входят.

## Архитектура

```
app/src/main/java/dev/sshmobile/
  core/
    ssh/         SshEngine (интерфейс), SshjEngine, KnownHostsVerifier, HostKeys, PrivateKeyInspector
    terminal/    TerminalEmulator (VT100/xterm), TerminalInput, TextWidth
    security/    SecretCipher, AndroidKeystoreSecretCipher, CryptoProviders
    database/    Room: AppDatabase, DAO, entities
    session/     SshSessionManager, TerminalSession, состояние сессии
  data/
    repository/  ServerRepository, KnownHostRepository, SettingsRepository (DataStore)
  domain/model/  Server, ServerCredentials, ServerDraft, HostKeyChallenge
  ui/
    servers/     Dashboard, Add/Edit server
    terminal/    TerminalScreen, TerminalCanvas, ExtraKeysRow
    sessions/    Sessions
    settings/    Settings, Known hosts
    sftp/        информационный экран
    components/  общие composable
    theme/
  navigation/    Compose Navigation  AppContainer.kt / SshMobileApplication.kt / MainActivity.kt
```

Сетевой слой скрыт за `SshEngine`; замена SSHJ на другую библиотеку не затрагивает UI,
эмулятор терминала и репозитории. Подробности: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Тестирование

```bash
./gradlew testDebugUnitTest
```

42 теста, 0 падений (на 29.10.2026):

- 22 теста терминального эмулятора (курсор, SGR, альтернативный экран, UTF-8, wide chars,
  scrollback, resize, DEC charset, OSC, DA/DSR, bracketed paste);
- 7 интеграционных SSH-тестов: реальный handshake с Apache MINA SSHD, пароль и RSA-ключ,
  PTY 80×24, echo, resize, отказ при неверном пароле, отказ при отклонённом host key,
  смена host key, fingerprint;
- 8 тестов Room-репозитория серверов (шифрование at rest, обновление без смены секрета,
  избранное, удаление, повреждённый шифротекст);
- 3 теста known_hosts (per host/port/algorithm, timestamps, удаление);
- 2 теста локализации: английские/русские строки, плюрализация `one/few/many`.

Проверка на эмуляторе и ручной чек-лист: [docs/TESTING.md](docs/TESTING.md).

## Безопасность

Модель угроз, гарантии и ограничения: [docs/SECURITY.md](docs/SECURITY.md).

Коротко:
- Android Keystore + AES-GCM, ключ не покидает Keystore;
- строгий known-hosts с подтверждением fingerprint и предупреждением при смене ключа;
- секреты не логируются и не бэкапятся;
- MITM-защита не отключается никакими настройками;
- при компрометации разблокированного устройства/root-доступе защита секретов ограничена
  возможностями платформы.

## Известные ограничения MVP

- SFTP не реализован (v0.3), экран SFTP информационный.
- Нет jump host, port forwarding, сниппетов и истории команд (v0.2).
- Нет поддержки SSH agent, FIDO/U2F и сертификатов; поддерживаются пароль, PEM/OpenSSH-ключи
  (RSA/ECDSA/Ed25519) и passphrase.
- Мышь в терминале (mouse reporting) не поддерживается; TUI работают с клавиатурой.
- Выделение текста построчное, не посимвольное; полный буфер копируется через меню.
- Resize не делает reflow уже выведенного текста; содержимое обрезается/дополняется.
- Сессии живут в процессе приложения; foreground service для фона не используется, поэтому
  ОС может убить процесс и соединение. Явное закрытие и reconnect реализованы.
- Автопереподключение до 5 попыток с backoff; после исчерпания — ручной Reconnect.
- Диалог подтверждения host key автоматически отменяется через 180 секунд, если пользователь
  его не закрыл (защита от «висящего» подключения); при таймауте показывается отдельное
  сообщение.
- Нет паролей, запрашиваемых при каждом подключении: сохранённые креды используются для
  reconnect (это осознанный trade-off).
- Локализация: русский/английский/системный; остальные языки пока не добавлены.
- Ширина эмодзи/сложных grapheme-кластеров аппроксимируется; редкие шрифты могут смещать сетку.

## План развития

**v0.2 — расширенный терминал**
- сниппеты и история команд;
- настройка шрифтов/цветовых схем, темы терминала;
- SSH jump host (ProxyJump);
- локальный/удалённый port forwarding;
- импорт/экспорт конфигурации без секретов в открытом виде.

**v0.3 — SFTP**
- просмотр каталогов, upload/download, создание/переименование/удаление;
- редактирование текстовых файлов, права доступа;
- прогресс и очередь передач, работа в фоне.

**v0.4 — администрирование**
- мониторинг CPU/RAM/диска/uptime;
- Docker (контейнеры, статус, логи), Proxmox VM/LXC, OpenWrt, systemd;
- подтверждение опасных операций (удаление, reboot, stop service).

## Лицензии

- Исходный код проекта — [Apache License 2.0](LICENSE).
- SSHJ — Apache License 2.0.
- Bouncy Castle — Bouncy Castle License (MIT-подобная).
- AndroidX/Compose/Room — Apache License 2.0.
- Полный список: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
- Терминальный эмулятор и остальной код проекта — собственный, без GPL-зависимостей.
