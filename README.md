# Backend — сервер для сетевой игры в «Гомоку»

**Java 21 + Spring Boot 4.2 + PostgreSQL 17 + Flyway + Docker.**
Техническое задание — [`../TZ_BACKEND.md`](../TZ_BACKEND.md), действия владельца —
[`../USER_ACTION.md`](../USER_ACTION.md), состояние клиента — [`../Gomoku/README.md`](../Gomoku/README.md).

## Что уже готово (каркас)

- Gradle-проект с wrapper 9.5.1, Java 21, Spring Boot 4.2.0;
- схема БД `src/main/resources/db/migration/V1__init.sql` — `users`, `refresh_tokens`,
  `games`, `moves` с CHECK-ограничениями; Flyway применяет её при старте, Hibernate
  только проверяет (`ddl-auto=validate`), схему никто, кроме Flyway, не меняет;
- `application.yml` — только переменные окружения, секретов в файлах нет;
- `Dockerfile` (multi-stage, запуск не от root) и `docker-compose.yml`
  (app + postgres, healthcheck, `restart: unless-stopped`), значения — из `.env`;
- интеграционный тест, поднимающий приложение на настоящем PostgreSQL через
  Testcontainers; без запущенного Docker он **пропускается**, а не падает.

Дальше по плану: аутентификация → правила игры на Java с золотыми кейсами → комнаты и
партии → WebSocket-уведомления → серверные часы.

## Запуск

Нужны **JDK 21** и **запущенный Docker**.

```powershell
# 1. конфигурация
Copy-Item .env.example .env      # заполнить POSTGRES_PASSWORD и JWT_SECRET

# 2. весь стенд: приложение + база, миграции применяются при старте
docker compose up --build        # живость: http://localhost:8080/actuator/health

# 3. только сборка и тесты
$env:JAVA_HOME = '<путь к JDK 21>'   # например C:\Users\<вы>\.jdks\openjdk-21.0.2
.\gradlew test
```

Gradle должен работать на JDK 21 или новее: компилятор закреплён через
`options.release = 21`, поэтому сборка не зависит от версии JDK в PATH.

Проверка compose-файла без запуска:

```powershell
docker compose config
```

Сценарии проверки лежат в `../TZ_BACKEND.md` §13 (локально) и §14 (сервер).

## Лицензия

GPL-3.0-or-later — как у клиента.