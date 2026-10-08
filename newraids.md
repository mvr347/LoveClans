# LoveClans — новый дизайн рейдов (Raid)

Статус: согласованный дизайн (2026-10-08).  
Цель: реальные «ограбления» точки, а не ночной лут казны по онлайну. Мелкий клан может рейдить крупный.

---

## 1. Суть

Атакующие объявляют набег → после подготовки в случайной точке территории защитника появляется **рейдовый сундук** → атакующие получают **компас только себе** и идут к точке → стоят в зоне вокруг сундука и набирают **% захвата** → при 100% открывают лут → **забирают и уходят**.

Защитники срывают захват, находясь в зоне / выбивая атакующих.  
Иммунитет по влиянию (influence) **снимается**.

---

## 2. Условия старта

| Правило | Значение |
|--------|----------|
| Минимум атакующих онлайн | **2** |
| Максимум защитников онлайн | **2** (игроки в AFK **> 15 минут** не считаются) |
| Иммунитет по influence | **нет** |
| Капитал у обеих сторон | да |
| Не в war / siege / другом raid | да |
| Не союзники | да |
| Кулдаун пары кланов | **24 часа** |
| Щит на защитника после рейда | **12 часов** (нельзя рейдить снова) |
| Лимит рейдов в сутки (как атакующий) | **5** |
| Лимит «быть целью» в сутки | **1** |
| Окно по времени суток | **нет** |

### PREPARING — отмена

Если во время подготовки онлайн защитников (не-AFK) стал **> max-defender-online** → рейд **отменяется**, кулдаун пары = **2 часа** (не полный 24).

### AFK

Игрок считается AFK, если неактивен **более 15 минут**. Такие игроки:
- не увеличивают счётчик `max-defender-online` при старте и в PREPARING;
- не блокируют/не ускоряют спад захвата в зоне (опционально уточнить в коде: безопаснее **не считать их защитниками в зоне**).

---

## 3. Фазы

```
PREPARING (5 мин)
  → босс-бар «Рейд через X:XX»
  → в конце: спавн сундука, компасы атакующим, аларм защитникам

ACTIVE — CAPTURE
  → зона вокруг сундука, % 0→100 на босс-баре
  → партиклы зоны, подсветка атакующих (см. §6)

ACTIVE — LOOT
  → сундук открыт, лимит лута (snapshot казны)
  → нужно забрать substantial loot и выйти из зоны (extract)

END
  → ATTACKER_WIN / DEFENDER_WIN / CANCELLED
  → щит 12ч на защитника, запись в архив
```

Дефолтная длительность ACTIVE: **12 минут** (capture + loot + extract).

---

## 4. Рейдовый сундук

### Спавн

- Случайная точка внутри **капитальной** территории защитника (capital claim).
- Не ближе `min-distance-from-banner` блоков от знамени (дефолт 8).
- Y: твёрдый пол, не вода/лава, не внутри стены.
- До **10 попыток** resspawn; если все провалились (нет пути) → cancel raid.

### Представление

- Entity (ArmorStand / Interaction + display chest) или устойчивый блок+hitbox.
- PDC: `raid_id`, `defender_clan_id`.
- Нельзя сломать, подобрать, сдвинуть поршнем.

### Анти-коробка / доступ

1. **Проверка пути** от края claim к сундуку (path / flood-fill walkable). Нет пути → resspawn.
2. При спавне — лёгкая очистка player-placed блоков в `clear-radius` (2) вокруг сундука (не трогать banner, terrain, важные клановые блоки по whitelist).
3. На время ACTIVE в **зоне рейда** (capture radius): **запрет place/break** для обеих сторон (режим как siege в LoveClaims).
4. Сундук всегда подсвечен партиклами/светом — нельзя «спрятать» в темноте.

---

## 5. Зона захвата

```yaml
radius: 5                    # горизонтальный радиус от сундука
# вертикаль: цилиндр ±3 от Y сундука (или аналог)
```

### Скорость захвата (% в секунду)

Пока в зоне есть ≥1 атакующий и **нет** защитников (не-AFK):

```
rate = min(max_rate, base + (nAttackers - 1) * per_extra)
```

| Параметр | Дефолт | Смысл |
|----------|--------|--------|
| `base-rate-per-second` | 1.5 | один атакующий |
| `per-extra-attacker` | 0.6 | каждый следующий |
| `max-rate-per-second` | 4.0 | потолок (~25 с на 100% даже толпой) |

Примеры: 1 чел ≈ 67 с; 2 ≈ 48 с; 3 ≈ 37 с; 10 всё равно ≈ 25 с.

### Блокировка и спад

| Ситуация | Эффект |
|----------|--------|
| Любой защитник (не-AFK) в зоне | прогресс **не растёт** |
| Нет атакующих в зоне | спад `passive-decay-per-second` (0.4 %/с) |
| Есть защитник в зоне | спад `defender-decay-per-second` (1.2 %/с) |
| Прогресс | не ниже 0% |

### Босс-бар (участники рейда)

```
Рейд [ATK] → [DEF] | Захват 37% | 8:12
```

- CAPTURE: красный/жёлтый по прогрессу  
- LOOT: жёлтый  
- Победа/конец: зелёный / скрыть  

### Компас

Только **атакующим**, цель — локация сундука. У защитников компаса нет.

---

## 6. Подсветка захватчиков

Атакующие **подсвечиваются** (glow / outline), но **только** если они находятся:

- внутри claim защитника, **или**
- в буфере **+10 блоков** вокруг границ этого claim.

Вне claim+10 — без подсветки (можно подходить скрытно до периметра).  
Внутри — защитники видят, кто уже «на территории ограбления».

Технически: tick/check локации атакующих vs bounding box claim (LoveClaims) expanded by 10; `setGlowing(true/false)` или team glow.

---

## 7. Лут и extract

### Snapshot при 100% захвата

```yaml
loot-money-percent: 40
loot-item-slot-percent: 40
```

Лут через GUI сундука (аналог текущего RaidLootMenu), лимиты по cap.

### Substantial loot (для победы)

```yaml
win-min-money-percent: 15   # от moneyLootCap
win-min-item-slots: 1
```

«Хотя бы 1 монета» больше не считается победой.

### Extract

```yaml
extract:
  required: true
  seconds-after-first-loot: 60
  must-leave-zone: true      # выйти из capture radius
```

После первого substantial-забора идёт таймер extract. Нужно **покинуть зону сундука**.  
Если ACTIVE истекло без extract / без substantial → лут возвращается защитнику, **DEFENDER_WIN**.

---

## 8. Исходы и награды

| Исход | Условие | Награда |
|-------|---------|---------|
| **ATTACKER_WIN** | substantial loot + extract | exp × `win-exp-multiplier` (0.5), bonus-item, влияние+ |
| **DEFENDER_WIN** | время вышло при <100%, или не забрали / не вышли | exp × `defend-win-exp-multiplier` (0.3) защитнику |
| **CANCELLED** | PREPARING cancel, disband, admin | без наград; кулдаун 2ч или по ситуации |

После **любого** завершённого (не admin-abort на старте) рейда на защитника — **post-raid-shield 12ч**.

### Аларм защитникам

- В момент перехода в ACTIVE (или в начале PREPARING): title + sound всем **онлайн** членам.
- Запись в `/clan history`.
- Soft: письмо оффлайн-лидеру через LoveTweaks/почту, если доступно.

---

## 9. Конфиг (целевой)

```yaml
raid:
  pre-start-minutes: 5
  duration-minutes: 12
  cooldown-hours: 24
  preparing-cancel-cooldown-hours: 2
  post-raid-shield-hours: 12
  min-attacker-online: 2
  max-defender-online: 2
  afk-ignore-minutes: 15
  max-raids-per-clan-per-day: 5
  max-times-raided-per-day: 1
  # influence / isImmuneToRaid — не использовать

  capture:
    radius: 5
    base-rate-per-second: 1.5
    per-extra-attacker: 0.6
    max-rate-per-second: 4.0
    passive-decay-per-second: 0.4
    defender-decay-per-second: 1.2
    particle-interval-ticks: 15

  chest:
    resspawn-attempts: 10
    clear-radius: 2
    min-distance-from-banner: 8
    access-check: true

  attacker-glow:
    enabled: true
    claim-buffer-blocks: 10    # claim + 10 блоков вокруг

  loot-money-percent: 40
  loot-item-slot-percent: 40
  win-min-money-percent: 15
  win-min-item-slots: 1
  win-exp-multiplier: 0.5
  defend-win-exp-multiplier: 0.3

  extract:
    required: true
    seconds-after-first-loot: 60
    must-leave-zone: true

  bonus-items: []
```

---

## 10. Порядок реализации (код)

1. Расширить `ClanRaid`: progress, chestLocation, phase (CAPTURE|LOOT), loot flags, extractDeadline.
2. `RaidManager`: убрать influence immunity; shield; daily limits; AFK-aware online count.
3. Спавн сундука + path/clear + компасы только атакующим.
4. `tickCapture`: rate/decay, bossbar %, particles зоны.
5. Glow атакующих только в claim+10.
6. LOOT GUI + substantial; extract leave zone.
7. PREPARING cancel; алармы; награда защитнику.
8. LoveClaims: raid-zone no-build на время ACTIVE.

---

## 11. Открытые решения (зафиксировать при коде)

| Вопрос | Текущее решение |
|--------|------------------|
| Сундук только capital или любая territory? | **Capital** |
| Extract: только зона или весь claim? | **Только capture zone** |
| max-times-raided-per-day | **1** |
| AFK в зоне как защитник | **Не считать** (не блокирует и не даёт defender-decay) |

---

## 12. Что убрано из старого рейда

- Условие «рейд только если защитников мало» как **единственная** механика победы (онлайн остаётся мягким гейтом старта, но победа = точка).
- `isImmuneToRaid` по среднему influence.
- Победа за «1 монету вынесли».
- Мгновенный лут без точки и без extract (заменяется сундуком + захватом + уходом).
