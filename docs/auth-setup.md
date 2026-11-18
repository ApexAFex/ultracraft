# Вход через Microsoft: как получить client id

Клиенту нужен **client id** приложения Azure — это идентификатор программы,
которая просит Microsoft пустить пользователя. Без него device-code вход не начать.

## Шаг 1. Зарегистрировать приложение (бесплатно, ~5 минут)

1. Открыть <https://portal.azure.com> → **Microsoft Entra ID** → **App registrations** → **New registration**.
2. Имя — любое (например, `mcplus`).
3. Supported account types — **Accounts in any organizational directory and personal Microsoft accounts**.
4. Redirect URI — можно оставить пустым (для device code не нужен).
5. Создать и скопировать **Application (client) ID**.

## Шаг 2. Разрешить публичный клиент

В приложении: **Authentication** → **Allow public client flows** → **Yes** → Save.
Без этого Microsoft откажет в device code.

## Шаг 3. Скормить его клиенту

```powershell
$env:MCPLUS_CLIENT_ID = "<скопированный id>"
.\gradlew.bat :app:run --args="login"
```

Или разово флагом: `--client-id <id>`.

## Важное ограничение

Mojang ограничивает доступ к `api.minecraftservices.com`: новым приложениям нужно
отдельно запросить разрешение через форму <https://aka.ms/mce-reviewappid>.
До одобрения Minecraft API может отвечать `403` на шаге `login_with_xbox`,
хотя вход в Microsoft и Xbox Live уже прошёл.

Если упёрся в 403 — есть два пути:

- подать заявку по форме и дождаться одобрения;
- использовать client id уже одобренного приложения (например, своего второго
  проекта), если он у тебя есть.

## Где лежат токены

После успешного входа токены сохраняются в `.mcplus/account.json` (каталог в `.gitignore`).
Там refresh token — файл приватный, никому не передавай. Удалить: `mcplus logout`.
