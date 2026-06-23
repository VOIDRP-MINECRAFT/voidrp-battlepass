# 🏆 VoidRP Battle Pass

> Paper 1.21.1 плагин — сезонная система Battle Pass с Free/Premium треками и интеграцией в экосистему.

![Paper](https://img.shields.io/badge/Paper-1.21.1-00AF54)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Vault](https://img.shields.io/badge/depends-Vault-yellow)
![License](https://img.shields.io/badge/license-proprietary-red)

---

## 🗺️ Место в экосистеме

```
  Игрок выполняет задание / торгует на рынке
        │
  voidrp-battlepass ◄── PlayerMarketTradeEvent (gamesync-plugin)
        │              ◄── QuestCompleteEvent (daily-quests)
        │ POST /api/v1/battlepass/* (X-Game-Auth-Secret)
        ▼
  minecraft-backend ←→ voidrp-site (отображение прогресса)
```

---

## ✨ Возможности

- **Сезонная система** — настраиваемые даты начала и конца сезона
- **Два трека наград** — Free (бесплатный) и Premium (подписка)
- **Задания из пула** — квесты, убийства, добыча ресурсов, крафт, торговля
- **Battle Pass XP** — начисляется за действия в игре и через события плагинов
- **GUI через NPC** — «Хранитель Сезона», интерактивный интерфейс
- **Синхронизация прогресса** с backend через `X-Game-Auth-Secret`
- **Premium-статус** влияет на комиссию в магазине (2% → 1%)
- **Soft-depend** на `VoidRpDailyQuests` — расширяет пул заданий

---

## 📋 Требования

| Компонент | Версия |
|---|---|
| Paper / Mohist | 1.21.1 |
| Java | 21 |
| Vault | обязательно |
| LuckPerms | опционально |
| VoidRpDailyQuests | опционально |

---

## 🚀 Сборка и установка

```bash
cd voidrp_battlepass
./gradlew shadowJar
# → build/libs/voidrp-battlepass-*.jar
```

1. Скопировать jar в `plugins/`
2. Перезапустить сервер → создадутся конфиги
3. Заполнить `plugins/VoidRpBattlePass/config.yml`
4. Настроить NPC с командой `/battlepass npc set`

---

## ⚙️ Конфигурация

```yaml
# config.yml
backend:
  url: https://api.void-rp.ru/api/v1
  secret: <секрет>
season:
  name: "Сезон 1"
  start: "2026-01-01"
  end: "2026-03-31"
xp:
  per_trade: 50
  per_quest: 100
  per_kill: 5
```

---

## 🔗 Связанные репозитории

| Репо | Связь |
|---|---|
| [minecraft-backend](https://github.com/VOIDRP-MINECRAFT/minecraft-backend) | Хранит прогресс и Premium-статус |
| [voidrp-gamesync-plugin](https://github.com/VOIDRP-MINECRAFT/voidrp-gamesync-plugin) | Отправляет `PlayerMarketTradeEvent` |
| [voidrp-daily-quests](https://github.com/VOIDRP-MINECRAFT/voidrp-daily-quests) | Отправляет `QuestCompleteEvent`, расширяет пул |
| [voidrp-site](https://github.com/VOIDRP-MINECRAFT/voidrp-site) | Отображает прогресс Battle Pass |

---

<div align="center">
<a href="https://void-rp.ru">🌐 Сайт</a> ·
<a href="https://github.com/VOIDRP-MINECRAFT">🏠 Организация</a>
</div>
