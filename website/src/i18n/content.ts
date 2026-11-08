export type Lang = 'ru' | 'en';

export const site = {
  repo: 'https://github.com/mars485/ssh-mobile',
  issues: 'https://github.com/mars485/ssh-mobile/issues',
  rustore: 'https://www.rustore.ru/catalog/app/dev.sshmobile',
  privacy: 'https://github.com/mars485/ssh-mobile/blob/main/docs/PRIVACY.md',
  licenses: 'https://github.com/mars485/ssh-mobile/blob/main/THIRD_PARTY_LICENSES.md',
  changelog: 'https://github.com/mars485/ssh-mobile/blob/main/CHANGELOG.md',
  security: 'https://github.com/mars485/ssh-mobile/blob/main/docs/SECURITY.md',
  testing: 'https://github.com/mars485/ssh-mobile/blob/main/docs/TESTING.md',
  email: 'info@itgsystem.ru',
  itg: 'https://itgsystem.ru',
  packageId: 'dev.sshmobile',
  /** Версия, доступная в RuStore на момент сборки сайта (проверено 08.11.2026). */
  versionInStore: '0.1.2',
  /** Версия из репозитория, ожидающая публикации в RuStore. */
  versionPending: '0.3.0',
  androidMin: '10',
  license: 'Apache-2.0',
}

export type FeatureIcon =
  | 'terminal'
  | 'folder'
  | 'key'
  | 'jump'
  | 'ports'
  | 'layers'
  | 'snippet'
  | 'shield'

export interface Feature {
  icon: FeatureIcon
  index: string
  tag: string
  title: string
  text: string
}

export type ShotId =
  | 'dashboard'
  | 'terminal'
  | 'new-server'
  | 'sessions'
  | 'htop'
  | 'vim'
  | 'reconnect'

export interface Shot {
  id: ShotId
  title: string
  caption: string
  alt: string
}

export interface Release {
  version: string
  date: string
  badge: string
  highlight: boolean
  items: string[]
}

export interface FaqEntry {
  q: string
  a: string
}

export interface Dict {
  seo: {
    title: string
    description: string
    ogTitle: string
    ogDescription: string
    keywords: string
  }
  nav: {
    features: string
    screenshots: string
    security: string
    faq: string
    download: string
    menu: string
    close: string
    skip: string
  }
  hero: {
    eyebrow: string
    title: string
    tagline: string
    lead: string
    primary: string
    secondary: string
    statusLabel: string
    status: string
    stats: { value: string; label: string }[]
    terminalTitle: string
    terminalLines: string[]
  }
  features: {
    kicker: string
    title: string
    lead: string
    note: string
    items: Feature[]
  }
  screenshots: {
    kicker: string
    title: string
    lead: string
    items: Shot[]
    hint: string
    allLink: string
    lightbox: { close: string; prev: string; next: string; counter: string }
  }
  advantages: {
    kicker: string
    title: string
    lead: string
    items: { title: string; text: string }[]
  }
  security: {
    kicker: string
    title: string
    lead: string
    items: { title: string; text: string; tag: string }[]
    absenceTitle: string
    absence: string[]
    link: string
  }
  start: {
    kicker: string
    title: string
    lead: string
    steps: { step: string; title: string; text: string; code?: string }[]
    cta: string
    note: string
  }
  changelog: {
    kicker: string
    title: string
    lead: string
    link: string
    items: Release[]
  }
  faq: {
    kicker: string
    title: string
    lead: string
    items: FaqEntry[]
  }
  footer: {
    tagline: string
    product: string
    docs: string
    links: { label: string; href: string }[]
    madeBy: string
    rights: string
    updated: string
  }
  cta: {
    title: string
    text: string
    note: string
  }
}

const ru: Dict = {
  seo: {
    title: 'SSH Mobile — SSH-клиент для Android с терминалом и SFTP',
    description:
      'SSH-клиент для Android: терминал VT100/xterm, SFTP, ключи, ProxyJump и проброс портов. Пароли и ключи шифруются через Android Keystore и остаются на устройстве.',
    ogTitle: 'SSH Mobile — ваши серверы всегда под рукой',
    ogDescription:
      'Терминал, SFTP, ключи, ProxyJump и проброс портов в нативном Android-приложении. Локальное хранение, проверка host key, открытый исходный код.',
    keywords:
      'SSH клиент Android, SSH терминал, SFTP, ProxyJump, SSH ключи, мобильный SSH, SSH Mobile, RuStore',
  },
  nav: {
    features: 'Возможности',
    screenshots: 'Скриншоты',
    security: 'Безопасность',
    faq: 'FAQ',
    download: 'Скачать',
    menu: 'Меню',
    close: 'Закрыть',
    skip: 'Перейти к содержанию',
  },
  hero: {
    eyebrow: 'Android · открытый исходный код',
    title: 'SSH Mobile',
    tagline: 'Ваши серверы. Всегда под рукой',
    lead: 'Нативный SSH-клиент для Android: интерактивный терминал, SFTP, ключи, jump host и проброс портов. Без рекламы, аналитики и облака — серверы и учётные данные остаются на устройстве.',
    primary: 'Скачать в RuStore',
    secondary: 'Исходный код',
    statusLabel: 'Статус',
    status: 'В RuStore — 0.1.2 · 0.3.0 на модерации',
    stats: [
      { value: '10+', label: 'Android' },
      { value: 'RU · EN', label: 'языки' },
      { value: '0', label: 'трекеров' },
    ],
    terminalTitle: 'prod-web-01 — терминал',
    terminalLines: [
      '$ ssh deploy@prod-web-01',
      'Welcome to Ubuntu 24.04 LTS',
      'deploy@prod-web-01:~$ htop',
      'deploy@prod-web-01:~$ vim nginx.conf',
    ],
  },
  features: {
    kicker: '01 — Возможности',
    title: 'Всё для работы с сервером — без ноутбука',
    lead: 'Восемь блоков, которые закрывают повседневное администрирование: от подключения по ключу до правки конфигов и передачи файлов.',
    note: 'SFTP, ProxyJump, проброс портов и расширенный терминал добавлены в версии 0.3.0 из репозитория; в RuStore на дату сборки доступна 0.1.2.',
    items: [
      {
        icon: 'terminal',
        index: '01',
        tag: 'ssh · pty',
        title: 'Интерактивный терминал',
        text: 'VT100/xterm с поддержкой bash, zsh, htop, vim, nano и less. Экранные клавиши Ctrl, Alt, Esc и Tab, стрелки, копирование и вставка.',
      },
      {
        icon: 'folder',
        index: '02',
        tag: 'sftp · 0.3',
        title: 'Файлы по SFTP',
        text: 'Просмотр каталогов, создание папок, переименование, удаление и смена прав. Загрузка и скачивание через системный выбор файлов с прогрессом и очередью.',
      },
      {
        icon: 'key',
        index: '03',
        tag: 'auth',
        title: 'Пароль и SSH-ключи',
        text: 'Ключи PEM и OpenSSH с passphrase, импорт из файла, вход по паролю. Ключ и пароль шифруются перед сохранением в базу приложения.',
      },
      {
        icon: 'jump',
        index: '04',
        tag: 'proxyjump',
        title: 'Jump host',
        text: 'Подключение через сохранённый сервер-шлюз. Проверяются оба host key — и шлюза, и целевого сервера; вложенные цепочки не поддерживаются.',
      },
      {
        icon: 'ports',
        index: '05',
        tag: 'forwarding',
        title: 'Проброс портов',
        text: 'Локальный и удалённый форвардинг. По умолчанию проброс слушает 127.0.0.1 и закрывается вместе с SSH-сессией.',
      },
      {
        icon: 'layers',
        index: '06',
        tag: 'sessions',
        title: 'Несколько сессий',
        text: 'Держите открытыми несколько подключений сразу, переключайтесь между ними и переподключайтесь после обрыва связи без потери списка серверов.',
      },
      {
        icon: 'snippet',
        index: '07',
        tag: 'local',
        title: 'Сниппеты и история',
        text: 'Частые команды — под рукой. История ввода выключена по умолчанию, хранится зашифрованной и не перехватывает стрелки в TUI-программах.',
      },
      {
        icon: 'shield',
        index: '08',
        tag: 'keystore',
        title: 'Защита учётных данных',
        text: 'Android Keystore с AES-256-GCM, проверка host key по SHA-256, предупреждение при смене ключа сервера и экспорт конфигурации без секретов.',
      },
    ],
  },
  screenshots: {
    kicker: '02 — Скриншоты',
    title: 'Как это выглядит на телефоне',
    lead: 'Реальные экраны из репозитория проекта. Нажмите на снимок, чтобы открыть его крупнее и полистать галерею.',
    hint: 'Нажмите на скриншот для увеличения',
    allLink: 'Все скриншоты в репозитории',
    items: [
      {
        id: 'dashboard',
        title: 'Серверы',
        caption: 'Поиск, группы, избранное и добавление первого подключения.',
        alt: 'Экран списка серверов SSH Mobile на русском языке',
      },
      {
        id: 'terminal',
        title: 'Терминал по ключу',
        caption: 'Вход по приватному ключу, статус подключения и экранные клавиши.',
        alt: 'Терминал SSH Mobile с приглашением командной строки',
      },
      {
        id: 'new-server',
        title: 'Новый сервер',
        caption: 'Пароль или ключ, passphrase, импорт из файла — на одном экране.',
        alt: 'Форма добавления сервера с SSH-ключом и passphrase',
      },
      {
        id: 'sessions',
        title: 'Сессии',
        caption: 'Несколько одновременных подключений с временем жизни сессии.',
        alt: 'Экран активных SSH-сессий',
      },
      {
        id: 'htop',
        title: 'htop',
        caption: 'Полноэкранные утилиты работают как в настольном терминале.',
        alt: 'htop, запущенный в терминале SSH Mobile',
      },
      {
        id: 'vim',
        title: 'vim',
        caption: 'Правка конфигов на сервере — с мобильной раскладкой клавиш.',
        alt: 'vim в терминале SSH Mobile',
      },
      {
        id: 'reconnect',
        title: 'Переподключение',
        caption: 'Понятное сообщение при обрыве и повторный вход в один тап.',
        alt: 'Диалог восстановления соединения в SSH Mobile',
      },
    ],
    lightbox: {
      close: 'Закрыть',
      prev: 'Предыдущий скриншот',
      next: 'Следующий скриншот',
      counter: 'из',
    },
  },
  advantages: {
    kicker: '03 — Преимущества',
    title: 'Сделано под Android, а не под браузер',
    lead: 'Приложение собрано на Kotlin и Jetpack Compose. Никакой WebView-обёртки, тяжёлых SDK и фоновой телеметрии.',
    items: [
      {
        title: 'Нативный Android',
        text: 'Kotlin, Jetpack Compose и SSH-транспорт SSHJ. Приложение ведёт себя как системное: тёмная тема, жесты, экранные клавиши, предсказуемая работа клавиатуры.',
      },
      {
        title: 'Русский интерфейс',
        text: 'Русский и английский языки с мгновенным переключением без перезапуска. Все строки, диалоги и ошибки локализованы.',
      },
      {
        title: 'Удобное управление',
        text: 'Панель Ctrl/Alt/Esc/Tab, выбор моноширинного шрифта, размера текста и четырёх цветовых схем терминала — под маленький экран и одну руку.',
      },
      {
        title: 'Локальные данные',
        text: 'Нет аккаунтов, аналитики, рекламных SDK и облачной синхронизации. Резервное копирование средствами Android отключено, данные удаляются вместе с приложением.',
      },
    ],
  },
  security: {
    kicker: '04 — Безопасность',
    title: 'Учётные данные не покидают устройство',
    lead: 'Модель угроз и детали реализации описаны в документации проекта. Ниже — то, что можно проверить в коде и на устройстве.',
    items: [
      {
        title: 'Android Keystore',
        text: 'Пароли, приватные ключи и passphrase шифруются ключом, который создаётся в аппаратно-защищённом хранилище Android и не экспортируется. Расшифровка — только для активного подключения.',
        tag: 'key · non-exportable',
      },
      {
        title: 'AES-256-GCM',
        text: 'Симметричное шифрование с аутентификацией: изменение шифротекста обнаруживается при расшифровке. Секреты не попадают в логи и не выгружаются в резервные копии.',
        tag: 'aead',
      },
      {
        title: 'Проверка host key',
        text: 'При первом подключении показывается SHA-256 fingerprint сервера. Смена ключа требует явного подтверждения — в том числе при работе через jump host.',
        tag: 'tofu · sha-256',
      },
      {
        title: 'Контроль экспорта',
        text: 'Экспорт конфигурации выгружает серверы и настройки, но не пароли, приватные ключи и passphrase. Импорт позволяет сохранить сервер и заполнить учётные данные позже.',
        tag: 'no secrets',
      },
    ],
    absenceTitle: 'Чего в приложении нет',
    absence: [
      'аналитики и crash-репортинга',
      'рекламных и трекинговых SDK',
      'облачной синхронизации и аккаунтов',
      'чтения буфера обмена без действия пользователя',
    ],
    link: 'Документация по безопасности',
  },
  start: {
    kicker: '05 — Как начать',
    title: 'Три шага до первого подключения',
    lead: 'Нужен только телефон и доступ к любому SSH-серверу. Демо-аккаунта нет по соображениям безопасности — тестовый сервер поднимается локально.',
    steps: [
      {
        step: '01',
        title: 'Установите приложение',
        text: 'Откройте карточку в RuStore и установите актуальную версию. Для сборки из исходников нужен Android Studio и JDK 17.',
        code: 'git clone https://github.com/mars485/ssh-mobile',
      },
      {
        step: '02',
        title: 'Добавьте сервер',
        text: 'Укажите имя, адрес, порт и пользователя. Выберите вход по паролю или вставьте приватный ключ, при необходимости — passphrase.',
        code: 'host: 192.0.2.10  port: 22  user: deploy',
      },
      {
        step: '03',
        title: 'Подключитесь',
        text: 'Проверьте SHA-256 fingerprint сервера и подтвердите его. Дальше — обычный терминал: команды, htop, редакторы, передача файлов по SFTP.',
        code: 'SHA256:9f2c…a71b accepted',
      },
    ],
    cta: 'Открыть в RuStore',
    note: 'APK из репозитория — релизные сборки. Для проверки советуем собственный тестовый sshd: инструкция в docs/TESTING.md.',
  },
  changelog: {
    kicker: '06 — История версий',
    title: 'Что менялось',
    lead: 'Полный список изменений ведётся в CHANGELOG.md репозитория.',
    link: 'Открыть CHANGELOG.md',
    items: [
      {
        version: '0.3.0',
        date: '8 ноября 2026',
        badge: 'на модерации RuStore',
        highlight: true,
        items: [
          'SFTP: файловый менеджер, загрузка и скачивание через SAF, очередь передач, права доступа.',
          'Сниппеты команд и локальная история с шифрованием, выбор шрифта и четыре цветовые схемы.',
          'Jump host, локальный и удалённый проброс портов.',
          'Экспорт и импорт конфигурации без паролей и ключей.',
        ],
      },
      {
        version: '0.1.2',
        date: '29 октября 2026',
        badge: 'в RuStore',
        highlight: false,
        items: [
          'Исправлено падение при открытии экрана добавления сервера после локализации.',
          'Импорт SSH-ключа через системный выбор файла снова работает.',
          'Проверено на HONOR Android 14 и эмуляторе Android 15.',
        ],
      },
      {
        version: '0.1.1',
        date: '29 октября 2026',
        badge: '',
        highlight: false,
        items: [
          'Русская локализация и переключение языка без перезапуска.',
          'Форма сервера больше не перекрывается экранной клавиатурой.',
          'Материалы для RuStore, политика конфиденциальности и лицензии.',
        ],
      },
      {
        version: '0.1.0',
        date: '29 октября 2026',
        badge: '',
        highlight: false,
        items: [
          'Первый MVP: менеджер серверов, пароль и SSH-ключ, PTY-терминал.',
          'Проверка host key, Android Keystore, несколько сессий, переподключение.',
          '40 автоматических тестов; проверка на Android 14 и 15.',
        ],
      },
    ],
  },
  faq: {
    kicker: '07 — FAQ',
    title: 'Частые вопросы',
    lead: 'Если ответа нет — создайте issue в репозитории: это самый быстрый канал поддержки.',
    items: [
      {
        q: 'Приложение бесплатное?',
        a: 'Да. SSH Mobile распространяется бесплатно, без рекламы, встроенных покупок и подписок. Исходный код открыт на GitHub под лицензией Apache-2.0.',
      },
      {
        q: 'Нужен ли собственный сервер?',
        a: 'Да. Приложение подключается к любому серверу с SSH: VPS, домашний сервер, Raspberry Pi, роутер. Публичного демо-сервера нет — как поднять локальный sshd для проверки, описано в docs/TESTING.md.',
      },
      {
        q: 'Какие способы входа поддерживаются?',
        a: 'Пароль и приватный ключ в форматах PEM и OpenSSH, в том числе с passphrase. Ключ можно вставить текстом или импортировать из файла через системный выбор файлов.',
      },
      {
        q: 'Данные куда-то передаются?',
        a: 'Нет. В приложении нет аналитики, crash-репортинга и трекеров. Соединения устанавливаются только к серверам, которые вы добавили сами, а серверы, ключи и отпечатки host key хранятся на устройстве.',
      },
      {
        q: 'Есть ли SFTP и проброс портов?',
        a: 'Да, в версии 0.3.0: файловый менеджер SFTP с очередью передач, jump host, локальный и удалённый проброс портов. На дату публикации сайта в RuStore доступна 0.1.2, версия 0.3.0 находится на модерации; собрать её можно из репозитория.',
      },
      {
        q: 'Какая минимальная версия Android?',
        a: 'Android 10 и новее. Приложение проверено на HONOR с Android 14 и на эмуляторе Android 15.',
      },
      {
        q: 'Можно ли перенести серверы на новый телефон?',
        a: 'Да, через экспорт и импорт конфигурации. Пароли, приватные ключи и passphrase в файл не попадают — их нужно ввести заново. Автоматической синхронизации через облако нет.',
      },
      {
        q: 'Где сообщить об ошибке или уязвимости?',
        a: 'Об ошибках — в GitHub Issues. Об уязвимостях — по инструкции из docs/SECURITY.md; не прикладывайте реальные пароли и приватные ключи.',
      },
    ],
  },
  footer: {
    tagline: 'Нативный SSH-клиент для Android. Открытый исходный код, локальные данные, без рекламы.',
    product: 'Продукт',
    docs: 'Документы',
    links: [
      { label: 'GitHub', href: site.repo },
      { label: 'RuStore', href: site.rustore },
      { label: 'Issues', href: site.issues },
      { label: 'CHANGELOG', href: site.changelog },
      { label: 'Политика конфиденциальности', href: site.privacy },
      { label: 'Лицензии', href: site.licenses },
      { label: 'Безопасность', href: site.security },
    ],
    madeBy: 'Разработка — IT GROUP',
    rights: '© 2026 IT GROUP. Приложение распространяется по лицензии Apache-2.0.',
    updated: 'Статус RuStore проверен',
  },
  cta: {
    title: 'Сервер под рукой, когда он нужен',
    text: 'Установите SSH Mobile из RuStore или соберите APK из исходников — и подключайтесь к своим серверам с телефона.',
    note: 'Android 10+ · бесплатно · без рекламы и трекеров',
  },
}

const en: Dict = {
  seo: {
    title: 'SSH Mobile — SSH client for Android with terminal and SFTP',
    description:
      'Native SSH client for Android: terminal, SFTP, keys, ProxyJump and port forwarding. Credentials stay on the device, encrypted with Android Keystore.',
    ogTitle: 'SSH Mobile — your servers, always at hand',
    ogDescription:
      'Terminal, SFTP, keys, ProxyJump and port forwarding in a native Android app. Local storage, host key verification, open source.',
    keywords:
      'SSH client Android, SSH terminal, SFTP, ProxyJump, SSH keys, mobile SSH, SSH Mobile, RuStore',
  },
  nav: {
    features: 'Features',
    screenshots: 'Screenshots',
    security: 'Security',
    faq: 'FAQ',
    download: 'Download',
    menu: 'Menu',
    close: 'Close',
    skip: 'Skip to content',
  },
  hero: {
    eyebrow: 'Android · open source',
    title: 'SSH Mobile',
    tagline: 'Your servers. Always at hand',
    lead: 'A native SSH client for Android: interactive terminal, SFTP, keys, jump host and port forwarding. No ads, no analytics, no cloud — servers and credentials stay on the device.',
    primary: 'Get it on RuStore',
    secondary: 'Source code',
    statusLabel: 'Status',
    status: 'RuStore build 0.1.2 · 0.3.0 in review',
    stats: [
      { value: '10+', label: 'Android' },
      { value: 'RU · EN', label: 'languages' },
      { value: '0', label: 'trackers' },
    ],
    terminalTitle: 'prod-web-01 — terminal',
    terminalLines: [
      '$ ssh deploy@prod-web-01',
      'Welcome to Ubuntu 24.04 LTS',
      'deploy@prod-web-01:~$ htop',
      'deploy@prod-web-01:~$ vim nginx.conf',
    ],
  },
  features: {
    kicker: '01 — Features',
    title: 'Everything you need for a server — without a laptop',
    lead: 'Eight areas that cover everyday administration: from key-based login to editing configs and moving files.',
    note: 'SFTP, ProxyJump, port forwarding and the extended terminal ship in 0.3.0 from the repository; the RuStore build is 0.1.2 as of the site build date.',
    items: [
      {
        icon: 'terminal',
        index: '01',
        tag: 'ssh · pty',
        title: 'Interactive terminal',
        text: 'VT100/xterm with bash, zsh, htop, vim, nano and less. On-screen Ctrl, Alt, Esc and Tab keys, arrow keys, copy and paste.',
      },
      {
        icon: 'folder',
        index: '02',
        tag: 'sftp · 0.3',
        title: 'SFTP file manager',
        text: 'Browse directories, create folders, rename, delete and change permissions. Uploads and downloads go through the system file picker with progress and a transfer queue.',
      },
      {
        icon: 'key',
        index: '03',
        tag: 'auth',
        title: 'Password and SSH keys',
        text: 'PEM and OpenSSH keys with passphrase, file import, password login. Keys and passwords are encrypted before they reach the app database.',
      },
      {
        icon: 'jump',
        index: '04',
        tag: 'proxyjump',
        title: 'Jump host',
        text: 'Connect through a saved gateway server. Both host keys are verified — the gateway and the target; nested chains are not supported.',
      },
      {
        icon: 'ports',
        index: '05',
        tag: 'forwarding',
        title: 'Port forwarding',
        text: 'Local and remote forwarding. Tunnels listen on 127.0.0.1 by default and are closed together with the SSH session.',
      },
      {
        icon: 'layers',
        index: '06',
        tag: 'sessions',
        title: 'Multiple sessions',
        text: 'Keep several connections open, switch between them and reconnect after a dropped link without losing the server list.',
      },
      {
        icon: 'snippet',
        index: '07',
        tag: 'local',
        title: 'Snippets and history',
        text: 'Frequent commands are one tap away. Input history is off by default, stored encrypted and never intercepts arrows in full-screen TUI apps.',
      },
      {
        icon: 'shield',
        index: '08',
        tag: 'keystore',
        title: 'Credential protection',
        text: 'Android Keystore with AES-256-GCM, SHA-256 host key verification, a warning when a server key changes, and secret-free config export.',
      },
    ],
  },
  screenshots: {
    kicker: '02 — Screenshots',
    title: 'How it looks on a phone',
    lead: 'Real screenshots from the project repository. Click any shot to open it larger and browse the gallery.',
    hint: 'Click a screenshot to enlarge',
    allLink: 'All screenshots in the repository',
    items: [
      {
        id: 'dashboard',
        title: 'Servers',
        caption: 'Search, groups, favourites and adding your first connection.',
        alt: 'SSH Mobile server list screen',
      },
      {
        id: 'terminal',
        title: 'Key-based terminal',
        caption: 'Private key login, connection status and on-screen keys.',
        alt: 'SSH Mobile terminal with a shell prompt',
      },
      {
        id: 'new-server',
        title: 'New server',
        caption: 'Password or key, passphrase, file import — on a single screen.',
        alt: 'Add server form with an SSH key and passphrase',
      },
      {
        id: 'sessions',
        title: 'Sessions',
        caption: 'Several concurrent connections with session uptime.',
        alt: 'Active SSH sessions screen',
      },
      {
        id: 'htop',
        title: 'htop',
        caption: 'Full-screen utilities behave like a desktop terminal.',
        alt: 'htop running inside the SSH Mobile terminal',
      },
      {
        id: 'vim',
        title: 'vim',
        caption: 'Edit configs on the server with the mobile key row.',
        alt: 'vim inside the SSH Mobile terminal',
      },
      {
        id: 'reconnect',
        title: 'Reconnect',
        caption: 'A clear message on a dropped link and a one-tap retry.',
        alt: 'Connection recovery dialog in SSH Mobile',
      },
    ],
    lightbox: {
      close: 'Close',
      prev: 'Previous screenshot',
      next: 'Next screenshot',
      counter: 'of',
    },
  },
  advantages: {
    kicker: '03 — Advantages',
    title: 'Built for Android, not for a browser',
    lead: 'The app is written in Kotlin and Jetpack Compose. No WebView wrapper, no heavy SDKs, no background telemetry.',
    items: [
      {
        title: 'Native Android',
        text: 'Kotlin, Jetpack Compose and the SSHJ transport. It behaves like a system app: dark theme, gestures, on-screen keys and predictable keyboard handling.',
      },
      {
        title: 'Russian and English',
        text: 'Two interface languages with instant switching, no restart. Every string, dialog and error message is localised.',
      },
      {
        title: 'Made for small screens',
        text: 'Ctrl/Alt/Esc/Tab row, monospace font choice, text size and four terminal colour schemes — tuned for one hand and a phone keyboard.',
      },
      {
        title: 'Local data only',
        text: 'No accounts, analytics, ad SDKs or cloud sync. Android backup is disabled and the data is removed together with the app.',
      },
    ],
  },
  security: {
    kicker: '04 — Security',
    title: 'Credentials never leave the device',
    lead: 'The threat model and implementation details live in the project docs. Below is what you can verify in the code and on a device.',
    items: [
      {
        title: 'Android Keystore',
        text: 'Passwords, private keys and passphrases are encrypted with a key created in Android hardware-backed storage and never exported. Decryption happens only for an active connection.',
        tag: 'key · non-exportable',
      },
      {
        title: 'AES-256-GCM',
        text: 'Authenticated symmetric encryption: tampering is detected on decrypt. Secrets never reach logs and are excluded from backups.',
        tag: 'aead',
      },
      {
        title: 'Host key verification',
        text: 'The first connection shows the server SHA-256 fingerprint. A changed key requires explicit confirmation — including connections through a jump host.',
        tag: 'tofu · sha-256',
      },
      {
        title: 'Controlled export',
        text: 'Config export contains servers and settings but no passwords, private keys or passphrases. Import lets you save a server and fill credentials later.',
        tag: 'no secrets',
      },
    ],
    absenceTitle: 'What the app does not have',
    absence: [
      'analytics or crash reporting',
      'advertising and tracking SDKs',
      'cloud sync or accounts',
      'clipboard reads without a user action',
    ],
    link: 'Security documentation',
  },
  start: {
    kicker: '05 — Getting started',
    title: 'Three steps to your first connection',
    lead: 'All you need is a phone and access to any SSH server. There is no public demo account by design — spin up a local test server instead.',
    steps: [
      {
        step: '01',
        title: 'Install the app',
        text: 'Open the RuStore card and install the current version. Building from source requires Android Studio and JDK 17.',
        code: 'git clone https://github.com/mars485/ssh-mobile',
      },
      {
        step: '02',
        title: 'Add a server',
        text: 'Enter a display name, host, port and user. Pick password auth or paste a private key with an optional passphrase.',
        code: 'host: 192.0.2.10  port: 22  user: deploy',
      },
      {
        step: '03',
        title: 'Connect',
        text: 'Check the server SHA-256 fingerprint and accept it. From there it is a regular terminal: commands, htop, editors and SFTP transfers.',
        code: 'SHA256:9f2c…a71b accepted',
      },
    ],
    cta: 'Open RuStore',
    note: 'Repository APKs are release builds. For testing, use your own sshd — see docs/TESTING.md.',
  },
  changelog: {
    kicker: '06 — Changelog',
    title: 'What changed',
    lead: 'The full history is maintained in the repository CHANGELOG.md.',
    link: 'Open CHANGELOG.md',
    items: [
      {
        version: '0.3.0',
        date: '8 November 2026',
        badge: 'in RuStore review',
        highlight: true,
        items: [
          'SFTP: file manager, SAF uploads and downloads, transfer queue, permissions.',
          'Command snippets and encrypted local history, font choice and four colour schemes.',
          'Jump host, local and remote port forwarding.',
          'Config export and import without passwords or keys.',
        ],
      },
      {
        version: '0.1.2',
        date: '29 October 2026',
        badge: 'on RuStore',
        highlight: false,
        items: [
          'Fixed a crash when opening the add-server screen after localisation.',
          'SSH key import through the system file picker works again.',
          'Verified on HONOR Android 14 and the Android 15 emulator.',
        ],
      },
      {
        version: '0.1.1',
        date: '29 October 2026',
        badge: '',
        highlight: false,
        items: [
          'Russian localisation and language switching without a restart.',
          'The server form is no longer covered by the on-screen keyboard.',
          'RuStore assets, privacy policy and licenses.',
        ],
      },
      {
        version: '0.1.0',
        date: '29 October 2026',
        badge: '',
        highlight: false,
        items: [
          'First MVP: server manager, password and SSH key auth, PTY terminal.',
          'Host key verification, Android Keystore, multiple sessions, reconnect.',
          '40 automated tests; verified on Android 14 and 15.',
        ],
      },
    ],
  },
  faq: {
    kicker: '07 — FAQ',
    title: 'Frequently asked',
    lead: 'Missing an answer? Open an issue in the repository — it is the fastest support channel.',
    items: [
      {
        q: 'Is the app free?',
        a: 'Yes. SSH Mobile is free, with no ads, in-app purchases or subscriptions. The source code is open on GitHub under the Apache-2.0 license.',
      },
      {
        q: 'Do I need my own server?',
        a: 'Yes. The app connects to any SSH server: a VPS, a home server, a Raspberry Pi or a router. There is no public demo server — docs/TESTING.md explains how to run a local sshd for testing.',
      },
      {
        q: 'Which authentication methods are supported?',
        a: 'Password and private keys in PEM or OpenSSH format, optionally with a passphrase. Paste a key as text or import it from a file through the system file picker.',
      },
      {
        q: 'Is any data sent anywhere?',
        a: 'No. There is no analytics, crash reporting or tracking. Connections go only to servers you add yourself, and servers, keys and host key fingerprints stay on the device.',
      },
      {
        q: 'Are SFTP and port forwarding available?',
        a: 'Yes, in 0.3.0: an SFTP file manager with a transfer queue, jump host, and local plus remote port forwarding. At the time of writing, RuStore serves 0.1.2 while 0.3.0 is in review; you can build it from the repository.',
      },
      {
        q: 'What is the minimum Android version?',
        a: 'Android 10 or newer. The app has been verified on a HONOR device with Android 14 and on the Android 15 emulator.',
      },
      {
        q: 'Can I move my servers to a new phone?',
        a: 'Yes, through config export and import. Passwords, private keys and passphrases are not included — you will need to enter them again. There is no cloud sync.',
      },
      {
        q: 'Where do I report a bug or a vulnerability?',
        a: 'Bugs go to GitHub Issues. For vulnerabilities follow docs/SECURITY.md; never attach real passwords or private keys.',
      },
    ],
  },
  footer: {
    tagline: 'A native SSH client for Android. Open source, local data, no ads.',
    product: 'Product',
    docs: 'Documents',
    links: [
      { label: 'GitHub', href: site.repo },
      { label: 'RuStore', href: site.rustore },
      { label: 'Issues', href: site.issues },
      { label: 'CHANGELOG', href: site.changelog },
      { label: 'Privacy policy', href: site.privacy },
      { label: 'Licenses', href: site.licenses },
      { label: 'Security', href: site.security },
    ],
    madeBy: 'Built by IT GROUP',
    rights: '© 2026 IT GROUP. The app is distributed under the Apache-2.0 license.',
    updated: 'RuStore status checked',
  },
  cta: {
    title: 'Your server, whenever you need it',
    text: 'Install SSH Mobile from RuStore or build the APK from source and connect to your servers from your phone.',
    note: 'Android 10+ · free · no ads, no trackers',
  },
}

export const content: Record<Lang, Dict> = { ru, en };

export function getContent(lang: Lang): Dict {
  return content[lang];
}
