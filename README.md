[![](https://jitpack.io/v/mvr347/LoveClans.svg)](https://jitpack.io/#mvr347/LoveClans)

# LoveClans

Система кланов с войнами, территориями, казной и дипломатией.

## Общая структура

LoveClans предоставляет фреймворк для организации игроков в кланы с системой рангов, боевого влияния, захватом территорий и ведением войн. Кланы имеют общую казну (сундук) для хранения предметов и денег, а также систему контрактов (обетов) на еженедельные и ежедневные задания.

## Команды

| Команда | Алиасы | Описание | Пермишин |
|---|---|---|---|
| `/loveclan` | `clan`, `c` | Основная команда кланов — открыть меню, просмотреть информацию | `loveclans.command` |
| `/diplo` | `cd` | Открыть меню дипломатии для взаимодействия с другими кланами | `loveclans.diplomacy` |
| `/loveclans` | `clans` | Открыть список всех кланов на сервере | `loveclans.command` |
| `/loveclansadmin` | `clansadmin`, `ca` | Администраторские команды (reload, delete, setspirit) | `loveclans.admin` |

### Подкоманды `/loveclansadmin`

- `/loveclansadmin reload` — перезагрузить конфигурацию
- `/loveclansadmin delete <clan_tag>` — удалить клан
- `/loveclansadmin setspirit <player> <spirit>` — назначить дух клану игрока

## Пермишины

### Основные команды (default: true)

| Пермишин | Описание |
|---|---|
| `loveclans.command` | Доступ к основным командам кланов |
| `loveclans.create` | Создание нового клана |
| `loveclans.disband` | Роспуск клана (требует лидера) |
| `loveclans.invite` | Приглашение игроков в клан |
| `loveclans.invitestoggle` | Управление публичностью приглашений |
| `loveclans.accept` | Принятие приглашения в клан |
| `loveclans.leave` | Выход из клана |
| `loveclans.kick` | Исключение из клана |
| `loveclans.rank` | Изменение рангов членов |
| `loveclans.claim` | Захват территории для клана (требует LoveClaims) |
| `loveclans.unclaim` | Отпуск территории |
| `loveclans.menu` | Открытие меню клана |
| `loveclans.chest` | Доступ к казне клана |
| `loveclans.ritual` | Использование обрядов клана |
| `loveclans.war` | Объявление войны и участие в боях |
| `loveclans.diplomacy` | Управление дипломатическими отношениями |
| `loveclans.settings` | Изменение настроек клана |
| `loveclans.applications` | Управление заявками на вступление |
| `loveclans.vote` | Участие в голосованиях клана |
| `loveclans.home` | Использование клановой базы (дома) |

### Администраторский пермишин (default: op)

| Пермишин | Описание |
|---|---|
| `loveclans.admin` | Доступ ко всем администраторским командам |

### Совместимость (deprecated, но всё ещё работают)

Старые пермишины `clans.*` поддерживаются как fallback:

| Старый | Новый |
|---|---|
| `clans.command` | `loveclans.command` |
| `clans.create` | `loveclans.create` |
| `clans.disband` | `loveclans.disband` |
| `clans.admin` | `loveclans.admin` |
| ... (и остальные) | `loveclans.*` |

## Конфигурация

### Основные параметры (`config.yml`)

```yaml
clans:
  # Тег клана — видимое имя вроде [ABC] перед именем игрока
  tag:
    min-length: 3           # минимум символов
    max-length: 6           # максимум символов
    pattern: "^[A-Za-z0-9_]+$"  # допустимые символы (буквы, цифры, подчёркивание)

  # Полное название клана
  name:
    min-length: 4
    max-length: 10
    pattern: "^[\\p{L}\\p{N} _-]+$"  # буквы (включая кириллицу), цифры, пробелы, дефис, подчёркивание

  # Кулдаун между созданием кланов
  creation-cooldown-seconds: 86400  # 24 часа

  # Стоимость создания клана (в монетах LoveCore, см. LoveEconomy)
  creation-cost: 0

  # Кулдаун на повторное присоединение после выхода
  rejoin-cooldown-seconds: 3600  # 1 час

  # Цвет тега по умолчанию
  default-tag-color: "<gold>"
```

### Казна клана (`clans.chest.*`)

```yaml
chest:
  # Предмет, используемый как слот валюты в казне (для отображения денег)
  # Формат: namespace:id (e.g., 'currency:gold_coin')
  # Хранилище денег во всём остальном идёт через LoveCore.LoveEconomy (монеты в инвентаре)
  currency-item: 'currency:gold_coin'

  tax:
    # С какого уровня клана взимается налог
    tax-free-until-level: 3  # 1-2 уровень — без налога

    # Базовый налог
    base-amount: 1000

    # Налог за члена клана
    percent-per-member: 0.05  # 5% за члена

    # Налог за ряд в сундуке
    percent-per-extra-row: 0.10  # 10% за ряд
```

### Цвета кланов

Доступные цвета щитов и тегов в конфигурации — от белого до фиолетового. Пример для красного клана:

```yaml
available-colors:
  red: { tag: "<red>", material: RED_WOOL, name: "Красный", dye-color: RED }
```

### Контракты (обеты)

```yaml
contracts:
  npc-id: -1               # ID NPC-Маршала (Citizens), -1 = отключено
  npc-bind-distance: 6.0
  tick-interval-minutes: 5

  # Штраф за невыполненный обет (% от награды)
  penalty-percent: 80

  # Множитель сложности = base + (members-1) × per-member-step
  difficulty:
    base: 1.0
    per-member-step: 0.15
```

## Экономика

### Монеты и платежи

LoveClans использует единую валюту LoveCore (`LoveEconomy`). Все платежи — физические монеты ItemsAdder в инвентаре игрока:

- **Создание клана**: `creation-cost` (по умолчанию 0)
- **Налог казны**: взимается еженедельно в зависимости от уровня и размера клана
- **Лута из войн**: распределяется между участниками боя

Казна клана хранит предметы и деньги (отображаются через `currency-item`). Отложенные деньги ходят через инвентарь игрока, списание идёт через LoveEconomy.

## PlaceholderAPI

Если включён PlaceholderAPI, доступны следующие плейсхолдеры:

| Плейсхолдер | Описание | Пример |
|---|---|---|
| `%loveclans_clan_name%` | Название клана игрока | `Dragonslayers` |
| `%loveclans_clan_tag%` | Тег клана | `[DRG]` |
| `%loveclans_clan_level%` | Уровень клана | `5` |
| `%loveclans_clan_members%` | Количество членов | `12` |
| `%loveclans_clan_influence%` | Влияние клана | `450` |
| `%loveclans_player_rank%` | Ранг игрока в клане | `Лидер` |
| `%loveclans_player_joindate%` | Дата присоединения | `2025-07-01` |

## Зависимости

### Обязательные

- **LoveCore** — единая экономика, служба репутации (`ReputationOracle`)
- **Paper 1.21.11** — базовый Minecraft сервер

### Мягкие зависимости

- **LoveClaims** — система территорий (если отсутствует, захват территорий отключен)
- **LoveTrades** — торговля между кланами
- **Citizens** — NPC для Маршала контрактов
- **PlaceholderAPI** — интеграция плейсхолдеров
- **ItemsAdder** — кастомные предметы (щиты, эмблемы)
- **LoveHunt** — интеграция с охотничьей системой

## Установка и сборка

### Зависимость в Maven

```xml
<dependency>
    <groupId>com.github.mvr347.LoveClans</groupId>
    <artifactId>loveclans</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>

<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
```

### Компиляция

```bash
mvn package
```

Java 21, Paper 1.21.11. Сборка на пуш настроена в `.github/workflows/build.yml`.

## Структура данных

### Таблицы базы данных

- `clans` — информация о кланах
- `clan_members` — участники
- `clan_chest_inventory` — содержимое казны
- `clan_treasury` — хранилище денег казны (интегрировано с LoveEconomy)
- `diplomacy_relations` — дипломатические отношения
- `territories` — захваченные территории (требует LoveClaims)
- `contracts` — активные контракты/обеты

Используется SQLite по умолчанию; возможна настройка на MySQL в `config.yml`.
