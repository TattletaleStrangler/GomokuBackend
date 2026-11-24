# Backend — сервер для сетевой игры в «Гомоку»

**Java 21 + Spring Boot 4.2 + PostgreSQL 17 + Flyway + Docker.**
Техническое задание — [`../TZ_BACKEND.md`](../TZ_BACKEND.md), действия владельца —
[`../USER_ACTION.md`](../USER_ACTION.md), состояние клиента — [`../Gomoku/README.md`](../Gomoku/README.md).

## Что реализовано (MVP 0.3.0 по ТЗ)

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
| Партии | ходы с номером (`ply`), отказы на чужой ход/занятую клетку/неверный номер, сдача, выход |
| Часы | серверные, инкремент после хода, живые значения в снимке, просрочка закрывается планировщиком |
| Отмена хода | запрос в первые 2 с после своего хода, решение соперника за 3 с, лимит 3, заморозка часов отвечающего, призрак снятого камня (`V2__undo.sql`) |
| Реальное время | `/ws/game`: аутентификация в рукопожатии, `GAME_STATE` при подключении, `MOVE_APPLIED`, `GAME_FINISHED`, `OPPONENT_JOINED`, `UNDO_REQUESTED`/`UNDO_DECIDED`/`UNDO_APPLIED` |
| Переподключение | любой снимок (`GET /games/{id}` или событие сокета) содержит всё состояние и `serverTime` |
| Тесты | 101 тест: правила, часы, rate limit, аккаунты, партии, отмена хода, выбор цвета, матчмейкинг, Elo, сокет — всё на настоящем PostgreSQL через Testcontainers |

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