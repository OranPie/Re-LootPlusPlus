# Re-LootPlusPlus — Config Parser Specification

> **Language / 语言:** [English](#english-version) · [中文](#中文版本chinese-version)

> **Implementation classes:**
> - `config/parse/Splitter.java` — Java `String.split(regex, 0)` semantics
> - `config/parse/LineReader.java` — comment/blank-line filtering with `SourceLoc`
> - `config/parse/NumberParser.java` — `int`/`float`/`bool` with WARN on parse failure
> - `config/loader/*Loader.java` — one loader class per config domain

> **Deferred / partial:** `fishing_amounts.txt` grammar has edge cases not fully verified from bytecode. `bows`, `guns`, `multitools` full projectile/ammo behavior and the block types `buttons`, `pressure_plates`, `slabs`, `stairs`, `panes`, `walls` are deferred.

---

## English Version

### §0 Global File-Reading Rules

Config text files are read line-by-line. Lines are **not** pre-trimmed and **not** pre-filtered before the loader receives them. Each loader receives the raw line as it appears in the zip entry or file stream.

**Blank lines:**
A line satisfying `line.equals("")` is silently skipped.

**Comment lines:**
A line is treated as a comment if and only if `line.length() > 0 && line.charAt(0) == '#'`. A line with leading whitespace such as `  #comment` is **not** a comment — the JAR does not trim before the character check.

**Extension (non-JAR-native):** Lines whose first character is `/` and whose second character is also `/` (i.e. `//`) are additionally treated as comments and silently skipped. This extension is implemented for compatibility with addon packs that use C-style comments.

Comment lines are silently skipped or passed through `notifyWrongNumberOfParts(comment=true)` without emitting any error or warning.

**File encoding:**
Read config text UTF-8 strict first; fall back to ISO-8859-1 / CP1252 on decode failure. Strip a leading BOM (`\uFEFF`) before processing.

---

### §0.3 Split Semantics

The JAR uses Java `String.split(regex)` with the default `limit=0`, which discards trailing empty fields. All implementations must reproduce this behavior exactly.

| Separator | Literal | Usage |
|-----------|---------|-------|
| Field separator | `_____` (five underscores) | Effects, drops, world gen, item/block additions |
| Drop group separator | `%%%%%` (five percent signs) | Separates entries within a drop group |
| Dash separator | `-` | Chest loot, fishing loot, drop entry payloads |

**Key behavior:** `"a_____b_____".split("_____")` yields `["a", "b"]`; the trailing empty field is discarded. This **must** be simulated using `Splitter.splitRegex()`, not raw `String.split()` calls in loader code.

---

### §1 DropInfo — Drop Entry Grammar

**Source:** JAR method `LootPPHelper.getDropInfo`
**Used in:** `block_drops/adding.txt`, `entity_drops/adding.txt`

#### §1.1 Drop Group Outer Syntax (EBNF)

```ebnf
drop_group := drop_entry ( "%%%%%" drop_entry )* ;
drop_entry := type_char "-" payload ;
type_char  := "i" | "e" | "c" ;
```

Split rule: find only the **first** `-` in the entry string.
`type_char = s.charAt(0)`, `payload = s.substring(firstDashIndex + 1)`.
The payload may itself contain additional `-` characters.

**Weight semantics:** Only the **first** entry's weight in a `%%%%%`-separated group participates in the weighted roll. If the group is selected, **all** entries in the group execute. This is a 1:1 replication of Loot++ 1.8.9 behavior and must not be altered.

#### §1.2 Item Drop (`i-`) Payload

**JAR split:** `payload.split("-", 6)` (limit = 6)

```ebnf
item_payload := item_id "-" min_count
               [ "-" max_count
               [ "-" weight
               [ "-" metadata
               [ "-" nbt_json ] ] ] ] ;
```

| Field | Type | Default | Clamp / Notes |
|-------|------|---------|---------------|
| `item_id` | String | — | Resolved via `Item.getByNameOrId(string)` (1.8.9 semantics); Legacy WARN required |
| `min_count` | int | — | `< 0` → `0` |
| `max_count` | int | `min_count` | `< min_count` → `max_count = min_count` |
| `weight` | int | `1` | `≤ 0` → `1` |
| `metadata` | int | `0` | `< 0` → `0`; Legacy WARN required (metadata concept maps to 1.18.2 flat IDs) |
| `nbt_json` | String | `null` | If provided: `trim()` then `JsonToNBT.parse`; failure → `notifyNBT` + empty tag (lenient) |

Legacy WARN: any old item name or namespace must call `LegacyWarnReporter.warn()` with type `ItemId`.

#### §1.3 Entity Drop (`e-`) Payload

**JAR split:** `payload.split("-", 3)` (limit = 3)

```ebnf
entity_payload := entity_id [ "-" weight ] [ "-" nbt_json ] ;
```

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `entity_id` | String | — | Legacy mob names → 1.18.2 IDs; WARN with type `EntityId` |
| `weight` | int | `1` | — |
| `nbt_json` | String | `"{}"` | JAR force-overrides `id` field: `nbt.setString("id", entity_id)` regardless of any `id` present in the supplied NBT |

#### §1.4 Command Drop (`c-`) Payload

**JAR split:** `payload.split("-", 2)` (limit = 2)

```ebnf
command_payload := weight "-" command_string ;
```

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `weight` | int | — | `≤ 0` → `1` |
| `command_string` | String | — | May contain `-`; 1.8 command syntax; Legacy WARN required |

---

### §2 `config/block_drops/*.txt`

#### §2.1 `removing.txt`

**JAR split:** `line.split("_____")`

```ebnf
line := block_id "_____" block_meta "_____" item_id
       [ "_____" item_meta ]
       [ "_____" nbt_json ] ;
```

| Field | Type | Default | Clamp / Notes |
|-------|------|---------|---------------|
| `block_id` | String | — | Legacy WARN: type `BlockId` |
| `block_meta` | int | — | `< 0` → `32767` (wildcard ANY); WARN: type `MetaWildcard` |
| `item_id` | String | — | `equalsIgnoreCase("any")` or `equalsIgnoreCase("all")` → special dummy item matching all items |
| `item_meta` | int | `-1` | `< 0` → `32767` (wildcard ANY); WARN: type `MetaWildcard` |
| `nbt_json` | String | `"{}"` | `""` or `"{}"` → no NBT filter; else `trim()` then `JsonToNBT.parse` |

#### §2.2 `adding.txt`

**JAR split (outer):** `line.split("_____")`

```ebnf
line   := header "_____" drop_group ( "_____" drop_group )* ;
header := block_id "-" rarity "-" only_player_mined "-" drop_with_silk
         "-" affected_by_fortune [ "-" block_meta ] ;
```

**Header split:** `header.split("-")`, requires ≥ 5 segments.

| Header field | Type | Default | Notes |
|---|---|---|---|
| `block_id` | String | — | Legacy WARN: type `BlockId` |
| `rarity` | float | — | `Float.valueOf`; JAR has a clamp operation whose result is immediately popped — effectively no clamp applied |
| `only_player_mined` | boolean | — | `Boolean.valueOf`: only `"true"` (case-insensitive) → `true`; `"1"` → `false` |
| `drop_with_silk` | boolean | — | Same `Boolean.valueOf` semantics |
| `affected_by_fortune` | boolean | — | Same `Boolean.valueOf` semantics |
| `block_meta` | int | `-1` | `< 0` → `32767` (wildcard ANY); WARN: type `MetaWildcard` |

An empty drop group string `""` (arising from consecutive `_____` separators) is silently skipped.

---

### §3 `config/entity_drops/*.txt`

Structure mirrors `block_drops/`. Drop group grammar is identical to §1.

**`removing.txt` format** (inferred from JAR constants):

```ebnf
line := entity_name "_____" entity_nbt "_____" item_name
       [ "_____" meta ]
       [ "_____" nbt_json ] ;
```

**`adding.txt`:** Drop groups follow the identical grammar defined in §1.

---

### §4 `config/item_effects/*.txt`

**Source:** JAR class `ConfigLoaderEffects`

**Trigger file names:**

| Category | Files |
|---|---|
| Potion triggers | `held`, `in_inventory`, `hitting_entity`, `hitting_entity_to_entity`, `wearing_armour`, `blocks_in_inventory`, `standing_on_block`, `breaking_block`, `near_entity`, `near_entity_to_entity` |
| Command triggers | `command_held`, `command_in_inventory`, `command_hitting_entity`, `command_wearing_armour`, `command_blocks_in_inventory`, `command_standing_on_block`, `command_breaking_block`, `command_near_entity` |

#### §4.1 Item Potion Effect

**Source:** JAR method `loadPotionEffect`
**Requires:** ≥ 8 segments after `split("_____")`

```ebnf
line := item_name "_____" item_meta "_____" item_nbt
       "_____" potion_id "_____" duration "_____" amplifier
       "_____" probability "_____" particle_type
       [ "_____" extra_armour1 "_____" extra_armour2 "_____" extra_armour3 ] ;
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `item_name` | String | — | `"any"` → dummy item matching any held item |
| `item_meta` | int | — | `< 0` → `32767` (ANY); WARN: type `MetaWildcard` |
| `item_nbt` | String | — | `""` or `"{}"` → no filter; else `trim()` then `JsonToNBT.parse` |
| `potion_id` | String | — | First try resource name; then try numeric ID; WARN if numeric: type `EffectName` |
| `duration` | int | `10` | Parse failure → `notifyNumber` + default |
| `amplifier` | int | `0` | Parse failure → `notifyNumber` + default |
| `probability` | float | `1.0` | Parse failure → `notifyNumber` + default |
| `particle_type` | String | — | — |

**Set-bonus (`wearing_armour` only):** Reads `parts[8]`, `parts[9]`, `parts[10]` as three extra armour item IDs. **Index 8 is intentional per JAR.** The `command_wearing_armour` trigger reads the same indices, which means set-bonus for command triggers requires placeholder fields at positions 4–7 to shift the armour IDs to the correct index.

#### §4.2 Item Command Effect

**Source:** JAR method `loadCommandEffect`
**Requires:** ≥ 5 segments after `split("_____")`

```ebnf
line := item_name "_____" item_meta "_____" item_nbt
       "_____" probability "_____" command ;
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `probability` | float | `1.0` | Parse failure → `1.0` |
| `command` | String | — | 1.8 command syntax; Legacy WARN required |

**Set-bonus quirk:** `command_wearing_armour` reads `parts[8..10]` for set-bonus armour IDs using the same indices as §4.1.

#### §4.3 Block Effects

**`blocks_in_inventory`, `standing_on_block`, `breaking_block`, `near_entity`, `near_entity_to_entity`**

**Block Potion Effect — requires ≥ 7 segments:**

```ebnf
line := block_name "_____" block_meta "_____" potion_id
       "_____" duration "_____" amplifier "_____" probability
       "_____" particle_type ;
```

- `block_name "any"` → special sentinel block `blockCommandBlockTrigger` (matches any block)
- `block_name "air"` or `"minecraft:air"` → resolves to air block (literal match only)

**Block Command Effect — requires ≥ 4 segments:**

```ebnf
line := block_name "_____" block_meta "_____" probability "_____" command ;
```

---

### §5 `config/chest_content/*.txt`

**Source:** JAR class `ConfigLoaderChestLoot`
**Extras keys:** `chest_loot`, `chest_amounts`

#### §5.1 `chest_amounts.txt`

**JAR split:** `line.split("-")`, requires exactly 3 segments.

```ebnf
line := chest_type "-" min_items "-" max_items ;
```

| Field | Type | Clamp |
|---|---|---|
| `min_items` | int | `< 0` → `0` |
| `max_items` | int | `< min_items` → `max_items = min_items` |

Legacy WARN: `chest_type` (1.8 Forge `ChestGenHooks` name → 1.18.2 loot table ID); type `LegacyChestType`.

#### §5.2 `chest_loot.txt`

**JAR split:** `line.split("-", 7)` (limit = 7), requires ≥ 5 segments.

```ebnf
line := chest_type "-" item_name "-" min "-" max "-" weight
       [ "-" metadata ] [ "-" nbt_json ] ;
```

| Field | Type | Default | Clamp / Notes |
|---|---|---|---|
| `metadata` | int | `0` | `< 0` → `0` |
| `nbt_json` | String | `"{}"` | `""` or `"{}"` → none; else `trim()` then `JsonToNBT.parse` |
| `min` | int | — | `< 0` → `0` |
| `max` | int | — | `< min` → `max = min` |
| `weight` | int | — | `< 0` → `0` (**note:** chest uses `< 0 → 0`, not `≤ 0 → 1` as in drops) |

If `chest_type` has no previously defined min/max, the JAR defaults: `setMin = 3`, `setMax = 9`.
Legacy WARN: `chest_type` mapping; old item names — types `LegacyChestType`, `ItemId`.

---

### §6 `config/fishing_loot/*.txt`

**Source:** JAR class `ConfigLoaderFishingLoot`
**Files:** `fish_additions.txt`, `junk_additions.txt`, `treasure_additions.txt`

#### §6.1 Fishing Loot Entry

**JAR split:** `line.split("-", 7)` (limit = 7), requires ≥ 5 segments.

```ebnf
line := item_name "-" stack_size "-" enchant_percent "-" enchanted
       "-" rarity [ "-" damage ] [ "-" nbt_json ] ;
```

| Field | Type | Default | Clamp / Notes |
|---|---|---|---|
| `stack_size` | int | — | `< 1` → `1` |
| `enchant_percent` | int | — | `< 0` → `0` |
| `enchanted` | boolean | — | `Boolean.valueOf`: only `"true"` is true |
| `rarity` | int | — | `< 1` → `1` |
| `damage` | int | `0` | `< 0` → `0` |
| `nbt_json` | String | `null` | `""` or `"{}"` → none; else `trim()` then `JsonToNBT.parse`; failure → `notifyNBT` + `null` (skip line) |

Blank lines or lines beginning with `#` → `null` (ignored).
Legacy WARN: 1.8 fishing loot category → 1.18.2 loot table pool; type `LegacyChestType`.

#### §6.2 `fishing_amounts.txt`

Grammar not fully verified from bytecode. Expected format:

```
loot_category "-" min "-" max
```

where `loot_category` is one of `junk`, `treasure`, or `fish`.

---

### §7 `config/world_gen/*.txt`

**Source:** JAR class `ConfigLoaderWorldGen`
**Extras keys:** `surface`, `underground`

#### §7.1 `surface.txt`

**JAR split:** `line.split("_____")`, requires **exactly 20 segments**.

```ebnf
line := block_name "_____" block_meta "_____" block_nbt "_____" bonemeal
       "_____" chance_per_chunk "_____" tries_per_chunk "_____" group_size
       "_____" tries_per_group "_____" height_min "_____" height_max
       "_____" beneath_block_blacklist "_____" beneath_block_whitelist
       "_____" beneath_material_blacklist "_____" beneath_material_whitelist
       "_____" biome_blacklist "_____" biome_whitelist
       "_____" biome_type_blacklist "_____" biome_type_whitelist
       "_____" dimension_blacklist "_____" dimension_whitelist ;
```

Any segment count other than 20 → wrong-parts error; line is skipped.

List fields (block / biome / dimension blacklist & whitelist) use `-` as an internal separator. A single `-` split by Java yields an empty array, meaning "no condition".

`block_nbt`: `"{}"` → none; else `trim()` then `JsonToNBT.parse`.

Legacy WARN: 1.8 dimension IDs; biome type system → 1.18.2.

#### §7.2 `underground.txt`

**JAR split:** `line.split("_____")`, requires **exactly 19 segments**.

```ebnf
line := block_name "_____" block_meta "_____" block_nbt
       "_____" chance_per_chunk "_____" tries_per_chunk
       "_____" vein_len_min [ "-" vein_len_max ]
       "_____" vein_thick_min "-" vein_thick_max
       "_____" height_min "_____" height_max
       "_____" block_blacklist "_____" block_whitelist
       "_____" beneath_material_blacklist "_____" beneath_material_whitelist
       "_____" biome_blacklist "_____" biome_whitelist
       "_____" biome_type_blacklist "_____" biome_type_whitelist
       "_____" dimension_blacklist "_____" dimension_whitelist ;
```

`vein_len_max` is optional per JAR.

---

### §8 Item and Block Additions — File Index

#### §8.1 `config/item_additions/` Keys

| Key | Item type |
|---|---|
| `generic_items` | Generic item |
| `foods` | Food item |
| `thrown` | Throwable item |
| `bows` | Bow |
| `guns` | Ranged weapon |
| `multitools` | Multi-tool |
| `materials` | Material / ingredient |
| `swords` | Sword |
| `pickaxes` | Pickaxe |
| `axes` | Axe |
| `shovels` | Shovel |
| `hoes` | Hoe |
| `helmets` | Helmet armour |
| `chestplates` | Chestplate armour |
| `leggings` | Leggings armour |
| `boots` | Boots armour |

#### §8.2 `config/block_additions/` Keys

| Key | Block type |
|---|---|
| `blocks` | Generic block |
| `blocks_with_states` | Block with blockstate properties |
| `glowing_blocks` | Emissive block |
| `ores` | Ore block |
| `bonemeal_flowers` | Bonemeal-growable flower |
| `flowers` | Flower |
| `plants` | Plant / crop |
| `slabs` | Slab |
| `stairs` | Stair |
| `trapdoors` | Trapdoor |
| `pressure_plates` | Pressure plate |
| `buttons` | Button |
| `doors` | Door |
| `fences` | Fence |
| `fence_gates` | Fence gate |
| `walls` | Wall |
| `panes` | Pane |
| `colored_blocks` | Dyeable colored block |

All lines in all addition files use `_____` as the field separator. Comment lines (`#`, `//`) and blank lines are skipped per §0.

#### §8.3 Implementation Status

| Category | Status |
|---|---|
| Item: generic, food, sword, axe, shovel, pickaxe, hoe, bow, armor | Implemented |
| Block: generic (blocks), plants, crops, cakes | Implemented |
| Item: bows / guns / multitools — full projectile/ammo behavior | **Deferred** |
| Block: buttons, pressure_plates, slabs, stairs, panes, walls | **Deferred** |

---

## 中文版本（Chinese Version）

### §0 全局文件读取规则

配置文本文件逐行读取。各行在交由加载器处理之前，**不**进行预裁剪（trim），**不**进行预过滤。加载器接收到的是 zip 条目或文件流中原始的行内容。

**空行：**
满足 `line.equals("")` 的行静默跳过。

**注释行：**
当且仅当 `line.length() > 0 && line.charAt(0) == '#'` 时，该行被视为注释。含有前导空格的行（如 `  #comment`）**不**是注释——JAR 在字符判断前不进行 trim 操作。

**扩展（非 JAR 原生）：** 首字符为 `/` 且第二字符也为 `/`（即 `//`）的行同样被视为注释并静默跳过。此扩展是为了兼容使用 C 风格注释的插件包。

注释行静默跳过，或以 `notifyWrongNumberOfParts(comment=true)` 传递，不产生任何错误或警告输出。

**文件编码：**
首先以 UTF-8 严格模式读取；解码失败时回退至 ISO-8859-1 / CP1252。在处理前剥去前导 BOM（`\uFEFF`）。

---

### §0.3 分割语义

JAR 使用 Java `String.split(regex)`，默认 `limit=0`，该模式丢弃末尾的空字段。所有实现必须精确复现此行为。

| 分隔符 | 字面量 | 使用场景 |
|---|---|---|
| 字段分隔符 | `_____`（五个下划线）| 效果、掉落物、世界生成、物品/方块添加 |
| 掉落组分隔符 | `%%%%%`（五个百分号）| 分隔掉落组内的条目 |
| 短横线分隔符 | `-` | 宝箱战利品、钓鱼战利品、掉落条目载荷 |

**关键行为：** `"a_____b_____".split("_____")` 得到 `["a", "b"]`；末尾空字段被丢弃。加载器代码中**必须**使用 `Splitter.splitRegex()`，不得直接调用原始 `String.split()`。

---

### §1 DropInfo — 掉落条目语法

**来源：** JAR 方法 `LootPPHelper.getDropInfo`
**使用于：** `block_drops/adding.txt`、`entity_drops/adding.txt`

#### §1.1 掉落组外层语法（EBNF）

```ebnf
drop_group := drop_entry ( "%%%%%" drop_entry )* ;
drop_entry := type_char "-" payload ;
type_char  := "i" | "e" | "c" ;
```

分割规则：仅查找条目字符串中的**第一个** `-`。
`type_char = s.charAt(0)`，`payload = s.substring(firstDashIndex + 1)`。
载荷本身可包含更多 `-` 字符。

**权重语义：** 在以 `%%%%%` 分隔的组中，仅**第一个**条目的权重参与加权抽取。若该组被选中，组内**所有**条目均执行。此行为与 Loot++ 1.8.9 完全一致，不得更改。

#### §1.2 物品掉落（`i-`）载荷

**JAR 分割：** `payload.split("-", 6)`（limit = 6）

```ebnf
item_payload := item_id "-" min_count
               [ "-" max_count
               [ "-" weight
               [ "-" metadata
               [ "-" nbt_json ] ] ] ] ;
```

| 字段 | 类型 | 默认值 | 钳制 / 说明 |
|---|---|---|---|
| `item_id` | 字符串 | — | 通过 `Item.getByNameOrId(string)` 解析（1.8.9 语义）；须发出 Legacy WARN |
| `min_count` | int | — | `< 0` → `0` |
| `max_count` | int | `min_count` | `< min_count` → `max_count = min_count` |
| `weight` | int | `1` | `≤ 0` → `1` |
| `metadata` | int | `0` | `< 0` → `0`；须发出 Legacy WARN（metadata 概念映射为 1.18.2 扁平 ID）|
| `nbt_json` | 字符串 | `null` | 若提供：`trim()` 后执行 `JsonToNBT.parse`；解析失败 → `notifyNBT` + 空标签（宽容模式）|

Legacy WARN：任何旧物品名称或命名空间须调用 `LegacyWarnReporter.warn()`，类型为 `ItemId`。

#### §1.3 实体掉落（`e-`）载荷

**JAR 分割：** `payload.split("-", 3)`（limit = 3）

```ebnf
entity_payload := entity_id [ "-" weight ] [ "-" nbt_json ] ;
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `entity_id` | 字符串 | — | 旧怪物名称 → 1.18.2 ID；WARN 类型 `EntityId` |
| `weight` | int | `1` | — |
| `nbt_json` | 字符串 | `"{}"` | JAR 强制覆盖 `id` 字段：`nbt.setString("id", entity_id)`，忽略 NBT 中已有的任何 `id` |

#### §1.4 命令掉落（`c-`）载荷

**JAR 分割：** `payload.split("-", 2)`（limit = 2）

```ebnf
command_payload := weight "-" command_string ;
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `weight` | int | — | `≤ 0` → `1` |
| `command_string` | 字符串 | — | 可含 `-`；1.8 命令语法；须发出 Legacy WARN |

---

### §2 `config/block_drops/*.txt`

#### §2.1 `removing.txt`

**JAR 分割：** `line.split("_____")`

```ebnf
line := block_id "_____" block_meta "_____" item_id
       [ "_____" item_meta ]
       [ "_____" nbt_json ] ;
```

| 字段 | 类型 | 默认值 | 钳制 / 说明 |
|---|---|---|---|
| `block_id` | 字符串 | — | Legacy WARN：类型 `BlockId` |
| `block_meta` | int | — | `< 0` → `32767`（通配符 ANY）；WARN：类型 `MetaWildcard` |
| `item_id` | 字符串 | — | `equalsIgnoreCase("any")` 或 `equalsIgnoreCase("all")` → 匹配所有物品的特殊虚拟物品 |
| `item_meta` | int | `-1` | `< 0` → `32767`（通配符 ANY）；WARN：类型 `MetaWildcard` |
| `nbt_json` | 字符串 | `"{}"` | `""` 或 `"{}"` → 无 NBT 过滤；否则 `trim()` 后执行 `JsonToNBT.parse` |

#### §2.2 `adding.txt`

**JAR 分割（外层）：** `line.split("_____")`

```ebnf
line   := header "_____" drop_group ( "_____" drop_group )* ;
header := block_id "-" rarity "-" only_player_mined "-" drop_with_silk
         "-" affected_by_fortune [ "-" block_meta ] ;
```

**头部分割：** `header.split("-")`，要求 ≥ 5 个段。

| 头部字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `block_id` | 字符串 | — | Legacy WARN：类型 `BlockId` |
| `rarity` | float | — | `Float.valueOf`；JAR 存在钳制操作但结果被弹出——实际上无钳制 |
| `only_player_mined` | boolean | — | `Boolean.valueOf`：仅 `"true"`（大小写不敏感）为 `true`；`"1"` 为 `false` |
| `drop_with_silk` | boolean | — | 同上 |
| `affected_by_fortune` | boolean | — | 同上 |
| `block_meta` | int | `-1` | `< 0` → `32767`（通配符 ANY）；WARN：类型 `MetaWildcard` |

空掉落组字符串 `""`（由连续 `_____` 分隔符产生）静默跳过。

---

### §3 `config/entity_drops/*.txt`

结构与 `block_drops/` 相同。掉落组语法与 §1 完全一致。

**`removing.txt` 格式**（从 JAR 常量推断）：

```ebnf
line := entity_name "_____" entity_nbt "_____" item_name
       [ "_____" meta ]
       [ "_____" nbt_json ] ;
```

**`adding.txt`：** 掉落组遵循 §1 所定义的完全相同语法。

---

### §4 `config/item_effects/*.txt`

**来源：** JAR 类 `ConfigLoaderEffects`

**触发器文件名：**

| 分类 | 文件 |
|---|---|
| 药水效果触发器 | `held`、`in_inventory`、`hitting_entity`、`hitting_entity_to_entity`、`wearing_armour`、`blocks_in_inventory`、`standing_on_block`、`breaking_block`、`near_entity`、`near_entity_to_entity` |
| 命令触发器 | `command_held`、`command_in_inventory`、`command_hitting_entity`、`command_wearing_armour`、`command_blocks_in_inventory`、`command_standing_on_block`、`command_breaking_block`、`command_near_entity` |

#### §4.1 物品药水效果

**来源：** JAR 方法 `loadPotionEffect`
**要求：** `split("_____")` 后 ≥ 8 个段

```ebnf
line := item_name "_____" item_meta "_____" item_nbt
       "_____" potion_id "_____" duration "_____" amplifier
       "_____" probability "_____" particle_type
       [ "_____" extra_armour1 "_____" extra_armour2 "_____" extra_armour3 ] ;
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `item_name` | 字符串 | — | `"any"` → 匹配任意手持物品的虚拟物品 |
| `item_meta` | int | — | `< 0` → `32767`（ANY）；WARN：类型 `MetaWildcard` |
| `item_nbt` | 字符串 | — | `""` 或 `"{}"` → 无过滤；否则 `trim()` 后执行 `JsonToNBT.parse` |
| `potion_id` | 字符串 | — | 先尝试资源名；再尝试数字 ID；若为数字则 WARN：类型 `EffectName` |
| `duration` | int | `10` | 解析失败 → `notifyNumber` + 默认值 |
| `amplifier` | int | `0` | 解析失败 → `notifyNumber` + 默认值 |
| `probability` | float | `1.0` | 解析失败 → `notifyNumber` + 默认值 |
| `particle_type` | 字符串 | — | — |

**套装加成（仅 `wearing_armour`）：** 读取 `parts[8]`、`parts[9]`、`parts[10]` 作为三件额外护甲物品 ID。**索引 8 是 JAR 的确切行为。** `command_wearing_armour` 触发器读取相同的索引，这意味着命令触发器的套装加成需要在第 4–7 位置填入占位字段，以将护甲 ID 移至正确索引。

#### §4.2 物品命令效果

**来源：** JAR 方法 `loadCommandEffect`
**要求：** `split("_____")` 后 ≥ 5 个段

```ebnf
line := item_name "_____" item_meta "_____" item_nbt
       "_____" probability "_____" command ;
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `probability` | float | `1.0` | 解析失败 → `1.0` |
| `command` | 字符串 | — | 1.8 命令语法；须发出 Legacy WARN |

**套装加成怪癖：** `command_wearing_armour` 使用与 §4.1 相同的索引读取 `parts[8..10]` 的套装加成护甲 ID。

#### §4.3 方块效果

**适用触发器：** `blocks_in_inventory`、`standing_on_block`、`breaking_block`、`near_entity`、`near_entity_to_entity`

**方块药水效果——要求 ≥ 7 个段：**

```ebnf
line := block_name "_____" block_meta "_____" potion_id
       "_____" duration "_____" amplifier "_____" probability
       "_____" particle_type ;
```

- `block_name "any"` → 特殊哨兵方块 `blockCommandBlockTrigger`（匹配任意方块）
- `block_name "air"` 或 `"minecraft:air"` → 解析为空气方块（仅字面匹配）

**方块命令效果——要求 ≥ 4 个段：**

```ebnf
line := block_name "_____" block_meta "_____" probability "_____" command ;
```

---

### §5 `config/chest_content/*.txt`

**来源：** JAR 类 `ConfigLoaderChestLoot`
**Extras 键：** `chest_loot`、`chest_amounts`

#### §5.1 `chest_amounts.txt`

**JAR 分割：** `line.split("-")`，要求恰好 3 个段。

```ebnf
line := chest_type "-" min_items "-" max_items ;
```

| 字段 | 类型 | 钳制 |
|---|---|---|
| `min_items` | int | `< 0` → `0` |
| `max_items` | int | `< min_items` → `max_items = min_items` |

Legacy WARN：`chest_type`（1.8 Forge `ChestGenHooks` 名称 → 1.18.2 战利品表 ID）；类型 `LegacyChestType`。

#### §5.2 `chest_loot.txt`

**JAR 分割：** `line.split("-", 7)`（limit = 7），要求 ≥ 5 个段。

```ebnf
line := chest_type "-" item_name "-" min "-" max "-" weight
       [ "-" metadata ] [ "-" nbt_json ] ;
```

| 字段 | 类型 | 默认值 | 钳制 / 说明 |
|---|---|---|---|
| `metadata` | int | `0` | `< 0` → `0` |
| `nbt_json` | 字符串 | `"{}"` | `""` 或 `"{}"` → 无；否则 `trim()` 后执行 `JsonToNBT.parse` |
| `min` | int | — | `< 0` → `0` |
| `max` | int | — | `< min` → `max = min` |
| `weight` | int | — | `< 0` → `0`（**注意：** 宝箱使用 `< 0 → 0`，而非掉落物的 `≤ 0 → 1`）|

若 `chest_type` 尚未定义 min/max，JAR 默认值为：`setMin = 3`，`setMax = 9`。
Legacy WARN：`chest_type` 映射；旧物品名称——类型 `LegacyChestType`、`ItemId`。

---

### §6 `config/fishing_loot/*.txt`

**来源：** JAR 类 `ConfigLoaderFishingLoot`
**文件：** `fish_additions.txt`、`junk_additions.txt`、`treasure_additions.txt`

#### §6.1 钓鱼战利品条目

**JAR 分割：** `line.split("-", 7)`（limit = 7），要求 ≥ 5 个段。

```ebnf
line := item_name "-" stack_size "-" enchant_percent "-" enchanted
       "-" rarity [ "-" damage ] [ "-" nbt_json ] ;
```

| 字段 | 类型 | 默认值 | 钳制 / 说明 |
|---|---|---|---|
| `stack_size` | int | — | `< 1` → `1` |
| `enchant_percent` | int | — | `< 0` → `0` |
| `enchanted` | boolean | — | `Boolean.valueOf`：仅 `"true"` 为 true |
| `rarity` | int | — | `< 1` → `1` |
| `damage` | int | `0` | `< 0` → `0` |
| `nbt_json` | 字符串 | `null` | `""` 或 `"{}"` → 无；否则 `trim()` 后执行 `JsonToNBT.parse`；失败 → `notifyNBT` + `null`（跳过该行）|

空行或以 `#` 开头的行 → `null`（忽略）。
Legacy WARN：1.8 钓鱼战利品分类 → 1.18.2 战利品表池；类型 `LegacyChestType`。

#### §6.2 `fishing_amounts.txt`

语法未从字节码中完全验证。预期格式：

```
loot_category "-" min "-" max
```

其中 `loot_category` 为 `junk`、`treasure` 或 `fish` 之一。

---

### §7 `config/world_gen/*.txt`

**来源：** JAR 类 `ConfigLoaderWorldGen`
**Extras 键：** `surface`、`underground`

#### §7.1 `surface.txt`

**JAR 分割：** `line.split("_____")`，要求**恰好 20 个**段。

```ebnf
line := block_name "_____" block_meta "_____" block_nbt "_____" bonemeal
       "_____" chance_per_chunk "_____" tries_per_chunk "_____" group_size
       "_____" tries_per_group "_____" height_min "_____" height_max
       "_____" beneath_block_blacklist "_____" beneath_block_whitelist
       "_____" beneath_material_blacklist "_____" beneath_material_whitelist
       "_____" biome_blacklist "_____" biome_whitelist
       "_____" biome_type_blacklist "_____" biome_type_whitelist
       "_____" dimension_blacklist "_____" dimension_whitelist ;
```

段数不等于 20 时触发 wrong-parts 错误，该行被跳过。

列表字段（方块 / 生物群系 / 维度的黑名单与白名单）以 `-` 作为内部分隔符。单个 `-` 经 Java 分割后得到空数组，表示"无条件"。

`block_nbt`：`"{}"` → 无；否则 `trim()` 后执行 `JsonToNBT.parse`。

Legacy WARN：1.8 维度 ID；生物群系类型系统 → 1.18.2。

#### §7.2 `underground.txt`

**JAR 分割：** `line.split("_____")`，要求**恰好 19 个**段。

```ebnf
line := block_name "_____" block_meta "_____" block_nbt
       "_____" chance_per_chunk "_____" tries_per_chunk
       "_____" vein_len_min [ "-" vein_len_max ]
       "_____" vein_thick_min "-" vein_thick_max
       "_____" height_min "_____" height_max
       "_____" block_blacklist "_____" block_whitelist
       "_____" beneath_material_blacklist "_____" beneath_material_whitelist
       "_____" biome_blacklist "_____" biome_whitelist
       "_____" biome_type_blacklist "_____" biome_type_whitelist
       "_____" dimension_blacklist "_____" dimension_whitelist ;
```

`vein_len_max` 在 JAR 中为可选字段。

---

### §8 物品与方块添加 — 文件索引

#### §8.1 `config/item_additions/` 键

| 键 | 物品类型 |
|---|---|
| `generic_items` | 通用物品 |
| `foods` | 食物 |
| `thrown` | 投掷物 |
| `bows` | 弓 |
| `guns` | 远程武器 |
| `multitools` | 多功能工具 |
| `materials` | 材料 / 原料 |
| `swords` | 剑 |
| `pickaxes` | 镐 |
| `axes` | 斧 |
| `shovels` | 锹 |
| `hoes` | 锄 |
| `helmets` | 头盔 |
| `chestplates` | 胸甲 |
| `leggings` | 护腿 |
| `boots` | 靴子 |

#### §8.2 `config/block_additions/` 键

| 键 | 方块类型 |
|---|---|
| `blocks` | 通用方块 |
| `blocks_with_states` | 含方块状态的方块 |
| `glowing_blocks` | 发光方块 |
| `ores` | 矿石方块 |
| `bonemeal_flowers` | 可用骨粉生长的花 |
| `flowers` | 花 |
| `plants` | 植物 / 农作物 |
| `slabs` | 台阶 |
| `stairs` | 楼梯 |
| `trapdoors` | 活板门 |
| `pressure_plates` | 压力板 |
| `buttons` | 按钮 |
| `doors` | 门 |
| `fences` | 栅栏 |
| `fence_gates` | 栅栏门 |
| `walls` | 墙 |
| `panes` | 栏杆 / 玻璃板 |
| `colored_blocks` | 可染色方块 |

所有添加文件中的每一行均以 `_____` 作为字段分隔符。注释行（`#`、`//`）和空行按 §0 规则跳过。

#### §8.3 实现状态

| 分类 | 状态 |
|---|---|
| 物品：generic、food、sword、axe、shovel、pickaxe、hoe、bow、armor | 已实现 |
| 方块：generic (blocks)、plants、crops、cakes | 已实现 |
| 物品：bows / guns / multitools — 完整投射物/弹药行为 | **延迟实现** |
| 方块：buttons、pressure_plates、slabs、stairs、panes、walls | **延迟实现** |
