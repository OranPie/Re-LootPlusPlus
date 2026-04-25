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

### §9 Lucky Block Addon — `drops.txt` Grammar

**Source classes:** `lucky/attr/LuckyAttrParser.java`, `lucky/drop/LuckyDropParser.java`, `lucky/drop/LuckyDropLine.java`

Lucky Block addon drop files (`drops.txt`, `bow_drops.txt`, `sword_drops.txt`, `potion_drops.txt`) use a `key=value` attribute format that is entirely distinct from the `_____`-separated Loot++ config format. This format is parsed by `LuckyAttrParser` and `LuckyDropParser`.

#### §9.1 Grammar (EBNF)

```ebnf
drop-file     ::= (drop-line | comment | blank)*
drop-line     ::= (regular-drop | group-drop) luck-suffix* chance-suffix?

regular-drop  ::= attr-pair ("," attr-pair)*
group-drop    ::= "group" count-spec? "(" group-entries ")"
group-entries ::= drop-line (";" drop-line)*
count-spec    ::= ":#" rand-expr ":"
rand-expr     ::= "rand(" integer "," integer ")"
               |  integer

luck-suffix   ::= "@luck=" integer
chance-suffix ::= "@chance=" float
               |  "@chance"          (* bare — normalised to chance=1; WARN LuckyAttrBareChance *)

attr-pair     ::= key "=" attr-value
attr-value    ::= string-value
               |  dict-value
               |  list-value

dict-value    ::= "(" attr-pair ("," attr-pair)* ")"
list-value    ::= "[" attr-value (";" attr-value)* "]"
string-value  ::= (* text not containing top-level "," / ";" / "(" / ")" / "[" / "]" / "=" *)

comment       ::= "/" [^\n]*
blank         ::= ""
```

The parser uses a bracket depth counter to correctly identify top-level delimiters `,` and `;`. Commas inside nested `dict (...)` or `list [...]` do not split attr-pairs.

`@luck` and `@chance` are scanned from the **end** of the line as top-level `@` tokens (outside all brackets). They are stripped before the attr-pairs portion is parsed.

#### §9.2 `@luck` and `@chance` Semantics

| Suffix | Meaning |
|---|---|
| `@luck=N` | Signed integer; used as `luckWeight` in the `LuckyDropRoller` weight formula |
| `@chance=P` | Float 0.0–1.0; tested **after** the luck-weighted roll selects this entry |
| `@chance` (bare) | Treated as `chance=1.0` with WARN `LuckyAttrBareChance` |

The `@chance` gate is independent of the luck roll. An entry with `@chance=0.5` is selected from the luck-weighted pool at its normal weight, then has a 50% independent probability of actually executing.

#### §9.3 Well-Known Attribute Keys

| Key | Applicable types | Meaning |
|---|---|---|
| `type` | all | Drop category; determines the action class. Default: `item` |
| `ID` | item, entity, block, structure | Target identifier (legacy or modern; remapped via `LegacyEntityIdFixer`) |
| `Count` | item | Stack size (integer ≥ 1) |
| `NBTTag` | item, entity | Dict-value containing the target's NBT compound |
| `posOffset` | entity, block | Relative position offset from drop origin: dict `(x=N,y=N,z=N)` |
| `spreading` | entity | Whether the spawned entity is launched outward (`true`/`false`) |
| `amount` | item | Synonym for `Count` in some addon packs |
| `command` | command | Command string executed via `LegacyCommandRunner` |
| `effectId` | effect | Status effect identifier (legacy name or modern `namespace:path`) |
| `duration` | effect | Effect duration in ticks |
| `amplifier` | effect | Effect amplifier (0-based integer) |
| `soundId` | sound | Sound event identifier (legacy or modern) |
| `message` | message | Chat message text (only sent if `dropChatEnabled=true`) |
| `luck` | any | Inline luck modifier (alias for `@luck` in some addon formats) |

Unrecognised attribute keys are not hard errors. The parser emits:

```
[WARN] [LootPP-Legacy] LuckyIgnoredField key '<fieldName>' not recognized for type='<type>' @ <packId>:<innerPath>:<lineNumber>
```

#### §9.4 Examples

**Regular drop — item:**
```
ID=diamond,Count=3@luck=2@chance=0.8
```
Drops 3 diamonds; luck weight 2; 80% chance gate.

**Regular drop — entity with NBT:**
```
type=entity,ID=Zombie,NBTTag=(CustomName="Lucky Zombie",CustomNameVisible=1)@luck=1
```

**Group — all entries execute together:**
```
group(ID=iron_sword;ID=iron_pickaxe;ID=iron_axe)@luck=0
```
All three items drop when this entry is selected.

**Group with count — pick N at random:**
```
group:#rand(2,3):(ID=diamond_sword;ID=diamond_pickaxe;ID=diamond_axe;ID=diamond_hoe)@luck=2
```
Selects 2 or 3 of the four items at random.

---

### §10 Lucky Block Addon — Drop File Variants and Fallback Chain

**Source class:** `lucky/loader/LuckyAddonLoader.java`

Lucky Block addon packs may include up to four drop file variants. Each is parsed separately at bootstrap Phase 4:

| File | Trigger | Fallback |
|---|---|---|
| `drops.txt` | Lucky Block break | (none) |
| `bow_drops.txt` | Lucky Bow shot | (none) |
| `sword_drops.txt` | Lucky Sword hit | Falls back to `bow_drops.txt` if empty |
| `potion_drops.txt` | Lucky Potion throw | Falls back to `bow_drops.txt` if empty |

Fallback is applied at the accessor level (`getMergedSwordDropLines()`, `getMergedPotionDropLines()`): if the specialized list is empty after loading, the accessor returns the bow drops list instead. This matches the original Lucky Block 1.8.9 addon convention.

All four file types share the same §9 grammar. Multiple addon packs' drop lists are **merged**: entries from all loaded packs are concatenated into a single flat list for each drop category.

---

### §11 Lucky Block Addon — `properties.txt`

**Source class:** `lucky/loader/LuckyAddonProperties.java`

One `key=value` pair per line. Lines beginning with `/` or `#` are comments; blank lines are ignored.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `spawnRate` | integer | `200` | Natural world-gen frequency — higher = rarer (approximately 1 per N chunks) |
| `structureChance` | integer | `2` | Probability denominator for structure-type drop selection |
| `doDropsOnCreativeMode` | boolean | `false` | Whether block break in Creative mode triggers drops |

When `properties.txt` is absent from a pack, `LuckyAddonProperties.DEFAULT` is used (all fields at their defaults).

**Example:**
```
/ Lucky Block Water Addon properties
spawnRate=150
structureChance=3
doDropsOnCreativeMode=false
```

---

### §12 Lucky Block Addon — `natural_gen.txt`

**Source class:** `lucky/loader/LuckyNaturalGenEntry.java`

Defines how and where Lucky Blocks generate naturally in the world. Uses dimension section headers followed by entries in drop-line format.

#### §12.1 Section Headers

| Header | Dimension |
|---|---|
| `>surface` | Overworld surface |
| `>underground` | Overworld underground (caves) |
| `>nether` | The Nether |
| `>end` | The End |

Lines beginning with `>` start a new dimension section. Before any header appears, the default section is `surface`.

#### §12.2 Entry Format

```
type=block,ID=<blockId>[,tileEntity=(Luck=<N>)]@chance=<rarity>
```

- `@chance=N` here is a **rarity denominator** (approximately 1 in N chunks), NOT a 0–1 probability gate.
- Default rarity when `@chance` is absent: `200`.
- `tileEntity=(Luck=N)` sets the initial luck value of the placed block.
- Only `type=block` entries are registered as world-gen features; group-type and structure-type entries emit a log line and are skipped.
- Lines starting with `/` are comments (or structure-file references) and are ignored.
- The feature is only registered when `naturalGenEnabled=true` in config.

#### §12.3 Example

```
>surface
type=block,ID=lucky:lucky_block,tileEntity=(Luck=0)@chance=200

>underground
type=block,ID=lucky:lucky_block,tileEntity=(Luck=-1)@chance=300

>nether
type=block,ID=lucky:lucky_block_nether,tileEntity=(Luck=-2)@chance=400
```

---

### §13 Lucky Block Addon — Template Variable Expansion

**Source class:** `lucky/template/LuckyTemplateVars.java`

Template variables appear inside attribute string values and are expanded at **drop evaluation time** (when the block is broken), not at parse time. Syntax: `#varName` or `#varName(arg,arg)`.

#### §13.1 Variable Reference

| Variable | Example | Result |
|---|---|---|
| `#rand(min,max)` | `#rand(1,5)` | Random integer in [min, max] inclusive |
| `#randFloat(min,max)` | `#randFloat(0.5,2.0)` | Random float in [min, max] |
| `#randList(a,b,c)` | `#randList(1,5,10)` | One randomly chosen value from the comma-separated list |
| `#randBool` | `#randBool` | Randomly `0` or `1` |
| `#randPosNeg(min,max)` | `#randPosNeg(1,5)` | Random integer with randomly positive or negative sign |
| `#circleOffset(r)` | `#circleOffset(3)` | Random XYZ offset on a circle of radius r |
| `#randLaunchMotion` | `#randLaunchMotion` | Random motion vector suitable for launched projectiles |
| `#bowMotion(speed)` | `#bowMotion(1.5)` | Motion matching a Lucky Bow trajectory at the given speed |
| `#motionFromDirection(h,v,speed)` | `#motionFromDirection(90,30,2.0)` | Motion from horizontal angle (degrees) + vertical angle (degrees) + speed |
| `#posX`, `#posY`, `#posZ` | `#posX` | World coordinates of the broken Lucky Block |
| `#pName` | `#pName` | Name of the player who broke the block |
| `#pX`, `#pY`, `#pZ` | `#pX` | Player's current world position coordinates |
| `#pUUID` | `#pUUID` | Player's UUID string |

Unknown `#token` patterns are left unchanged in the output to preserve compatibility with addon-specific template extensions.

After all template variables are expanded, simple arithmetic expressions (`N + M`, `N - M`) in integer or float positions are evaluated.

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

---

### §9 Lucky Block 附加包 — `drops.txt` 语法

**源类：** `lucky/attr/LuckyAttrParser.java`、`lucky/drop/LuckyDropParser.java`、`lucky/drop/LuckyDropLine.java`

Lucky Block 附加包的掉落文件（`drops.txt`、`bow_drops.txt`、`sword_drops.txt`、`potion_drops.txt`）采用 `key=value` 属性格式，与 `_____` 分隔的 Loot++ 配置格式完全不同。该格式由 `LuckyAttrParser` 和 `LuckyDropParser` 解析。

#### §9.1 语法（EBNF）

```ebnf
drop-file     ::= (drop-line | comment | blank)*
drop-line     ::= (regular-drop | group-drop) luck-suffix* chance-suffix?

regular-drop  ::= attr-pair ("," attr-pair)*
group-drop    ::= "group" count-spec? "(" group-entries ")"
group-entries ::= drop-line (";" drop-line)*
count-spec    ::= ":#" rand-expr ":"
rand-expr     ::= "rand(" integer "," integer ")"
               |  integer

luck-suffix   ::= "@luck=" integer
chance-suffix ::= "@chance=" float
               |  "@chance"          (* 裸形式 — 规范化为 chance=1；WARN LuckyAttrBareChance *)

attr-pair     ::= key "=" attr-value
attr-value    ::= string-value
               |  dict-value
               |  list-value

dict-value    ::= "(" attr-pair ("," attr-pair)* ")"
list-value    ::= "[" attr-value (";" attr-value)* "]"
string-value  ::= (* 不包含顶级 "," / ";" / "(" / ")" / "[" / "]" / "=" 的文本 *)

comment       ::= "/" [^\n]*
blank         ::= ""
```

解析器使用括号深度计数器来正确识别顶级分隔符 `,` 和 `;`。嵌套于 `(...)` 或 `[...]` 内的逗号不会拆分属性对。

`@luck` 与 `@chance` 从行末以顶级 `@` 标记的形式扫描识别（括号外），并在解析属性对之前从字符串中剥离。

#### §9.2 `@luck` 与 `@chance` 语义

| 后缀 | 含义 |
|---|---|
| `@luck=N` | 有符号整数；作为 `LuckyDropRoller` 权重公式中的 `luckWeight` 使用 |
| `@chance=P` | 浮点数 0.0–1.0；在幸运加权抽取选中该条目**之后**独立判断 |
| `@chance`（裸形式） | 视为 `chance=1.0`，并输出 WARN `LuckyAttrBareChance` |

`@chance` 概率门控与幸运抽取相互独立。含 `@chance=0.5` 的条目以其正常权重参与加权池的抽取，之后还有 50% 的独立概率实际执行。

#### §9.3 已知属性键

| 键 | 适用类型 | 含义 |
|---|---|---|
| `type` | 所有 | 掉落类别，决定处理该条目的动作类。默认：`item` |
| `ID` | item、entity、block、structure | 目标标识符（遗留或现代；通过 `LegacyEntityIdFixer` 映射） |
| `Count` | item | 堆叠数量（整数 ≥ 1） |
| `NBTTag` | item、entity | 包含目标 NBT 复合标签的字典值 |
| `posOffset` | entity、block | 相对于掉落原点的位置偏移：字典 `(x=N,y=N,z=N)` |
| `spreading` | entity | 生成实体是否向外抛射（`true`/`false`） |
| `amount` | item | 部分附加包中 `Count` 的同义词 |
| `command` | command | 通过 `LegacyCommandRunner` 执行的命令字符串 |
| `effectId` | effect | 状态效果标识符（遗留名称或现代 `namespace:path`） |
| `duration` | effect | 效果持续时间（tick 数） |
| `amplifier` | effect | 效果级别（从 0 起的整数） |
| `soundId` | sound | 声音事件标识符（遗留或现代） |
| `message` | message | 聊天消息文本（仅在 `dropChatEnabled=true` 时发送） |
| `luck` | 任意 | 内联幸运修正值（部分格式中 `@luck` 后缀的同义词） |

未识别的属性键不会触发硬性错误，解析器仅输出：

```
[WARN] [LootPP-Legacy] LuckyIgnoredField key '<fieldName>' not recognized for type='<type>' @ <packId>:<innerPath>:<lineNumber>
```

#### §9.4 示例

**普通掉落 — 物品：**
```
ID=diamond,Count=3@luck=2@chance=0.8
```
掉落 3 颗钻石；幸运权重 2；80% 概率门控。

**普通掉落 — 带 NBT 的实体：**
```
type=entity,ID=Zombie,NBTTag=(CustomName="Lucky Zombie",CustomNameVisible=1)@luck=1
```

**组（所有条目一同执行）：**
```
group(ID=iron_sword;ID=iron_pickaxe;ID=iron_axe)@luck=0
```
该条目被选中时，三把物品同时掉落。

**带数量的组（随机选择 N 个）：**
```
group:#rand(2,3):(ID=diamond_sword;ID=diamond_pickaxe;ID=diamond_axe;ID=diamond_hoe)@luck=2
```
从四件物品中随机选取 2 或 3 件。

---

### §10 Lucky Block 附加包 — 掉落文件类型与回退链

**源类：** `lucky/loader/LuckyAddonLoader.java`

Lucky Block 附加包最多可包含四种掉落文件类型，均在启动阶段 4 单独解析：

| 文件 | 触发场景 | 回退 |
|---|---|---|
| `drops.txt` | 破坏 Lucky 方块 | （无） |
| `bow_drops.txt` | 射出 Lucky 弓 | （无） |
| `sword_drops.txt` | Lucky 剑击中 | 为空时回退至 `bow_drops.txt` |
| `potion_drops.txt` | 投掷 Lucky 药水 | 为空时回退至 `bow_drops.txt` |

回退在访问器层面（`getMergedSwordDropLines()`、`getMergedPotionDropLines()`）应用：若特化列表在加载后为空，访问器则返回弓的掉落列表。这与原版 Lucky Block 1.8.9 附加包约定相符。

全部四类文件共享 §9 中的相同语法规则。多个附加包的掉落列表会被**合并**：所有已加载包的条目按每种掉落类别拼接为一个扁平列表。

---

### §11 Lucky Block 附加包 — `properties.txt`

**源类：** `lucky/loader/LuckyAddonProperties.java`

每行一个 `key=value` 键值对。以 `/` 或 `#` 开头的行为注释；空行忽略。

| 键 | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `spawnRate` | 整数 | `200` | 自然世界生成频率——数值越大越稀有（每 N 个区块约生成 1 次） |
| `structureChance` | 整数 | `2` | 结构类型掉落选择的概率分母 |
| `doDropsOnCreativeMode` | 布尔 | `false` | 在创造模式下破坏方块是否触发掉落 |

若附加包中不存在 `properties.txt`，则使用 `LuckyAddonProperties.DEFAULT`（所有字段取默认值）。

**示例：**
```
/ Lucky Block Water Addon 属性
spawnRate=150
structureChance=3
doDropsOnCreativeMode=false
```

---

### §12 Lucky Block 附加包 — `natural_gen.txt`

**源类：** `lucky/loader/LuckyNaturalGenEntry.java`

定义 Lucky 方块在世界中自然生成的位置和方式。采用维度分节标题，后跟掉落行格式的条目。

#### §12.1 分节标题

| 标题 | 维度 |
|---|---|
| `>surface` | 主世界地表 |
| `>underground` | 主世界地下（洞穴） |
| `>nether` | 下界 |
| `>end` | 末地 |

以 `>` 开头的行开始一个新的维度分节。在任何标题出现之前，默认分节为 `surface`。

#### §12.2 条目格式

```
type=block,ID=<blockId>[,tileEntity=(Luck=<N>)]@chance=<rarity>
```

- 此处的 `@chance=N` 是**稀有度分母**（大约每 N 个区块生成 1 次），而**非** 0–1 的概率门控。
- 未设置 `@chance` 时默认稀有度为 `200`。
- `tileEntity=(Luck=N)` 设置生成方块的初始幸运值。
- 只有 `type=block` 条目被注册为世界生成特性；组类型和结构类型条目记录日志后跳过。
- 以 `/` 开头的行为注释（或结构文件引用），一律忽略。
- 该特性仅在配置项 `naturalGenEnabled=true` 时注册。

#### §12.3 示例

```
>surface
type=block,ID=lucky:lucky_block,tileEntity=(Luck=0)@chance=200

>underground
type=block,ID=lucky:lucky_block,tileEntity=(Luck=-1)@chance=300

>nether
type=block,ID=lucky:lucky_block_nether,tileEntity=(Luck=-2)@chance=400
```

---

### §13 Lucky Block 附加包 — 模板变量展开

**源类：** `lucky/template/LuckyTemplateVars.java`

模板变量出现在属性字符串值内，在**掉落求值时**（即方块被破坏时）展开，而非解析时。语法为 `#varName` 或 `#varName(arg,arg)`。

#### §13.1 变量参考

| 变量 | 示例 | 结果 |
|---|---|---|
| `#rand(min,max)` | `#rand(1,5)` | [min, max] 范围内的随机整数（含边界） |
| `#randFloat(min,max)` | `#randFloat(0.5,2.0)` | [min, max] 范围内的随机浮点数 |
| `#randList(a,b,c)` | `#randList(1,5,10)` | 从逗号分隔列表中随机选取一个值 |
| `#randBool` | `#randBool` | 随机为 `0` 或 `1` |
| `#randPosNeg(min,max)` | `#randPosNeg(1,5)` | 符号随机（正或负）的随机整数 |
| `#circleOffset(r)` | `#circleOffset(3)` | 半径为 r 的圆上的随机 XYZ 偏移量 |
| `#randLaunchMotion` | `#randLaunchMotion` | 适用于弹射体的随机运动向量 |
| `#bowMotion(speed)` | `#bowMotion(1.5)` | 以给定速度匹配 Lucky 弓轨迹的运动向量 |
| `#motionFromDirection(h,v,speed)` | `#motionFromDirection(90,30,2.0)` | 由水平角（度）+ 垂直角（度）+ 速度计算的运动向量 |
| `#posX`、`#posY`、`#posZ` | `#posX` | 被破坏 Lucky 方块的世界坐标 |
| `#pName` | `#pName` | 破坏方块的玩家名称 |
| `#pX`、`#pY`、`#pZ` | `#pX` | 玩家的当前世界坐标 |
| `#pUUID` | `#pUUID` | 玩家的 UUID 字符串 |

未知的 `#token` 模式在输出中保持不变，以兼容附加包特有的模板扩展。

所有模板变量展开完成后，整数或浮点数位置的简单算术表达式（`N + M`、`N - M`）将被求值。
