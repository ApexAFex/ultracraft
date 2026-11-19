# Вход через Microsoft: как получить client id

Клиенту нужен **client id** приложения, зарегистрированного в Microsoft Entra ID
(бывший Azure AD). Без него device-code вход не начать.

Придумать его нельзя: это GUID, который Microsoft создаёт при регистрации приложения.
Выдуманный id даст `AADSTS700016` («приложение не найдено»).

**Client id — не секрет.** Он публичный по определению: попадает в ссылки авторизации
и в исходники лаунчеров. Секретом был бы `client secret`, но для device code он не нужен.

---

## Ловушка: личный аккаунт не может регистрировать приложения

Если войти на <https://portal.azure.com> или <https://entra.microsoft.com> **личным**
аккаунтом Microsoft (outlook.com, hotmail.com, live.com), портал молча посадит тебя
в служебный тенант «Microsoft Services» (`f8cdef31-a31e-4b4a-93e4-5f571e91255a`),
у которого **нет каталога**. Любая попытка что-то создать заканчивается ошибкой:

```
AADSTS50020: User account … does not exist in tenant 'Microsoft Services' and cannot
access the application '74658136-14ec-4630-ad9b-26e160ff0fc6' (ADIbizaUX)…
```

`74658136-…` — это идентификатор **самого портала Azure**, а не твоего приложения.
Ошибка означает «у личного аккаунта нет рабочего каталога», а не «ты что-то сделал не так».

**Вывод: для регистрации приложения нужен рабочий или учебный аккаунт** (work/school),
то есть аккаунт в каком-нибудь тенанте Entra ID.

### Что можно попробовать, прежде чем сдаваться

Ошибка `AADSTS16000` / `AADSTS50020` в портале иногда означает не «нет каталога»,
а «браузер держит старую сессию в служебном тенанте». Порядок действий:

1. Выйти из всех сессий: <https://account.microsoft.com> → «Выйти».
2. Почистить cookie для `login.microsoftonline.com`, `portal.azure.com`, `entra.microsoft.com`.
3. Открыть <https://entra.microsoft.com> в приватном окне и войти заново.

Если пустило и виден каталог — регистрируй приложение. Если снова та же ошибка,
значит каталога действительно нет: нужен любой тенант (см. ниже).

---

## Как получить тенант (и client id)

### Вариант 1 — рабочий/учебный аккаунт

Если есть аккаунт от работы, вуза или школы (`you@company.com`, `you@university.edu`) —
это самый быстрый путь. Дальше: <https://entra.microsoft.com> → **App registrations** →
**New registration** → тип «Accounts in any organizational directory and personal Microsoft
accounts» → **Register** → скопировать **Application (client) ID** →
**Authentication** → **Allow public client flows** = Yes → Save.

#### Названия в русском интерфейсе

| Английский интерфейс | Русский интерфейс |
|---|---|
| Microsoft Entra ID → App registrations | «Удостоверение» → **«Регистрация приложений»** |
| New registration | **«+ Новая регистрация»** |
| Name | **«Имя»** |
| Supported account types | **«Поддерживаемые типы учетных записей»** |
| Accounts in any organizational directory and personal Microsoft accounts | **«Учетные записи в любом каталоге организации и личные учетные записи Майкрософт»** (третий пункт) |
| Redirect URI | **«URI перенаправления»** — оставить пустым |
| Register | **«Зарегистрировать»** |
| Overview | **«Обзор»** |
| Application (client) ID | **«Идентификатор приложения (клиента)»** ← нужен именно он |
| Directory (tenant) ID | «Идентификатор каталога (клиента)» — **не** он |
| Object ID | «Идентификатор объекта» — **не** он |
| Authentication | **«Проверка подлинности»** |
| Allow public client flows | **«Разрешить потоки общедоступного клиента»** → **«Да»** |
| Save | **«Сохранить»** |

### Вариант 2 — создать свой тенант

Два способа, оба бесплатные:

- **Azure free account** (<https://azure.microsoft.com/free>): регистрация создаёт настоящий
  каталог Entra ID и рабочий аккаунт вида `admin@твойтенант.onmicrosoft.com`.
  Требуется банковская карта для проверки личности; на бесплатном тарифе списаний нет,
  ресурсы можно не создавать вообще — нужен только каталог.
- **«Create a tenant»** в <https://entra.microsoft.com>: иногда доступно личному аккаунту
  без карты, но у части пользователей вход в портал блокируется ошибкой `AADSTS50020`.

После появления тенанта — те же шаги регистрации приложения.

### Вариант 3 — Microsoft 365 Developer Program: больше не подходит

Раньше эта программа давала бесплатный тенант E5 любому желающему. Сейчас доступ только по
квалификации: Visual Studio Professional/Enterprise, участие в ISV Success / MAICPP,
либо Premier/Unified Support. Кроме того, при создании песочницы теперь обязательно
привязывается биллинговый аккаунт с активной подпиской Azure. Для частного лица
с личным аккаунтом путь фактически закрыт —
см. [официальный FAQ](https://learn.microsoft.com/en-us/office/developer-program/microsoft-365-developer-program-faq).

---

## Куда его положить

```powershell
$env:MCPLUS_CLIENT_ID = "<скопированный id>"
.\gradlew.bat :app:run --args="login"
```

Или разово: `--client-id <id>`. Если вход жалуется на неверный тенант, попробуй
`--tenant common` (по умолчанию используется `consumers`).

## Разрешение Mojang (получено 19.11.2026)

`api.minecraftservices.com` пускает только приложения из allow list, и попасть туда можно
лишь по заявке <https://aka.ms/mce-reviewappid>. Заявку рассмотрели 19.11.2026 и одобрили:
id приложения добавлен в список. **Код клиента при этом не менялся** — упирался именно
доступ, а не реализация.

В письме об одобрении стоит прочитать не только «approved»:

> We did not identify any block outcomes in this reviewed batch. …
> We do not provide consultation or troubleshoot API access.

То есть одобрение — это только строчка в allow list. Ни «почему 403», ни «почему не пускает
на сервер» они разбирать не будут.

До одобрения шаг `login_with_xbox` отвечал:

```
HTTP 403: Invalid app registration, see https://aka.ms/AppRegInfo
```

При этом Microsoft, Xbox Live и XSTS проходили успешно. Это подтверждают и независимые
реализации: у сторонних лаунчеров была та же стена до одобрения
([lighty-launcher](https://github.com/lighty-launcher/lightylauncherlib/blob/ca8c8c1bac47e20756e9fb875ecc245b9d6162c9/crates/auth/docs/microsoft.md),
[horizon](https://github.com/horizon-rust/horizon/blob/92b0ee1347b82d80e8a8faf6fe2206ad52fcd5d6/docs/microsoft-account.md)).

## Что проверено после одобрения

| Шаг | Чем подтверждён |
|---|---|
| Microsoft → Xbox Live → XSTS | `mcplus login` дошёл до конца |
| `login_with_xbox` | профиль `SashimiShrimpA`, `Java Edition есть` |
| Шифрование канала | сервер в `online-mode=true` принял вход |
| Подтверждение сессии у Mojang | в логе сервера настоящий UUID профиля |
| Отказ без входа | `--online` не указан → «Сервер требует вход через Microsoft» |

**Вход сразу после предыдущего может не пройти.** Сервер сессий несколько секунд не принимает
новую сессию того же профиля и отвечает киком `multiplayer.disconnect.unverified_username`.
Замер: после паузы 65 секунд первый вход проходит, два подряд — нет. Клиент повторяет вход до
трёх раз с паузой 5 секунд, так что руками ничего делать не надо.

## Где лежат токены

После успешного входа токены сохраняются в `.mcplus/account.json` (каталог в `.gitignore`).
Там refresh token — файл приватный, никому не передавай. Удалить: `mcplus logout`.
