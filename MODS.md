# Моды для «Слоя»

Мод — это текстовый файл .json. Добавляется в приложении: Слой -> МОДЫ ->
«Импорт файла» или «Вставить текст». Пересборка APK не нужна.

```json
{
  "name": "Мой мод",
  "greeting": "Привет!",
  "accent": "#FF4081",
  "hide": ["weather", "eyes"],
  "buttons": [
    {"icon": "📶", "action": "wifi"},
    {"icon": "🎵", "action": "launch", "arg": "com.spotify.music"},
    {"icon": "🌐", "action": "url", "arg": "https://google.com"},
    {"icon": "💬", "action": "say", "arg": "Текст на панели"}
  ]
}
```

Что можно скрыть (hide):
clock, eyes, music, battery, weather, volume, brightness, buttons, message

Действия кнопок (action):
torch, silent, camera, settings, wifi, bluetooth,
launch (arg = пакет приложения), url (arg = ссылка), say (arg = текст)

Все поля необязательны. Кривой файл просто игнорируется, панель не падает.
Кнопок из модов максимум 12.
