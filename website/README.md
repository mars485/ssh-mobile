# Сайт SSH Mobile

Презентационный сайт Android-приложения SSH Mobile: тёмная тема, красный акцент IT GROUP, RU/EN,
статическая сборка. Раздел `website/` репозитория, исходники приложения не затрагиваются.

- Стек: Astro 5 + TypeScript + Tailwind CSS 4, статический вывод (`output: static`).
- Домен: `https://ssh.itgsystem.ru` (задаётся через `SITE_URL` при сборке).
- Языки: `/` — русский, `/en/` — английский, `hreflang` + `x-default` в `<head>`.

## Структура

```
website/
├─ public/                 # статика: скриншоты WebP, иконки, OG-обложка, manifest
├─ scripts/
│  └─ optimize-shots.mjs   # пересборка WebP из ../docs/screenshots + иконки
├─ src/
│  ├─ components/          # Header, Hero, Features, Screenshots, Security, …
│  ├─ i18n/content.ts      # весь контент RU/EN в одном типизированном объекте
│  ├─ pages/               # index.astro, en/index.astro, 404.astro, robots.txt.ts
│  └─ styles/global.css    # тема Tailwind 4 (@theme), компонентные классы
├─ astro.config.mjs        # site, sitemap, tailwind
├─ nginx.conf              # статика + security headers + кэш
├─ Dockerfile              # node:22-alpine → nginx:1.27-alpine, порт 8080
└─ docker-compose.yml      # запуск за существующим Traefik (сеть itg-proxy)
```

## Локальная разработка

```bash
cd website
npm ci
npm run dev        # http://localhost:4321
npm run build      # dist/
npm run preview    # preview собранного dist
```

Требования: Node.js 20+ (проверено на Node 26). Сборка не требует backend и внешних API.

## Обновление контента

| Что | Где |
| --- | --- |
| Тексты, возможности, FAQ, changelog, ссылки | `src/i18n/content.ts` (`site`, `ru`, `en`) |
| Версия в RuStore / ожидающая версия | `site.versionInStore`, `site.versionPending` |
| Скриншоты | `docs/screenshots/*.png` → `node scripts/optimize-shots.mjs` |
| OG-обложка | `public/og-cover.png` (1200×630) |
| Цвета и типографика | `src/styles/global.css` (`@theme`) |

Скриншоты в галерее берутся только из `docs/screenshots/`: `dashboard-ru.png` (dashboard),
`terminal-key-auth.png` (terminal), `server-key.png` (new-server), `sessions.png`, `htop.png`,
`vim.png`, `host-key.png` (reconnect). Hero использует `htop.png` с обрезкой верхней панели
(чтобы не показывать тестовый хост). `settings-ru.png` намеренно не используется — на экране
видна сборка `0.1.0-debug`.

## Проверка перед публикацией

1. `npm run build` — сборка без ошибок.
2. Открыть `dist/index.html` и `dist/en/index.html` на ширинах 320 / 360 / 375 / 390 / 414 / 768 / 1024 / 1440:
   нет горизонтального скролла (`document.documentElement.scrollWidth === window.innerWidth`),
   нет ошибок JS в консоли, меню и лайтбокс работают.
3. Ссылки: RuStore (`dev.sshmobile`), GitHub, политика конфиденциальности, лицензии, CHANGELOG — отвечают 200.
4. SEO: `title` ≤ 60, `description` 140–170 символов, canonical, OG, JSON-LD `SoftwareApplication` + `FAQPage`,
   `sitemap-index.xml`, `robots.txt`, `hreflang`.
5. Актуальность: в карточке RuStore указана версия, которая реально опубликована (на 08.11.2026 — `0.1.2`,
   `0.3.0` на модерации). Не писать «доступно в RuStore» про неопубликованную версию.

## Локальные метрики (08.11.2026)

Собрано и проверено в контейнере (nginx, gzip, security headers), Lighthouse 12:

| Режим | Performance | Accessibility | Best practices | SEO | LCP | CLS | TBT | Вес страницы |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Desktop | 100 | 100 | 100 | 100 | 0,5 с | 0 | 0 мс | 75 KiB |
| Mobile | 99 | 100 | 100 | 100 | 1,7 с | 0 | 100 мс | 98 KiB |

Gzip: HTML 91 КБ → 17 КБ. Проверено: нет горизонтального скролла на 320 / 390 / 414 / 768 / 1024 / 1280 / 1440,
ноль ошибок JS в консоли, меню и лайтбокс работают (Escape, стрелки, клик по фону), изображения
с `width`/`height` и `alt`. Полевых данных CrUX нет — цифры лабораторные, на продовом домене их надо перепроверить.

## Docker

```bash
cd website
docker build -t itg-ssh-site:latest .
docker run --rm -p 8080:8080 itg-ssh-site:latest
curl -sI http://127.0.0.1:8080/ | head
```

Контейнер отдаёт статику на `8080`, healthcheck — `GET /healthz`. Заголовки безопасности и CSP
заданы в `nginx.conf`; HTML не кэшируется, `/_astro/` и `/shots/` — immutable на длинный срок.

## Развёртывание на VPS (за Traefik)

Существующая инфраструктура: VPS `195.208.61.169`, Traefik `itg-traefik` (провайдер Docker,
сеть `itg-proxy`, `exposedByDefault=false`, certresolver `letsencrypt` HTTP-01, middleware
`itg-security@file`).

```bash
ssh itg-vps
mkdir -p /opt/itg/ssh-site
# скопировать содержимое website/ (без node_modules и dist)
cd /opt/itg/ssh-site
docker compose config           # проверка конфигурации
docker compose up -d --build
docker compose ps
curl -sI https://ssh.itgsystem.ru/   # 200 + security headers
```

**DNS.** `ssh.itgsystem.ru` должен указывать A-записью на `195.208.61.169` (как `crm.` и `project.`)
до первого запуска: HTTP-01 challenge Let's Encrypt проходит только при живом DNS.
Запись добавляется в Cloudflare вручную и **только после подтверждения владельца**.

**Откат.** `docker compose down` в `/opt/itg/ssh-site` — сайт перестаёт отвечать, остальные сервисы
не затрагиваются. Роутер Traefik удаляется вместе с контейнером (labels), сертификат остаётся в `acme.json`.

Проверить после публикации:

```bash
curl -sI https://ssh.itgsystem.ru/ | grep -Ei 'HTTP/|content-security|strict-transport|content-type'
curl -s https://ssh.itgsystem.ru/robots.txt
curl -s https://ssh.itgsystem.ru/sitemap-index.xml | head -c 400
```

## Ограничения

- Отзывов, рейтингов и счётчиков загрузок на сайте нет: подтверждённых данных для них не было.
- Docker, AI, биометрия и мониторинг в список возможностей не включены — в приложении их нет.
- Полевых данных Core Web Vitals (CrUX) нет; в README фиксируются только локальные метрики сборки.
