# Стандарт оформления лора GUI (MiniMessage)

Источник: `CLAN_SPIRIT_DESIGN_GUIDE.md` (общая дизайн-система `gui-gen`). Гайд написан `&`-кодами DeluxeMenus, здесь он
переведён в MiniMessage. Пилот — LoveClans; остальные плагины переводятся по этому документу.

Соответствие цветов: `&8` → `<dark_gray>`, `&7` → `<gray>`, `&f` → `<white>`, `&a` → `<green>`, `&c` → `<red>`,
`&6` → `<gold>`, `&e` → `<yellow>`.

## Роли цветов
| Цвет | Где |
|---|---|
| `dark_gray` | подзаголовок категории, скобки шкал, разделители, стрелки `→`, тире `—` |
| `gray` | основной текст и названия параметров |
| `white` | числа, лимиты, **всегда прямо перед монетой/числом денег** |
| `green` | прирост `+`, выполнено `✓`, активное |
| `red` | нехватка `✕`, блокировки, предупреждения |
| `gold` / `yellow` | ранги, опыт, уровни, акценты |

## Название предмета
- Без иконок, смайликов и подзаголовков через `·`; без `<bold>`.
- Предмет с состоянием: `<gold>Имя <dark_gray>| <yellow>Состояние`.
- Неактивный: `<gray>Имя <dark_gray>| <red>Недоступно`.
- Курсив снимается централизованно в `ItemBuilder` (`name`/`lore`); явный курсив в тексте сохраняется.
- Навигация (назад, закрыть, страницы) — единственное исключение: название без лора.

## Лор
```yaml
lore:
  - "<dark_gray>Подзаголовок категории"
  - ""
  - "<dark_gray>▪ <gray>Текущие эффекты:"
  - "  <green>+ <gray>Прирост опыта: <white>+15% <dark_gray>→ <green>+20% <dark_gray>(<green>+5%<dark_gray>)"
  - ""
  - "<dark_gray>▪ <gray>Требования:"
  - "  <green>✓ <gray>Казна клана: <white>50000 <dark_gray>(<green>готово<dark_gray>)"
  - "  <red>✕ <gray>Эссенции: <red>2 <dark_gray>/ <white>5 <dark_gray>(<red>не хватает 3<dark_gray>)"
  - "<dark_gray><st>─────────────────────</st>"
  - "<green>ЛКМ <dark_gray>— <gray>открыть"
  - "<gold>ПКМ <dark_gray>— <gray>вложить"
```

- Строки внутри секции — с отступом в два пробела.
- Шкала: 16 клеток `█` в тёмных скобках и процент; `gold` пока заполняется, `green` на 100 %. Строится `util/LoreBar`
  и подставляется плейсхолдером `<bar>`.
- Разделитель перед блоком кликов — значение `gui.style.separator`.
- Клики: `gui.style.click.left|right|shift-left|shift-right` (`<action>` — текст действия). `ЛКМ` — `green`,
  `ПКМ` — `gold`, `Shift+…` — `yellow`; по одному действию в строке, средняя кнопка мыши не используется (вместо неё Shift).
- Запрещены стрелки `›`/`>` и старые формы `ЛКМ - x`, `▸ ЛКМ — x`, «Нажмите…».

## Неактивная кнопка
Порядок строк: разделитель, статус, `Причина:`, заблокированный клик.
```yaml
- "<dark_gray><st>─────────────────────</st>"
- "<red>✕ Действие недоступно"        # gui.style.status.*
- "<red>Причина: <gray>Не хватает ресурсов"   # MessageService.reason / denyLore
- "<dark_gray>ЛКМ — улучшить (заблокировано)" # gui.style.blocked-click
```
Статусы: `gui.style.status.unavailable`, `locked` (нет прав), `cooldown` (`<yellow>⌛`), `max` (`<green>✔`,
единственное место, где допустим `✔`). Текст причины берётся из чат-ключей `deny.*`; `MessageService.reasonTemplate`
вырезает из него свои цвета и префикс, поэтому один и тот же ключ годится и для чата, и для лора. Иконка кнопки меняется
на `HEAD_INACTIVE`; при клике — звук `BLOCK_NOTE_BLOCK_BASS`.

## Деньги
Число и глиф монеты (`%img_*_coin%`) всегда в `<white>`: цвет перед глифом красит текстуру. `CoinFormat` оборачивает
глиф в `<white>` сам, но число после него наследует предыдущий цвет — поэтому перед плейсхолдером денег ставится `<white>`.

## Эталонные примеры
Готовые образцы для частых кнопок: сначала «было» (как делать не надо), затем «стало» — настоящие ключи `lang.yml` LoveClans.
Правила, на которых они построены:

- Название — `Имя | Состояние`; состояние входит в название, а не прячется в лор. Названия без `<bold>`, значков и `·`.
- Первая строка лора — подзаголовок категории (`dark_gray`), затем пустая строка и секции `▪ …:` с отступом в 2 пробела.
- Клик — всегда отдельная строка после разделителя, по **одному действию в строке**: `ЛКМ` зелёный, `ПКМ` золотой,
  `Shift+…` жёлтый. Среднюю кнопку мыши не используем — вместо неё Shift. Строки кликов собирает `LoreBlocks.clicks`
  из `gui.style.click.*`, а не текст в `lang.yml`.
- `✓` / `✕` — выполнено / не выполнено, `⌛` — срок, `✔` только в статусе «макс» (`gui.style.status.max`).
- Цена и деньги — белые (`<white>`), перед глифом монеты тоже `<white>`.
- Любое `x/y` прогресса — шкала `<bar>` (16 клеток, процент); вместимость (`участников 5/10`) шкалы не требует.

### 1. Тумблер вкл/выкл
Состояние — в названии, в лоре «Было → Станет» после клика, клик один. Источник: `gui.rank-permissions`.

Было:
```yaml
enabled:
  - "<green>✓ Право включено"
  - "<dark_gray><st>─────────────────────</st>"
  - "<green>ЛКМ <dark_gray>— <gray>отключить право"
```
Стало:
```yaml
rank-permissions:
  name-on: "<gold><permission> <dark_gray>| <green>Вкл"
  name-off: "<gold><permission> <dark_gray>| <red>Выкл"
  state-on: "<green>Вкл"
  state-off: "<red>Выкл"
  lore:
    - "<dark_gray>Право ранга"
    - ""
    - "<dark_gray>▪ <gray>После клика:"
    - "  <gray>Право: <now> <dark_gray>→ <next>"
  action-enable: "включить право"      # LoreBlocks.clicks(…, hint(LEFT, action-enable | action-disable))
  action-disable: "отключить право"
```

### 2. Кнопка-цикл со списком вариантов
Текущий вариант — в названии и отмечен в списке; ЛКМ вперёд, ПКМ назад. Источник: `gui.cycle` (`CycleButton`).

Было:
```yaml
cycle:
  option-current: "<green>▶ <name>"
  option-other: "<gray>  <name>"
  hint:
    - "<dark_gray><st>─────────────────────</st>"
    - "<green>ЛКМ <dark_gray>— <gray>следующий вариант"
    - "<gold>ПКМ <dark_gray>— <gray>предыдущий вариант"
```
Стало:
```yaml
cycle:
  name: "<name> <dark_gray>| <yellow><current>"
  subtitle: "<dark_gray>Переключатель"
  options-title: "<dark_gray>▪ <gray>Варианты:"
  option-current: "  <green>▶ <name>"
  option-other: "    <gray><name>"
  next: "следующий вариант"            # LoreBlocks.clicks(LEFT next, RIGHT previous)
  previous: "предыдущий вариант"
```

### 3. Ввод в чат и шаговый счётчик
Кнопка ввода показывает текущее значение (или `—` / красное «не задано») и секцию «Правила ввода» с лимитами из
конфига. Источник: `gui.settings.rename`.

Было:
```yaml
rename:
  name: "<gold>Переименовать клан"
  lore:
    - "<gray>Изменить название клана."
    - "<dark_gray><st>─────────────────────</st>"
    - "<green>ЛКМ <dark_gray>— <gray>ввести новое название"
```
Стало:
```yaml
rename:
  name: "<gold>Переименовать клан"
  lore:
    - "<dark_gray>Ввод в чат"
    - ""
    - "<dark_gray>▪ <gray>Сейчас:"
    - "  <gray>Название: <white><current>"
    - ""
    - "<dark_gray>▪ <gray>Правила ввода:"
    - "  <gray>Длина: <white><min>–<max> <gray>символов"
    - "<dark_gray><st>─────────────────────</st>"
    - "<green>ЛКМ <dark_gray>— <gray>ввести новое название"
```
Шаговый счётчик (в LoveClans его нет; общая схема): значение и границы — секцией, четыре действия — четыре строки:
`ЛКМ +1`, `ПКМ −1`, `Shift+ЛКМ +10`, `Shift+ПКМ −10`, собранные одним `LoreBlocks.clicks(...)` с общим разделителем.

### 4. Таймер и кулдаун
Срок — секция с `⌛`; кнопка на паузе — блок отказа со статусом `gui.style.status.cooldown`. Источник:
`gui.modifiers.entry.guild-shield-cooldown`, `ClanModifiersMenu#cooldownEntry`.

Было:
```yaml
guild-shield-cooldown:
  name: "<gray>Пауза Гильдейского щита"
  lore:
    - "<yellow>⌛ <gray>Снова можно через <white><time>"
```
Стало:
```yaml
guild-shield-cooldown:
  name: "<white>Гильдейский щит <dark_gray>| <yellow>Пауза"
  reason: "Гильдейский щит недавно действовал: повторно его можно использовать позже."
  lore:
    - "<dark_gray>Перезарядка"
    - ""
    - "<dark_gray>▪ <gray>Срок:"
    - "  <yellow>⌛ <gray>Снова можно через <white><time>"
# + LoreBlocks.blockedLines(messages, player, "gui.style.status.cooldown", List.of(messages.reason(base + ".reason", player)), null)
# у кликабельной кнопки (щит в столице): LoreBlocks.blocked(…, "gui.style.status.cooldown", denyKey, placeholders, hint)
```

### 5. Выбор: вкладка и radio
Выбранный вариант — `Имя | Выбрано` и строка «Выбран сейчас» без клика; остальные несут клик. Источник: `gui.casus-belli.tab`,
`gui.trade.tab`.

Было:
```yaml
tab:
  selected: "<green>Открыта сейчас"
  open:
    - "<dark_gray><st>─────────────────────</st>"
    - "<green>ЛКМ <dark_gray>— <gray>открыть"
```
Стало:
```yaml
tab:
  selected: "<green>✓ <gray>Выбран сейчас"
  war:
    name: "<gold>Война"
    name-selected: "<gold>Война <dark_gray>| <green>Выбрано"
    lore:
      - "<dark_gray>Тип конфликта"
      - ""
      - "<gray>Открытый конфликт за честь и репарации."
    action: "переключить на войну"     # у невыбранной: LoreBlocks.clicks(LEFT action)
```

### 6. Цена и средства `✓` / `✕`
Цена — своя секция, под ней строка средств с точной нехваткой. Источник: `gui.recognition`, `gui.style.funds`.

Было:
```yaml
info-lore:
  - "<gray>Цена: <white><cost>"
  - "<gray>Признанный клан получает больше мест,"
not-enough: "<red>✕ Не хватает монет на признание"
```
Стало:
```yaml
recognition:
  info-lore:
    - "<dark_gray>Статус клана"
    - ""
    - "<dark_gray>▪ <gray>Что даёт:"
    - "  <green>+ <gray>Больше мест в клане и территорий"
    - ""
    - "<dark_gray>▪ <gray>Цена:"
    - "  <gray>Признание: <white><cost>"
style:
  funds:                                # LoreBlocks.funds(messages, player, cost, balance)
    ok: "  <green>✓ <gray>Средств хватает <dark_gray>(<green>готово<dark_gray>)"
    missing: "  <red>✕ <gray>Не хватает: <white><missing> <dark_gray>(<gray>есть <white><have><dark_gray>)"
```

### 7. Апгрейд «Было → Станет»
Изменение показывается парой «было → станет (+Δ)» в одной строке секции «После выбора», уровень — со шкалой. Источник:
`gui.upgrades.option.members`, `gui.upgrades.option.level`.

Было:
```yaml
members:
  now: "<gray>Сейчас: <white><now> мест"
  after: "<gray>После выбора: <white><after> мест"
```
Стало:
```yaml
members:
  now: "  <gray>Мест для участников: <white><now>"
  change:
    - "<dark_gray>▪ <gray>После выбора:"
    - "  <green>+ <gray>Места: <white><now> <dark_gray>→ <green><after> <dark_gray>(<green>+<delta><dark_gray>)"
level:
  - "  <gray>Уровень улучшения: <white><level> <dark_gray>/ <white><max>"
  - "  <bar>"
```

### 8. Прогресс и шкала
Любое `x/y` — это шкала `LoreBar.bar(current, max)` (золотая, на 100 % зелёная) в плейсхолдере `<bar>`; число можно оставить
рядом. Источник: `gui.trade.state.info`.

Было:
```yaml
progress: "<gray>Собрано: <white><filled>/<total>"
ends: "<gray>До конца: <white><time>"
```
Стало:
```yaml
info:
  head:
    - "<dark_gray>Государственная закупка"
    - ""
    - "<dark_gray>▪ <gray>Заказ:"
    - "  <gray>Собрано: <white><filled>/<total> <dark_gray>(общий на весь сервер)"
    - "  <bar>"
  time-title: "<dark_gray>▪ <gray>Срок:"
  ends: "  <yellow>⌛ <gray>До конца: <white><time>"
```

### 9. Запись списка
Элемент списка — как профиль: название `Имя | Состояние`, секции «Профиль / Показатели», один клик «открыть».
Источник: `gui.clan-list.clan-item`, `gui.members.entry`.

Было:
```yaml
clan-item:
  status-recognized: "<green>✓ Признанный клан"
  leader: "<gray>Глава: <white><player>"
  level: "<gray>Уровень: <white><level>"
  members: "<gray>Участников: <white><current>/<max>"
  click-info:
    - "<dark_gray><st>─────────────────────</st>"
    - "<green>ЛКМ <dark_gray>— <gray>подробная информация"
```
Стало:
```yaml
clan-item:
  name: "<color><name> <white>[<tag>]"
  profile:
    - "<dark_gray>Клан"
    - ""
    - "<dark_gray>▪ <gray>Профиль:"
    - "  <gray>Статус: <recognition>"
    - "  <gray>Основан: <white><date>"
  stats:
    - ""
    - "<dark_gray>▪ <gray>Показатели:"
    - "  <gray>Уровень: <white><level>"
    - "  <gray>Мощь: <white><influence>"
    - "  <gray>Участников: <white><current>/<max>"
  action: "подробная информация"        # LoreBlocks.clicks(LEFT action)
```

### 10. Пустое состояние
Пустой список — не одна строка в названии, а предмет с подзаголовком «Пусто» и секцией «Что делать». Источник:
`gui.modifiers.empty`, `gui.history.empty`.

Было:
```yaml
empty: "<gray>Здесь пока ничего не произошло."
```
Стало:
```yaml
empty:
  name: "<gray>Нет активных эффектов и пактов"
  lore:
    - "<dark_gray>Пусто"
    - ""
    - "<dark_gray>▪ <gray>Что делать:"
    - "  <gray>Пакты, эмбарго и блокады оформляются"
    - "  <gray>в меню дипломатии, Гильдейский щит — в столице."
```

### 11. Подтверждение и отмена
Кнопки без скобок (скобки — вид кликабельного текста в чате); на «Подтвердить» — секция последствий и клик, на «Отмена» —
«Ничего не изменится». Источник: `gui.confirm`, `ClanConfirmMenu#effects`.

Было:
```yaml
confirm:
  "yes": "<green>[Подтвердить]"
  "no": "<red>[Отмена]"
  disband:
    title: "<red>Распустить клан <color><tag></color>?"
```
Стало:
```yaml
confirm:
  "yes": "<green>Подтвердить"
  "no": "<red>Отмена"
  subtitle: "<dark_gray>Подтверждение"
  effects-title: "<dark_gray>▪ <gray>Что произойдёт:"
  yes-action: "подтвердить"
  no-lore:
    - "<dark_gray>Отмена"
    - ""
    - "<gray>Ничего не изменится."
  disband:
    title: "<red>Распустить клан <color><tag></color>?"
    effects:
      - "  <red>− <gray>Клан будет распущен безвозвратно"
      - "  <red>− <gray>Все участники покинут клан"
```

### 12. Заблокированный вариант
Название `Имя | Недоступно` (`LoreBlocks.disabledName`), иконка `HEAD_INACTIVE`, затем блок: разделитель → `✕` статус →
`Причина:` → заблокированный клик. Блок собирает `LoreBlocks.blockedReason` / `blocked` / `blockedLines`, руками его не пишем.
Источник: `gui.members-hub.filter-disabled`, `gui.chest.locked-warning`.

Было:
```yaml
filter-disabled:
  name: "<gray>Фильтрация <dark_gray>| <red>Недоступно"
  lore:
    - "<gray>В режиме просмотра вклада отображаются"
    - "<gray>все участники клана."
    - "<dark_gray><st>─────────────────────</st>"
    - "<red>✕ Действие недоступно"
    - "<red>Причина: <gray>Фильтрация отключена в режиме вклада"
    - "<dark_gray>ЛКМ — сменить фильтр (заблокировано)"
```
Стало:
```yaml
filter-disabled:
  lore:
    - "<dark_gray>Фильтр списка"
    - ""
    - "<gray>В режиме вклада отображаются все участники клана."
  reason: "Фильтрация отключена в режиме вклада"
  action: "сменить фильтр"
# .name(LoreBlocks.disabledName(messages, player, "gui.members-hub.filter-title"))
# .lore(LoreBlocks.blockedReason(messages, player, "gui.members-hub.filter-disabled.reason", Map.of(), hint(LEFT, "…action")))
```

### 13. Инфо-голова
Голова-справка в слоте 0: подзаголовок «Справка» и секции, без кликов. Источник: `gui.modifiers.info`.

Было:
```yaml
info:
  name: "<gold>Модификаторы клана <white><clan>"
  lore:
    - "<gray>Список активных эффектов, репараций,"
    - "<gray>щитов, пактов и доступных поводов к войне."
```
Стало:
```yaml
info:
  name: "<gold>Модификаторы клана <white><clan>"
  lore:
    - "<dark_gray>Справка"
    - ""
    - "<dark_gray>▪ <gray>Что здесь показано:"
    - "  <gray>Активные эффекты, репарации и щиты"
    - "  <gray>Пакты, блокады и поводы к войне"
```

### 14. Навигация — исключение; физические предметы
**Навигация** (назад, закрыть, страницы) остаётся без лора: у неё нет ни состояния, ни действия сверх очевидного.
```yaml
back: "<gray>« Назад"
close: "<red>Закрыть"
next-page: "<gray>Следующая страница »"
previous-page: "<gray>« Предыдущая страница"
```
**Физические предметы** (печати, знамёна, компасы) лежат в инвентаре, а не в меню: строк кликов нет, «как использовать»
описывается секцией; название без `<bold>`, тело не жёлтое, курсив снимает фабрика предметов. Источник: `item.*`, `raid.compass`.

Было:
```yaml
clan-creation-banner:
  name: "<gold><bold>Знамя основания клана"
  lore:
    - "<gray>Куплено у торговца знамёнами."
    - "<yellow>Установите на свободной земле,"
    - "<yellow>чтобы основать свой клан и"
    - "<yellow>провозгласить столицу!"
```
Стало:
```yaml
clan-creation-banner:
  name: "<gold>Знамя основания клана"
  lore:
    - "<dark_gray>Знамя основания"
    - ""
    - "<gray>Куплено у торговца знамёнами."
    - ""
    - "<dark_gray>▪ <gray>Как использовать:"
    - "  <gray>Установите на свободной земле,"
    - "  <gray>чтобы основать свой клан и"
    - "  <gray>провозгласить столицу!"
```
Предмет с действием по ПКМ (Гильдейский щит) добавляет строку клика через `LoreBlocks.clicks(…, hint(RIGHT, "item.guild-shield.action"))`.

## Помощники в коде
- `util/ItemBuilder` — снятие курсива (`name`/`lore`, `upright` для готовых компонентов).
- `util/LoreBar` — шкала прогресса (`bar(cur, max)`, `bar(cur, max, color)`).
- `gui/LoreBlocks` — `clicks(...)` (разделитель + строки кликов), `blocked(...)` / `blockedReason(...)` / `blockedLines(...)`
  (блок неактивной кнопки), `disabledName(...)` (название `Имя | Недоступно`), `funds(...)` (строка средств `✓`/`✕`).
- `gui/CycleButton` — переключатель фильтра/сортировки; `gui/ClanConfirmMenu#effects` — последствия подтверждения.
- `service/MessageService.reason` / `denyLore` — строки `Причина:`.

## Проверки
`GuiLoreStyleTest` читает `lang.yml` (все `gui.*`, предметы `item.*`, `raid.compass`, экран добычи, баннеры `clan.banner`) и
следит за стилем: символы и `<bold>` в названиях, цвета кликов (`ЛКМ` зелёный, `ПКМ` золотой, `Shift` жёлтый), одно действие
в строке, разделитель непосредственно перед первым кликом, порядок блока неактивной кнопки, подзаголовок первой строкой лора,
отступ строк секции, собственный цвет у каждой строки, шкала для `x/y`, белые деньги, отсутствие `✔`/`✘` и слов «Нажмите»/«Кликните».
Исключения (навигация без лора, бесцветные значения для подстановки, экранные заголовки, вместимость без шкалы) заданы явными
списками рядом с причиной. `NoHardcodedColorsTest` запрещает `§`, `ChatColor` и `&`-коды в коде всех классов `gui/**` и
`ClanItemFactory` (комментарии не учитываются). `GuiLangKeysTest` проверяет, что все литеральные ключи меню и предметов есть в `lang.yml`.

## Доставка на сервер
`copyDefaults` не обновляет существующие строки. Новый вид текстов доходит до серверов только через `lang-version` и
`LangMigrator.OVERWRITE_BY_VERSION`: при переходе версия перезаписывает перечисленные разделы из jar (правки администратора
в них пропадают), старый файл сохраняется как `lang.yml.v<прежняя_версия>.bak`.
