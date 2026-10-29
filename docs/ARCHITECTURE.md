# Архитектура SSH Mobile

## 1. Цели и ограничения

MVP должен давать полноценную интерактивную SSH-сессию на Android 10+, безопасно хранить
секреты, строго проверять host key и допускать замену SSH-транспорта без переписывания UI
и терминала. При этом проект должен оставаться обозримым: Clean Architecture без лишних
абстракций, ручной DI, один Gradle-модуль `app`.

Ключевые технические решения:

| Решение | Причина |
|---|---|
| SSHJ 0.41.2 | Зрелый SSHv2-клиент (Apache-2.0), поддержка PTY, современных KEX/cipher и ключей; проверен интеграционным тестом и на Android-эмуляторе |
| BouncyCastle 1.84, устанавливается первым provider | На Android системный «BC» урезан; SSHJ нужен полный BC для Ed25519/ECDSA и разбора ключей. Это рекомендуемое исправление проблем Android P+ и не отключает проверку ключей |
| Собственный VT100/xterm-эмулятор | Позволяет избежать GPL-зависимостей (Termux) и старого Java-кода; полностью тестируется на JVM |
| Room + DataStore | Структурированные данные/секреты и настройки; Flow-запросы нативно интегрируются с StateFlow |
| Ручной `AppContainer` | Прозрачный граф зависимостей и единый lifetime для менеджера сессий; нет codegen/скрытой магии |
| Сессии в Application-scope | Переживают навигацию и смену конфигурации; соединение не привязано к Activity |

## 2. Слои и пакеты

```
core/ssh
  SshEngine, SshConnection, SshShell   — контракт транспорта
  SshjEngine                          — реализация на SSHJ (только IO)
  KnownHostsHostKeyVerifier, HostKeys — политика доверия и fingerprint
  PrivateKeyInspector                 — валидация импортируемого ключа
core/terminal
  TerminalEmulator                    — VT100/xterm state machine, grid, scrollback
  TerminalInput                       — отображение ввода в байты PTY
core/security
  SecretCipher                        — контракт шифрования
  AndroidKeystoreSecretCipher         — AES-256-GCM + AndroidKeyStore
core/database
  AppDatabase, ServerDao, KnownHostDao, entities
core/session
  SshSessionManager, TerminalSession  — lifecycle, reconnect, host-key prompt, ввод/вывод
data/repository
  ServerRepository / LocalServerRepository
  KnownHostRepository / RoomKnownHostRepository
  SettingsRepository (DataStore)
domain/model
  Server, ServerCredentials, ServerDraft, HostKeyInfo, KnownHostKey, HostKeyChallenge
ui/*
  Compose-экраны и ViewModel; не знают про SSHJ
navigation/*
  Compose Navigation, Routes, bottom navigation
```

Зависимости направлены внутрь: `ui → domain/data → core`. `core/ssh` не зависит от UI
и Android; `core/terminal` — чистый Kotlin.

## 3. Поток подключения

```
Dashboard (tap)
  └─ SshSessionManager.openSession(serverId) → sessionId
       └─ TerminalSession.runSession()
            ├─ ServerRepository.getServer(id)
            ├─ ServerRepository.credentials(id)        // decrypt из Keystore
            ├─ KnownHostRepository.keysFor(host, port)
            ├─ SshjEngine.connect(config, verifier)    // IO dispatcher
            │    ├─ TCP + KEX
            │    ├─HostKeyVerifier.verify(host, port, key)
            │    │    ├─ key совпал с known_hosts → true
            │    │    └─ иначе → HostKeyChallenge → UI-диалог (blocking до 180 с)
            │    └─ authPassword / authPublickey
            ├─ openShell(cols, rows) → allocatePTY + startShell
            ├─ writer coroutine  (stdin/resize)
            └─ reader coroutine  (stdout → TerminalEmulator)
```

`TerminalScreen` создаётся по `sessionId` и подписывается на `StateFlow` сессии. Экран не
владеет соединением: кнопка «назад» сохраняет сессию, закрытие сессии — явное.

### Host key

- Фингерпринт: `SHA256:` + Base64(SHA-256(SSH public key blob)) без padding — формат OpenSSH.
  На устройстве проверено совпадение с `ssh-keygen`/логами сервера.
- Сравнение — constant-time по base64 blob в рамках одного типа ключа.
- Новый ключ: диалог «Unknown host key» с fingerprint и предупреждением; при Accept ключ
  сохраняется в `known_hosts` (host, port, algorithm).
- Изменение ключа: `changed=true`, показываются старый и новый fingerprint, текст о MITM;
  Accept заменяет ключ, Reject аварийно завершает handshake.
- При отказе автопереподключение не выполняется (иначе пользователь получал бы бесконечные
  запросы).

## 4. Терминальный эмулятор

`TerminalEmulator` — собственная реализация, не зависит от Android:

- сетка ячеек: codepoint-строка + fg/bg/attrs/width; UTF-8 декодер с обработкой split-байтов;
- управляющие символы BEL/BS/HT/LF/VT/FF/CR/SO/SI;
- CSI: движение курсора, erase (J/K/X), insert/delete (char/line), scroll up/down,
  scroll region (DECSTBM), SGR (16/256/truecolor, bold/dim/italic/underline/blink/reverse/
  hidden/strike), save/restore (DECSC/DECRC), DA/DSR, tab stops;
- приватные режимы: alternate screen 47/1047/1049, auto-wrap, origin, cursor visibility,
  bracketed paste, application cursor keys; mouse reporting игнорируется;
- ESC-последовательности: DECALN, charset G0/G1 (DEC special graphics, UK), index/reverse index;
- OSC 0/2 title, OSC 8/52 игнорируются; DCS/PM/APC пропускаются;
- wide chars (CJK/emoji) занимают 2 ячейки, combining marks добавляются к предыдущей;
- scrollback ограничен (настройка 1k–25k), alternate screen scrollback не имеет;
- `onResponse` отправляет ответы (DA, cursor position) обратно в PTY.

Публикация в UI: чтение/запись защищены `synchronized`; после вывода сигнал в conflated
Channel, публикатор раз в ~16 мс делает immutable snapshot видимых строк. UI не читает
изменяемую сетку напрямую.

Отрисовка (`TerminalCanvas`) использует `android.graphics.Paint` с моноширинным шрифтом:
метрики ячейки вычисляются из `measureText("M")` и font metrics, поэтому сетка стабильна.
Canvas рисует фон, SGR-цвета, underline/strike, wide-glyph с clip, курсор и подсветку
выделения. Жесты: tap → фокус/IME, drag → scroll, long-press + drag → построчное выделение.

## 5. Ввод

- Скрытый `BasicTextField` получает soft-keyboard ввод; буфер аккумулируется и сравнивается
  с предыдущим значением, поэтому IME-батчи не дублируют символы (найденный и исправленный
  баг при первом вводе).
- Sticky Ctrl/Alt применяются к следующему вводу; `TerminalInput` преобразует текст в байты,
  Ctrl-коды, стрелки/Home/End/F1–F12 (с xterm-модификаторами), bracketed paste.
- Аппаратная клавиатура обрабатывается `onPreviewKeyEvent` до текстового поля.
- Extra keys row прокручивается горизонтально; кнопки Copy/Cancel появляются при выделении.

## 6. Состояние сессии и reconnect

```text
CONNECTING ──► VERIFYING_HOST_KEY ──► CONNECTED
     │                  │                 │
     │                  └─ reject ──► DISCONNECTED
     └─ error ──► RECONNECTING (1,2,4,8,15 c) ──► CONNECTING
CONNECTED ── socket lost ──► RECONNECTING
CONNECTED ── shell exit ──► DISCONNECTED
любое ── close() ──► CLOSED
```

- Ошибка аутентификации не ретраится (риск блокировки аккаунта).
- Чистый выход из shell (`exit`) определяется через `connection.isConnected()` и не вызывает
  reconnect; обрыв транспорта — вызывает.
- При reconnect сохраняется содержимое буфера и текущий размер PTY; known host verification
  выполняется заново (но доверенный ключ не спрашивает подтверждения).
- При закрытии: отмена coroutine, закрытие shell/transport, завершение ожидающего host-key
  запроса, освобождение writer/reader.

## 7. Хранение

`ServerEntity`:
`id, name, host, port, username, authType, groupName, favorite, secret, passphrase, keySummary,
createdAt, updatedAt`.

- `secret` — шифротекст пароля или приватного ключа; `passphrase` — шифротекст passphrase.
- Доменный `Server` не содержит секретов; `credentials(id)` расшифровывает их только перед
  подключением.
- Пустое поле при редактировании означает «оставить сохранённый секрет»; смена типа auth
  требует новый секрет.
- `keySummary` (тип + fingerprint) хранится открыто — это не секрет и помогает пользователю.

`KnownHostEntity` — PK `(host, port, keyType)`, хранит base64 SSH-blob и fingerprint.

`AppSettings` в DataStore: размер шрифта, keep screen on, FLAG_SECURE, auto reconnect,
scrollback, тема.

## 8. UI и навигация

- `MainActivity`: edge-to-edge, тема из настроек, `FLAG_SECURE`, системные бары.
- `AppNavigation`: bottom bar (Servers/Sessions/Settings) для верхнего уровня; терминал,
  редактор, known hosts и SFTP — полноэкранные маршруты.
- ViewModel создаются фабрикой с зависимостями из `LocalAppContainer`.
- Все долгие операции — в ViewModel/менеджере, не в composable.
- Темы: тёмная по умолчанию, зелёный акцент `#00E676`, терминал всегда тёмный для контраста.

### Локализация

- Все строки находятся в `res/values/strings.xml` (English) и `res/values-ru/strings.xml`.
- `AppLanguage` (`SYSTEM`, `ENGLISH`, `RUSSIAN`) хранится в DataStore.
- `AppLocalization` создаёт `ConfigurationContext` с выбранной локалью и подменяет
  `LocalContext`/`LocalConfiguration` для Compose. Переключение в Settings применяется
  сразу и не требует перезапуска.
- Ошибки сессии передаются из `core/session` как `SessionError` (enum), а не как готовые
  английские строки; UI переводит их через ресурсы. Валидация формы возвращает
  `ValidationError`; ViewModel хранит `@StringRes errorRes`.
- Плюрализация русского языка реализована через Android plurals (`one/few/many/other`).

## 9. Lifecycle и ограничения

- Сессии принадлежат `Application` (singleton `SshSessionManager`); Activity recreation
  не рвёт соединение.
- Нет foreground service: при убийстве процесса ОС закроет сокеты. Для v0.2 рассматривается
  foreground service с уведомлением и явным согласием пользователя.
- `Application.onTerminate` на реальных устройствах не вызывается; корректное завершение
  обеспечивается Closed/CloseAll и закрытием транспорта.
- При потере процесса незавершённые сессии исчезают; сохранённые серверы/known hosts остаются.

## 10. Точки расширения

- `SshEngine`/`SshConnection`/`SshShell` — замена SSHJ, добавление jump host/port forwarding.
- `TerminalEmulator` — mouse reporting, hyperlinks, sixel, reflow, кастомизация палитры.
- `SecretCipher` — переход на hardware-backed ключи с biometric gate.
- `ServerRepository` — импорт/экспорт конфигураций (v0.2), синхронизация без секретов.
- `Routes` — SFTP/администрирование (v0.3/v0.4).

## 11. Тестируемость

- `core/terminal` и `core/ssh` не зависят от Android → быстрые JVM-тесты.
- `SshjEngine` покрыт интеграционным тестом с in-process Apache MINA SSHD: реальный KEX,
  пароль/RSA-ключ, PTY, resize, отказ host key.
- Room-репозитории проверяются Robolectric с in-memory БД и fake-cipher.
- Android Keystore проверяется только на устройстве/эмуляторе (провайдер недоступен в JVM).
