# StarlitMoon Launcher

Кастомный лаунчер Minecraft-сервера [StarlitMoon](https://starlit-moon.ru/) на **Kotlin Compose Desktop**.

## Возможности

- **Авторизация как на сайте** — `POST /api/auth/login` (ник + пароль mcAuth)
- **Личный кабинет** — профиль через `/api/auth/me`
- **Админ-панель** — WebView с `https://starlit-moon.ru/admin`
- **Автообновления** — проверка GitHub Releases при запуске
- **Запуск игры** — загрузка клиента Mojang, подключение к `play.starlit-moon.ru`

## Скачать

Releases: https://github.com/starlit-moon/starlitmoon-launcher/releases

Windows: скачайте `StarlitMoon Launcher-1.x.x.exe` из последнего релиза.

## Сборка

```powershell
cd F:\Projects\Projects\starlitmoon-launcher
.\gradlew.bat run
```

EXE-установщик:

```powershell
.\gradlew.bat packageReleaseDistribution
```

Артефакт: `build/compose/binaries/main-release/exe/StarlitMoonLauncher-1.0.0.exe`

## GitHub

### Создать репозиторий

```powershell
gh auth login
.\scripts\create-github-repo.ps1 -Owner starlit-moon
```

### Опубликовать релиз с EXE

```powershell
.\scripts\publish-release.ps1 -Version 1.0.0
```

## Обновления

Лаунчер при старте запрашивает:

```
GET https://api.github.com/repos/starlit-moon/starlitmoon-launcher/releases/latest
```

Если версия новее — показывается баннер со ссылкой на `.exe` из assets релиза.

Настройки в `config.json`:

```json
{
  "githubOwner": "starlit-moon",
  "githubRepo": "starlitmoon-launcher",
  "checkUpdatesOnStart": true
}
```

## Конфигурация

Файл: `%USERPROFILE%\.starlitmoon-launcher\config.json`

```json
{
  "apiBaseUrl": "https://starlit-moon.ru",
  "serverHost": "play.starlit-moon.ru",
  "minecraftVersionId": "1.21.4",
  "minMemoryMb": 2048,
  "maxMemoryMb": 4096
}
```

## Требования

- JDK 17+ (сборка)
- Java 17+ (Minecraft-клиент)

## Лицензия

MIT — см. [LICENSE](LICENSE)
