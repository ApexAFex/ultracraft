# майн+ — совместимый клиент Minecraft: Java Edition

Своя реализация клиента на современном коде: официальный набор функций и доступ
к мультиплееру, без поддержки модов. Цель — **Java Edition 26.3, протокол 777**.

Проект не связан с Mojang и Microsoft. Код игры и её ассеты не распространяются:
клиент читает их из установки игрока.

## Статус

Работает первый вертикальный срез — **Server List Ping** к живому серверу:
handshake → status → ping/pong, разбор JSON-ответа, MOTD, замер пинга.

```
> .\gradlew.bat :app:run --args="mc.hypixel.net"
Сервер        mc.hypixel.net:25565
Версия        Requires MC 1.8 / 1.21 — протокол 777
Игроки        14700 / 200000
MOTD          Hypixel Network [1.8/26.3]
Пинг          162 мс
Наш протокол  777
```

## Сборка

Нужен **JDK 21** (проверено на Temurin 21.0.13). Gradle ставится сам через wrapper.

```
.\gradlew.bat build                     # компиляция + 15 тестов
.\gradlew.bat :app:run                  # ping 127.0.0.1:25565
.\gradlew.bat :app:run --args="play.example.com --json"
```

## Модули

| Модуль | Что внутри | Зависимости |
|---|---|---|
| `protocol` | VarInt/VarLong, строки, UUID, позиции, фрейминг с zlib-сжатием | нет |
| `net` | TCP-соединение, Server List Ping, разбор статуса | `protocol` |
| `app` | CLI `mcplus` | `net` |

Правило слоёв: `protocol` не знает ни про мир, ни про сеть, ни про рендер.

## Документы

- [docs/START-HERE.md](docs/START-HERE.md) — с чего начать, план на 14 дней, ловушки
- [docs/adr/0001-product-client.md](docs/adr/0001-product-client.md) — почему клиент, а не сервер
- [docs/adr/0002-stack-kotlin-jvm.md](docs/adr/0002-stack-kotlin-jvm.md) — почему Kotlin/JVM

## Следующий шаг

E2 по плану: login в offline-mode → состояние `configuration` (реестры) → `play` (Join Game).
