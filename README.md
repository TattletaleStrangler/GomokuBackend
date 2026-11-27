# Backend — сервер для сетевой игры в «Гомоку»

**Java 21 + Spring Boot 4.2 + PostgreSQL 17 + Flyway + Docker.**
Техническое задание — [`../TZ_BACKEND.md`](../TZ_BACKEND.md), действия владельца —
[`../USER_ACTION.md`](../USER_ACTION.md), состояние клиента — [`../Gomoku/README.md`](../Gomoku/README.md).

## Что реализовано (MVP 0.14.0 по ТЗ)

| Блок | Что есть |
|---|---|
| Аккаунты | регистрация/вход по никнейму и паролю, BCrypt, `/me` со статистикой |
| Сессии | JWT access (15 мин) + refresh-токен в БД только хешем, ротация, отзыв, повторное использование токена завершает все сессии |
| Защита | rate limit на `/auth/*` (на адрес и на никнейм), единый формат ошибок с кодами, фиктивный хеш против перебора по времени |
| Аккаунт | удаление из приложения с обезличиванием (партии соперника остаются) |
| Правила | гомоку и рендзю на Java: overline, 4-4, 3-3, приоритет пятёрки, ничья — те же золотые кейсы, что у клиента |
| Комнаты | создание с правилом, контролем времени (30+30 / 15+10 / 3+2 / без часов) и цветом создателя (`BLACK`/`WHITE`/`ANY`), вход по 6-символьному коду; при `ANY` цвет выбирает вошедший |
| Матчмейкинг | публичные комнаты (`visibility=PUBLIC`), список открытых игр (`GET /games/lobby`), автоподбор по правилу и контролю (`POST /games/quick`) под advisory-lock, TTL публичной комнаты 15 минут |
| Рейтинг | Elo быстрых игр: K=32, старт 1200, дельта и текущий рейтинг в снимке; полоса подбора начинается с ±200 и расширяется на +50 каждые 5 с ожидания до ±600; комнаты по коду рейтинг не трогают (`V5__rating.sql`) |
| История | `GET /games/history` — завершённые партии игрока, новые первыми, с соперником, результатом и дельтой рейтинга; ходы для повтора отдаёт обычный `GET /games/{id}` |
| Лидеры | `GET /leaderboard` — игроки хотя бы с одной завершённой партией, по рейтингу: место, ник, рейтинг и запись побед/поражений/ничьих одним запросом |
| Реванш | `POST /games/{id}/rematch` — приватная комната с теми же правилом и контролем и обменом цветов; снимок завершённой партии несёт `rematchGameId`, повторный вызов возвращает ту же комнату, а соперник ею же и соглашается |
| Модерация | `POST /games/{id}/block` — больше не сводить эту пару (учитывают и лобби, и автоподбор, в обе стороны); `GET /blocks` и `DELETE /blocks/{id}` — свой чёрный список с разблокировкой (одна жалоба на партию, до 20 в сутки); `POST /games/{id}/report {reason}` — жалоба с игрой и причиной (`V7__moderation.sql`) |
| Ничья | `POST /games/{id}/draw-offer` и `POST /games/{id}/draw-decision {accept}` — протокол как у отмены хода: 10 секунд на ответ, 3 попытки на игрока, часы отвечающего стоят; согласие завершает партию как `DRAW`/`AGREE` и считается в рейтинг (`V8__draw.sql`) |
| Админка | `/admin` (Thymeleaf, вход по нику и паролю аккаунта с ролью `ADMIN`): очередь жалоб с партией и историей аккаунта, решения (`WARN`/`BAN_RANKED`/`BAN_ACCOUNT`/`UNBAN` и другие) с журналом `admin_actions`, страницы ошибок сервера и падений клиента; первый админ выдаётся переменной `ADMIN_NICKNAME` (`V9__admin.sql`) |
| Ошибки | 5xx пишутся в `error_events` вместе с `requestId` (он же в теле ответа и заголовке `X-Request-Id`); клиентские падения принимает `POST /api/v1/crash-reports` и группирует по отпечатку. Ошибки самого клиента — несуществующий адрес, неверный метод, строка вместо UUID — отдают 404/405 и в `error_events` не попадают |
| Логи вызовов | `RequestLogFilter` пишет строку на каждый вызов API: метод, путь с параметрами, статус, время, ник, `requestId`; тела и заголовки не логируются; `app.log.requests=false` выключает (тесты так и делают). Плюс `GameService` пишет, что решил подбор: создал комнату или вошёл в чужую, с рейтингом и настройками |
| Защита от флуда | Лимиты на аккаунт и адрес: игровые изменяющие вызовы 10/мин, ходы 30/мин, отчёты о падениях 20/час на IP, вход в админку 5 неудач/15 мин; тело запроса ≤ 64 КБ; один сокет на аккаунт; ошибки и падения хранятся 30 дней (`RequestLimitFilter`, `RequestSizeFilter`, `AuthRateLimitFilter`) |
| Страницы для Play | `/legal/privacy` и `/legal/delete-account` — открытые HTML-страницы: политика конфиденциальности (что собирается, сроки, права) и способ удалить аккаунт; контакт — `LEGAL_CONTACT_EMAIL` (`PLAY_RELEASE.md`) |
| Партии | ходы с номером (`ply`), отказы на чужой ход/занятую клетку/неверный номер, сдача, выход |
| Часы | серверные, инкремент после хода, живые значения в снимке, просрочка закрывается планировщиком |
| Отмена хода | запрос в первые 2 с после своего хода, решение соперника за 3 с, лимит 3, заморозка часов отвечающего, призрак снятого камня (`V2__undo.sql`) |
| Реальное время | `/ws/game`: аутентификация в рукопожатии, `GAME_STATE` при подключении, `MOVE_APPLIED`, `GAME_FINISHED`, `OPPONENT_JOINED`, `UNDO_REQUESTED`/`UNDO_DECIDED`/`UNDO_APPLIED` |
| Переподключение | любой снимок (`GET /games/{id}` или событие сокета) содержит всё состояние и `serverTime` |
| Тесты | 150 тестов: правила, часы, rate limit, аккаунты, партии, отмена хода, выбор цвета, матчмейкинг, Elo, история, сокет — всё на настоящем PostgreSQL через Testcontainers |

## Запуск

Нужны **JDK 21** и **запущенный Docker**.

```powershell
# 1. конфигурация
Copy-Item .env.example .env      # заполнить POSTGRES_PASSWORD и JWT_SECRET

# 2. весь стенд: приложение + база, миграции применяются при старте
docker compose up --build        # живость: http://localhost:8080/actuator/health
                                 # документация API: http://localhost:8080/swagger-ui.html

# 3. только сборка и тесты
$env:JAVA_HOME = '<путь к JDK 21>'   # например C:\Users\<вы>\.jdks\openjdk-21.0.2
.\gradlew test
```

Gradle должен работать на JDK 21 или новее: компилятор закреплён через
`options.release = 21`.

Проверка compose-файла без запуска: `docker compose config`.

## Развёртывание

Образ для прода собирает GitHub Actions (`.github/workflows/image.yml`): сначала прогон
150 тестов, затем публикация в GHCR — `ghcr.io/tattletalestrangler/gomokubackend` с
тегами `latest`, версией сервера из `build.gradle.kts` и `sha-<короткий хеш>`. Образ
без зелёных тестов не публикуется.

```bash
# на сервере: только запуск готового образа — ни JDK, ни Gradle, ни сборки
git clone https://github.com/TattletaleStrangler/GomokuBackend.git /opt/gomoku
cd /opt/gomoku
cp .env.example .env      # POSTGRES_PASSWORD, JWT_SECRET, ADMIN_NICKNAME,
                          # LEGAL_CONTACT_EMAIL, SERVER_PORT — порт, открытый на хостинге
docker compose -f docker-compose.prod.yml up -d
curl http://127.0.0.1:8080/actuator/health
```

`docker-compose.prod.yml` рассчитан на контейнерный хостинг (Portainer на
dockerhosting.ru, Docker VPS и подобное): база наружу не публикуется, память
ограничена (`APP_MEM_LIMIT`/`DB_MEM_LIMIT`), Swagger выключен, версия образа задаётся
`IMAGE_TAG`. Пошаговая инструкция для Portainer — [`../USER_ACTION.md`](../USER_ACTION.md)
§13, требования к серверу — [`../TZ_BACKEND.md`](../TZ_BACKEND.md) §14.

## Логи

Каждый вызов API оставляет одну строку — её пишет `common/RequestLogFilter`:

```
10:41:07.331 INFO  c.o.g.b.common.RequestLogFilter : GET /api/v1/games/lobby?rule=RENJU -> 200 (11 ms) user=ann requestId=-
10:41:09.002 INFO  c.o.g.b.game.GameService       : quick match: ann (rating 1200) opened room K7M4QP for RENJU/BLITZ: nothing waiting matched
10:41:12.480 INFO  c.o.g.b.game.GameService       : quick match: bob joined room K7M4QP, which waited 3 s
```

Зачем: до 0.14.0 неудачный поиск соперника не оставлял следов вообще. Эндпоинт не
ошибается — он открывает комнату и ждёт, — а отказы игровых вызовов это 4xx, тогда как
админка собирает только 5xx. Теперь видно, отправил ли телефон запрос и что вернулось.

Что попадает в строку: метод, путь **с параметрами** (там живут фильтры лобби), статус,
длительность, ник из токена (или `-`, если вызов отклонён до аутентификации) и `requestId`.
**Тела запросов и заголовки не логируются** — в них пароли и токены.

Где смотреть:

```powershell
docker compose -f docker-compose.prod.yml logs -f app          # SSH
# Portainer: Containers → gomoku-app-1 → Logs
docker compose -f docker-compose.prod.yml logs app | Select-String "quick match"
```

Выключить: `app.log.requests=false` (тесты выключают его сами, иначе 150 тестов
заваливают консоль строками).

Что смотреть при жалобе «не находит соперника»: строки `quick match:` с обоих устройств.
Два «opened room … for RENJU/BLITZ» и «… for FREESTYLE/UNTIMED» — разные настройки на
телефонах; два «opened room» с одинаковыми настройками и разными `rating` — разошлась
полоса подбора (±200, растёт на 50 каждые 5 с ожидания до ±600). Ещё пару могут не свести
блокировка и бан — их видно в админке и запросами из `USER_ACTION.md` §13.

## Проверить руками

```powershell
$base = 'http://localhost:8080/api/v1'

# два игрока
$a = Invoke-RestMethod "$base/auth/register" -Method Post -ContentType 'application/json' `
     -Body '{"nickname":"ann","password":"password-123"}'
$b = Invoke-RestMethod "$base/auth/register" -Method Post -ContentType 'application/json' `
     -Body '{"nickname":"bob","password":"password-123"}'

# комната и вход по коду
$game = Invoke-RestMethod "$base/games" -Method Post -ContentType 'application/json' `
     -Headers @{ Authorization = "Bearer $($a.accessToken)" } `
     -Body '{"rule":"RENJU","timeControl":"BLITZ"}'
"код комнаты: $($game.code)"

$joined = Invoke-RestMethod "$base/games/join" -Method Post -ContentType 'application/json' `
     -Headers @{ Authorization = "Bearer $($b.accessToken)" } `
     -Body "{`"code`":`"$($game.code)`"}"

# ход чёрных
Invoke-RestMethod "$base/games/$($game.gameId)/moves" -Method Post -ContentType 'application/json' `
     -Headers @{ Authorization = "Bearer $($a.accessToken)" } `
     -Body '{"ply":1,"x":7,"y":7}'
```

Сокет: `ws://localhost:8080/ws/game` с заголовком `Authorization: Bearer <access>`;
на подключении приходит `GAME_STATE`, дальше — события партии.

## Вне объёма MVP

Матчмейкинг, рейтинг и таблица лидеров, чат, друзья, зрители, push-уведомления,
история партий онлайн в клиенте, e-mail и OAuth — по ТЗ часть 2, следующий этап.

## Лицензия

GPL-3.0-or-later — как у клиента.