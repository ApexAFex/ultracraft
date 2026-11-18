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

---

## Три способа получить client id

### Способ 1 — твой рабочий/учебный аккаунт (самый быстрый)

Если есть аккаунт от работы, вуза или школы (`you@company.com`, `you@university.edu`) —
заходи им:

1. <https://entra.microsoft.com> → **App registrations** → **New registration**.
2. Name — `mcplus`.
3. Supported account types — **Accounts in any organizational directory and personal
   Microsoft accounts** (важно: именно этот вариант, иначе личный аккаунт Minecraft не пустят).
4. Redirect URI — пусто → **Register**.
5. Скопировать **Application (client) ID**.
6. **Authentication** → **Allow public client flows** → **Yes** → **Save**.

### Способ 2 — бесплатный тенант Microsoft 365 Developer Program

Даёт собственный рабочий аккаунт вида `ты@твойтенант.onmicrosoft.com`.
Условия программы Microsoft периодически меняет — если регистрация недоступна,
используй способ 3. После получения аккаунта — те же шаги, что в способе 1.

### Способ 3 — попросить того, у кого рабочий аккаунт есть

Client id публичен, поэтому его спокойно можно передать: приложение принадлежит тому,
кто его зарегистрировал, но идентификатор ничего не раскрывает. Достаточно, чтобы в
приложении был включён вариант «… and personal Microsoft accounts» и public client flows.

---

## Куда его положить

```powershell
$env:MCPLUS_CLIENT_ID = "<скопированный id>"
.\gradlew.bat :app:run --args="login"
```

Или разово: `--client-id <id>`. Если вход жалуется на неверный тенант, попробуй
`--tenant common` (по умолчанию используется `consumers`).

## Ограничение Mojang

Mojang ограничивает доступ к `api.minecraftservices.com`: новым приложениям нужно
запросить разрешение через форму <https://aka.ms/mce-reviewappid>. До одобрения шаг
`login_with_xbox` может вернуть `403`, хотя вход в Microsoft и Xbox Live уже пройдут.

## Где лежат токены

После успешного входа токены сохраняются в `.mcplus/account.json` (каталог в `.gitignore`).
Там refresh token — файл приватный, никому не передавай. Удалить: `mcplus logout`.
