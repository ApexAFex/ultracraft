# майн+ — совместимый клиент Minecraft: Java Edition

Своя реализация клиента на современном коде: официальный набор функций и доступ
к мультиплееру, без поддержки модов. Цель — **Java Edition 26.3, протокол 777**.

Проект не связан с Mojang и Microsoft. Код игры и её ассеты не распространяются:
клиент читает их из установки игрока.

## Статус: клиент входит на сервер

Сделано:

- **E1 — Server List Ping**: handshake → status → ping/pong, разбор ответа сервера.
- **E2 — вход на сервер**: handshake → login → configuration → play, `Join Game` разобран.

```
> .\gradlew.bat :app:run --args="join"
Сервер       127.0.0.1:25565
Профиль      mcplus (9667b029-256c-3a6a-bc5f-9b65783b13f4)
Протокол     777
Мир          minecraft:overworld
Измерения    minecraft:overworld, minecraft:the_nether, minecraft:the_end
Тип мира     id 0
Режим        выживание
Сущность     1
Дальность    обзор 4, симуляция 4
Игроков      до 10
Online-mode  нет
Плоский мир  да
Реестры      32: worldgen/biome, chat_type, trim_pattern, trim_material, …
Флаги        minecraft:vanilla
```

А сервер в это время пишет:

```
mcplus[/127.0.0.1:56602] logged in with entity id 1 at (2.5, -60.0, 3.5)
System chat: mcplus joined the game
```

Окна, мира и движения пока нет: клиент умеет входить и читать первые пакеты.

## Сборка и запуск

Нужен **JDK 21** для сборки (проверено на Temurin 21.0.13). Gradle ставится сам через wrapper.

```
.\gradlew.bat build                                   # компиляция + 21 тест
.\gradlew.bat :app:run --args="mc.hypixel.net"        # список игроков
.\gradlew.bat :app:run --args="join"                  # войти на локальный сервер
.\gradlew.bat :app:run --args="join 127.0.0.1:25565 --name Steve --verbose"
```

## Локальный сервер (оракул протокола)

Ванильный сервер 26.3 — эталон, с которым сверяется клиент. Первый запуск:

1. скачать `server.jar` для 26.3 с minecraft.net в `tools/server/`;
2. `.\tools\server-start.ps1`.

Скрипт сам создаёт `eula.txt` и `server.properties` (offline-mode, без whitelist),
находит JDK 25 и ждёт готовности. Каталог `tools/server/` в git не попадает.

**Важно:** сервер 26.3 требует **JDK 25** (более старый падает на `UnsupportedClassVersionError`),
а `white-list` у него по умолчанию включён — скрипт это выключает.

## Модули

| Модуль | Что внутри | Зависимости |
|---|---|---|
| `protocol` | VarInt/VarLong, строки, UUID, позиции, фрейминг с zlib-сжатием | нет |
| `net` | соединение, Server List Ping, вход (login/configuration), разбор `Join Game` | `protocol` |
| `app` | CLI `mcplus` (`ping`, `join`) | `net` |

Правило слоёв: `protocol` не знает ни про мир, ни про сеть, ни про рендер.

## Документы

- [docs/START-HERE.md](docs/START-HERE.md) — с чего начать, план, ловушки
- [docs/adr/0001-product-client.md](docs/adr/0001-product-client.md) — почему клиент, а не сервер
- [docs/adr/0002-stack-kotlin-jvm.md](docs/adr/0002-stack-kotlin-jvm.md) — почему Kotlin/JVM
- [docs/git-shpargalka.md](docs/git-shpargalka.md) — git, если понадобится

## Следующий шаг

E3: чанки. `Chunk Data and Update Light` → палитровые секции → NBT-реестр блоков →
ASCII-карта мира в консоли.
