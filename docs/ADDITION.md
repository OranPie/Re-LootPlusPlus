# Re-LootPlusPlus — Additional Format Specifications

> **Language / 语言:** [English](#english-version) · [中文](#中文版本chinese-version)

**Implementation classes:**

```
- command/exec/CommandChain.java          — top-level `;` split; warns on `&&`/`||`
- config/loader/RecordsLoader.java        — records.txt format
- config/loader/FishingLootLoader.java    — fishing loot file split
- config/loader/FurnaceRecipesLoader.java — furnace recipes format
- config/loader/StackSizeLoader.java      — stack size format
- lucky/drop/LuckyDropParser.java         — Lucky drop key=value attr format
- lucky/attr/LuckyAttrParser.java         — bare @chance normalization + WARN
- legacy/LegacyDropSanitizer.java         — drop string sanitization
- resourcepack/LegacyResourcePackPatcher.java — 1.8 resource format patching
```

**Deferred / partial:**

```
- bows/guns/multitools: registration works; full projectile/ammo behavior not implemented
- buttons/pressure_plates/slabs/stairs/panes/walls/doors/fences: block registration not implemented
- LuckySword/LuckyBow full drop pipeline: partially implemented
```

---

## English Version

### §1 Command Chain Splitting (`CommandChain`)

#### §1.1 Splitting Rule

The `CommandChain` class is responsible for splitting a raw command string into one or more individual commands before any command is dispatched for execution. The splitting algorithm operates as follows:

- The sole splitting delimiter is the semicolon (`;`).
- Only **top-level** semicolons are treated as delimiters. A semicolon is considered top-level if and only if it appears outside of both a NBT compound block (`{...}`) and a selector argument block (`[...]`). Nesting is tracked via a brace/bracket depth counter that is incremented on `{` or `[` and decremented on `}` or `]`.
- If no top-level semicolon is present, the entire raw string is treated as a single command and returned as a one-element list.
- After splitting, each resulting token is trimmed of leading and trailing whitespace only. Internal whitespace within a token is preserved verbatim.

#### §1.2 WARN Requirements

- If the characters `&&` or `||` appear in the command string at top-level (i.e., outside `{...}` and `[...]`), the following warning must be emitted and the characters must be retained literally — they must **not** be interpreted as logical operators:

```
[WARN] [LootPP-Legacy] LegacyCommandChain unsupported separator && or || treated as literal @ <packId>:<innerPath>:<lineNumber>
```

- Rationale: certain 1.8.9 packs include `&&` or `||` as literal text within command strings. Interpreting them as logical operators would alter pack behavior.

#### §1.3 EBNF Grammar

```ebnf
command-chain  ::= command { ";" command }
command        ::= { token | nbt-block | selector-block }
nbt-block      ::= "{" { nbt-content } "}"
selector-block ::= "[" { selector-content } "]"
token          ::= any-char except ";" | "{" | "}" | "[" | "]"
```

---

### §2 Drop Group Weight Semantics (`%%%%%`)

#### §2.1 Definition

A **drop group** is a sequence of one or more drop entries concatenated with `%%%%%` (five percent signs) as the delimiter. This delimiter is distinct from the field separator `_____` (five underscores) used within a single entry.

#### §2.2 Fixed Semantics (Must Not Be Changed)

The following semantics are a 1:1 match of Loot++ 1.8.9 JAR behavior and must be preserved exactly:

1. **Only the first entry's weight** participates in the weighted random selection roll. Weights of subsequent entries within the same group are ignored for the purpose of the roll.
2. **If the group is selected**, every entry in the group is executed, in order of appearance, without exception.
3. A group with a single entry degenerates to standard single-entry behavior.

#### §2.3 Example

```
i-gold_ingot-1-3-5%%%%%e-Zombie-1-2%%%%%c-1-say hello
```

| Field | Value | Note |
|---|---|---|
| Entry 1 | `i-gold_ingot-1-3-5` | Weight = 5; used for the weighted roll |
| Entry 2 | `e-Zombie-1-2` | Executed if group is selected; weight 2 is ignored for roll |
| Entry 3 | `c-1-say hello` | Executed if group is selected |

If this group is selected by the weighted roll: one to three gold ingots are dropped, one Zombie is summoned, and the command `say hello` is executed — all three occur.

---

### §3 `config/records/records.txt`

#### §3.1 Format

```
<record_name> "-" <description>
```

- The field separator is a single hyphen `-`.
- `RecordsLoader` reads each non-blank, non-comment line and splits on the **first** occurrence of `-`.
- Left token: the record name, used as the item identifier.
- Right token: the display description string.
- Lines beginning with `#` or `//` are treated as comments and skipped.
- Blank lines are skipped.

#### §3.2 Registration

Each valid line registers a named record item with the corresponding description text. The item is registered under the `lootplusplus` namespace.

---

### §4 `config/stack_size/stack_sizes.txt`

#### §4.1 Format

```
<item_name> "_____" <stack_size>
```

- The field separator is `_____` (five underscores).
- `StackSizeLoader` processes each non-blank, non-comment line.

#### §4.2 Field Rules

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Item identifier; may be a legacy 1.8 name — emit WARN and remap via `LegacyItemIdFixer` |
| `stack_size` | Integer | Must be a positive integer (> 0); if ≤ 0, skip the line with WARN |

#### §4.3 Application

The overridden maximum stack count is applied at runtime via `ItemStackSizeMixin`, which intercepts `ItemStack.getMaxCount()` and returns the configured value for matching items.

#### §4.4 Example

```
ender_pearl_____64
```

---

### §5 `config/furnace_recipes/`

#### §5.1 `add_smelting_recipes.txt`

Format (field separator `_____`):

```
<input_item_id> "_____" <metadata> "_____" <output_item_id> "_____" <output_meta> "_____" <nbt_tag> "_____" <amount> ["_____" <xp_given>]
```

| Field | Type | Rule |
|---|---|---|
| `input_item_id` | String | Item identifier of the smelting input |
| `metadata` | Integer | Item damage/meta; `-1` = wildcard (any). Emit WARN on wildcard usage |
| `output_item_id` | String | Item identifier of the smelting output |
| `output_meta` | Integer | Output item meta; `-1` = wildcard. Emit WARN |
| `nbt_tag` | SNBT String | Output NBT; `{}` denotes empty/no NBT |
| `amount` | Integer | Output stack size; must be > 0 |
| `xp_given` | Float | Optional; experience awarded per smelt; default `0.0` |

WARN for wildcard metadata:

```
[WARN] [LootPP-Legacy] MetaWildcard metadata=-1 treated as wildcard on smelting recipe @ <packId>:<innerPath>:<lineNumber>
```

#### §5.2 `add_furnace_fuels.txt`

Format (field separator `_____`):

```
<fuel_item_id> "_____" <metadata> "_____" <burn_time>
```

| Field | Type | Rule |
|---|---|---|
| `fuel_item_id` | String | Item identifier of the fuel item |
| `metadata` | Integer | Item damage/meta; `-1` = wildcard. Emit WARN |
| `burn_time` | Integer | Burn duration in ticks; if ≤ 0, skip the line with WARN |

---

### §6 `config/fishing_loot/` — Three-File Split

#### §6.1 File Structure

The fishing loot directory contains exactly three loot category files:

| File | Loot Category |
|---|---|
| `fish_additions.txt` | Fish |
| `junk_additions.txt` | Junk |
| `treasure_additions.txt` | Treasure |

The loot category is determined solely by the file in which a line appears, not from any field within the line.

#### §6.2 Line Format

All three files share an identical line format. The field separator is `-`, and fields are split using `split("-", 7)`, requiring a minimum of 5 segments:

```
<item_name> "-" <stack_size> "-" <enchant_percent> "-" <enchanted> "-" <weight> ["-" <damage>] ["-" <nbt_json>]
```

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Item identifier |
| `stack_size` | Integer | Stack count |
| `enchant_percent` | Float | Probability of enchanting the item (0.0–1.0) |
| `enchanted` | Boolean | Whether the item is pre-enchanted |
| `weight` | Integer | Loot table weight |
| `damage` | Integer | Optional; item damage value |
| `nbt_json` | SNBT String | Optional; additional NBT |

Refer to PARSER.md §6.1 for full field-level validation rules.

---

### §7 Extended `block_additions/` Types

#### §7.1 `blocks.txt` / `generic.txt` — 14 Fields

Format (field separator `_____`):

```
<block_name> "_____" <display_name> "_____" <material> "_____" <falls> "_____" <beacon_base> "_____" <hardness> "_____" <explosion_resistance> "_____" <harvest_tool> "_____" <harvest_level> "_____" <light_emitted> "_____" <slipperiness> "_____" <fire_spread_speed> "_____" <flammability> "_____" <opacity>
```

| Field | Type | Rule |
|---|---|---|
| `block_name` | String | Block identifier; normalize (dots → underscores, lowercase) |
| `display_name` | String | Display name; may contain §-codes |
| `material` | String | 1.8 material name (e.g. `iron`, `rock`, `ground`); WARN + map to 1.18.2 equivalent |
| `falls` | Boolean | `true` → block falls like sand/gravel |
| `beacon_base` | Boolean | `true` → block is valid beacon base |
| `hardness` | Float | Block hardness; negative → indestructible |
| `explosion_resistance` | Float | Explosion resistance value |
| `harvest_tool` | String | Required harvest tool type (e.g. `pickaxe`, `axe`, `shovel`) |
| `harvest_level` | Integer | Required harvest level; `-1` = any |
| `light_emitted` | Integer | Light level emitted (0–15) |
| `slipperiness` | Float | Friction coefficient (vanilla ice = 0.98) |
| `fire_spread_speed` | Integer | Speed at which fire spreads from this block |
| `flammability` | Integer | Likelihood of catching fire |
| `opacity` | Integer | Light opacity; `-1` = use default opaque |

WARN for 1.8 material name mapping:

```
[WARN] [LootPP-Legacy] BlockId material 'rock' mapped to 'stone' @ <packId>:<innerPath>:<lineNumber>
```

Example line:

```
ender.ender_crystal_block_____§2§lEnder Crystal Block_____iron_____false_____false_____5.0_____5.0_____pickaxe_____2_____0_____0.6_____0_____0_____-1
```

#### §7.2 `plants.txt` — 10 Fields

Format (field separator `_____`):

```
<block_name> "_____" <display_name> "_____" <material> "_____" <hardness> "_____" <explosion_resistance> "_____" <harvest_tool> "_____" <harvest_level> "_____" <light_emitted> "_____" <fire_spread_speed> "_____" <flammability>
```

Plant blocks are rendered with a crossed-planes texture (flower/crop style). All material remapping rules from §7.1 apply.

Example line:

```
water.coral_plant_____Coral Plant_____gourd_____0.0_____0.0_____none_____-1_____0.7_____0_____0
```

#### §7.3 `crops.txt` — 10 Fields

Format (field separator `_____`):

```
<block_name> "_____" <display_name> "_____" <seed_item_name> "_____" <seed_item_meta> "_____" <light_emitted> "_____" <fire_spread_speed> "_____" <flammability> "_____" <can_bonemeal> "_____" <nether_plant> "_____" <right_click_harvest>
```

| Field | Type | Rule |
|---|---|---|
| `block_name` | String | Block identifier |
| `display_name` | String | Display name |
| `seed_item_name` | String | Item identifier of the seed that plants this crop |
| `seed_item_meta` | Integer | Seed item meta; `-1` = any |
| `light_emitted` | Integer | Light level (0–15) |
| `fire_spread_speed` | Integer | Fire spread speed |
| `flammability` | Integer | Flammability |
| `can_bonemeal` | Boolean | Whether bonemeal can accelerate growth |
| `nether_plant` | Boolean | Whether this crop grows in the Nether |
| `right_click_harvest` | Boolean | Whether right-click harvests without breaking |

#### §7.4 `cakes.txt` — 13 Fields + Optional Potion Effects

Format (field separator `_____`):

```
<block_name> "_____" <display_name> "_____" <hardness> "_____" <explosion_resistance> "_____" <light_emitted> "_____" <slipperiness> "_____" <fire_spread_speed> "_____" <flammability> "_____" <num_bites> "_____" <hunger_restored> "_____" <saturation_restored> "_____" <always_edible> "_____" <potion_effects>
```

| Field | Type | Rule |
|---|---|---|
| `block_name` | String | Block identifier |
| `display_name` | String | Display name |
| `hardness` | Float | Block hardness |
| `explosion_resistance` | Float | Explosion resistance |
| `light_emitted` | Integer | Light level (0–15) |
| `slipperiness` | Float | Friction coefficient |
| `fire_spread_speed` | Integer | Fire spread speed |
| `flammability` | Integer | Flammability |
| `num_bites` | Integer | Number of bites before the block is consumed |
| `hunger_restored` | Integer | Hunger points restored per bite |
| `saturation_restored` | Float | Saturation restored per bite |
| `always_edible` | Boolean | Whether the cake can be eaten when full |
| `potion_effects` | String | Optional; see potion effect spec below |

**Potion effect spec** (field `potion_effects`):

Each potion effect is specified as five sub-fields separated by `-`:

```
<effect_id> "-" <duration_ticks> "-" <amplifier> "-" <probability> "-" <particle_type>
```

| Sub-field | Type | Rule |
|---|---|---|
| `effect_id` | String | Potion effect identifier |
| `duration_ticks` | Integer | Duration in game ticks |
| `amplifier` | Integer | Effect level (0 = level I) |
| `probability` | Float | Probability of applying effect (0.0–1.0) |
| `particle_type` | Enum | `none` \| `faded` \| `normal` |

If a potion spec string contains fewer than 5 sub-fields, that spec is silently skipped. Multiple potion specs may be present.

#### §7.5 Deferred Block Types

The following block addition types are recognized by the parser but block registration is **not** currently implemented. Lines are parsed and a WARN is emitted; no block is registered:

`buttons`, `pressure_plates`, `doors`, `slabs`, `stairs`, `panes`, `walls`, `fences`, `fence_gates`, `trapdoors`, `colored_blocks`, `glowing_blocks`, `ores`, `bonemeal_flowers`, `flowers`, `blocks_with_states`

```
[WARN] [LootPP-Legacy] BlockId type 'slabs' not implemented, skipping @ <packId>:<innerPath>:<lineNumber>
```

---

### §8 Extended `item_additions/` Types

#### §8.1 Global Rules

The following rules apply to all item addition types:

| Rule | Detail |
|---|---|
| Namespace default | Item names without a namespace prefix are registered as `lootplusplus:<name>` |
| Name normalization | Dots and uppercase letters in names are normalized: dots → underscores, all characters → lowercase |
| Boolean parsing | Only the string `"true"` (case-insensitive) is `true`; `"1"` and other strings are `false` — standard `Boolean.parseBoolean` semantics |
| Probability clamping | Probability values exceeding `1.0` are **not** clamped; a value > 1.0 means the effect always triggers |
| NBT invisible characters | Invisible characters (soft hyphen U+00AD, zero-width space U+200B, etc.) must be stripped from NBT strings before SNBT parsing; emit WARN if any were stripped |

WARN for invisible character stripping:

```
[WARN] [LootPP-Legacy] LegacyNBT stripped invisible character U+00AD from NBT string @ <packId>:<innerPath>:<lineNumber>
```

#### §8.2 `generic_items.txt` — 2–3 Fields

Format (field separator `_____`):

```
<item_name> "_____" <display_name> ["_____" <shiny>]
```

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Item identifier |
| `display_name` | String | Display name |
| `shiny` | Boolean | Optional; default `false`; if `true`, the item renders with an enchantment glint |

#### §8.3 `materials.txt` — Tool/Armor Material Definition

Format (field separator `_____`):

```
<material_item_id> "_____" <material_meta> "_____" <harvest_level> "_____" <base_durability> "_____" <efficiency> "_____" <damage> "_____" <enchantability> "_____" <armour_durability_factor> "_____" <armour_protection_list>
```

| Field | Type | Rule |
|---|---|---|
| `material_item_id` | String | Item used as the repair material |
| `material_meta` | Integer | Item meta; `-1` = any. Emit WARN |
| `harvest_level` | Integer | Tool harvest level |
| `base_durability` | Integer | Base durability of tools using this material |
| `efficiency` | Float | Mining speed multiplier |
| `damage` | Float | Attack damage bonus |
| `enchantability` | Integer | Enchantability value |
| `armour_durability_factor` | Integer | Armor durability multiplier |
| `armour_protection_list` | String | `<helmet>-<chest>-<legs>-<boots>` — four integers separated by `-` |

#### §8.4 `swords.txt` — 4–5 Fields

Format (field separator `_____`):

```
<item_name> "_____" <display_name> "_____" <material_item_id> "_____" <damage> ["_____" <material_meta>]
```

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Sword item identifier |
| `display_name` | String | Display name |
| `material_item_id` | String | Material item identifier (references `materials.txt`) |
| `damage` | Float | Base attack damage bonus |
| `material_meta` | Integer | Optional; material item meta; default `-1` |

#### §8.5 `pickaxes.txt` / `axes.txt` / `shovels.txt` / `hoes.txt` — 3–4 Fields

Format (field separator `_____`):

```
<item_name> "_____" <display_name> "_____" <material_item_id> ["_____" <material_meta>]
```

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Tool item identifier |
| `display_name` | String | Display name |
| `material_item_id` | String | Material item identifier |
| `material_meta` | Integer | Optional; material item meta; default `-1` |

#### §8.6 `helmets.txt` / `chestplates.txt` / `leggings.txt` / `boots.txt` — 4–5 Fields

Format (field separator `_____`):

```
<item_name> "_____" <display_name> "_____" <material_item_id> "_____" <armour_texture_base> ["_____" <material_meta>]
```

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Armor item identifier |
| `display_name` | String | Display name |
| `material_item_id` | String | Material item identifier |
| `armour_texture_base` | String | Base filename for the armor layer texture; consumed by `ArmorFeatureRendererMixin` |
| `material_meta` | Integer | Optional; material item meta; default `-1` |

#### §8.7 `foods.txt` — 8 Base Fields + Optional Potion Specs

Format (field separator `_____`):

```
<item_name> "_____" <display_name> "_____" <shiny> "_____" <food_restored> "_____" <saturation> "_____" <wolves_eat> "_____" <always_edible> "_____" <time_to_eat> ["_____" <potion_spec> ...]
```

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Food item identifier |
| `display_name` | String | Display name |
| `shiny` | Boolean | Enchantment glint |
| `food_restored` | Integer | Hunger points restored |
| `saturation` | Float | Saturation modifier |
| `wolves_eat` | Boolean | Whether wolves can eat this food |
| `always_edible` | Boolean | Whether the food can be eaten when full |
| `time_to_eat` | Integer | Use duration in ticks |
| `potion_spec` | String | Zero or more; each spec has 5 sub-fields: `<effect_id>-<duration_ticks>-<amplifier>-<probability>-<particle_type>` (see §7.4 for sub-field rules) |

#### §8.8 `thrown.txt` — 8 Base Fields + Optional Drop Entries

Format (field separator `_____`):

```
<item_name> "_____" <display_name> "_____" <shiny> "_____" <damage> "_____" <velocity> "_____" <gravity> "_____" <inaccuracy> "_____" <drop_chance> ["_____" <drop_entry> ...]
```

| Field | Type | Rule |
|---|---|---|
| `item_name` | String | Thrown item identifier |
| `display_name` | String | Display name |
| `shiny` | Boolean | Enchantment glint |
| `damage` | Float | Impact damage dealt to entities |
| `velocity` | Float | Initial projectile velocity |
| `gravity` | Float | Gravity factor applied per tick |
| `inaccuracy` | Float | Random spread applied to trajectory |
| `drop_chance` | Float | Probability that drop entries execute on impact |
| `drop_entry` | String | Zero or more drop entries; drop group format (`%%%%%` separator) as defined in §2 and PARSER.md §1 |

The item registers a `LootThrownItem` which spawns a `LootThrownItemEntity` on right-click. On impact, the registered drop groups are evaluated and executed.

#### §8.9 `bows.txt` / `guns.txt` / `multitools.txt` — Deferred

Item registration is performed (the item object is created and registered in the Fabric registry). Full projectile mechanics and ammo behavior are **not** currently implemented.

---

### §9 Extended `item_effects/` Trigger Types

#### §9.1 `hitting_entity_to_yourself` Trigger

| Property | Value |
|---|---|
| Config files | `hitting_entity_to_yourself.txt`, `command_hitting_entity_to_yourself.txt` |
| Effect target | The attacker themselves (the entity holding the item), **not** the entity being attacked |
| Line format | Identical to other item-effect formats (PARSER.md §4.1 / §4.2) |

This trigger fires when a player attacks any entity while holding a matching item, and applies the configured effect or command to the attacker.

#### §9.2 `digging_block` Triggers

Three config files govern block-breaking triggers:

| File | Type | Fires when |
|---|---|---|
| `digging_block.txt` | Item-based | Player breaks any block while holding the matching item |
| `command_digging_block.txt` | Item-based, command | Player breaks any block while holding the matching item |
| `digging_block_block.txt` | Block-based | Player breaks the specific matching block (any held item) |

**Block-based effect format** (`digging_block_block.txt`), field separator `_____`:

```
<block_name> "_____" <block_meta> "_____" <effect_id> "_____" <duration> "_____" <amplifier> "_____" <probability> "_____" <particle_type>
```

| Field | Type | Rule |
|---|---|---|
| `block_name` | String | Block identifier to match |
| `block_meta` | Integer | Block metadata; `-1` = any |
| `effect_id` | String | Potion effect identifier |
| `duration` | Integer | Duration in ticks |
| `amplifier` | Integer | Effect amplifier |
| `probability` | Float | Trigger probability |
| `particle_type` | Enum | `none` \| `faded` \| `normal` |

**Block-based command format** (`command_digging_block.txt` when block-keyed), field separator `_____`:

```
<block_name> "_____" <block_meta> "_____" <command_probability> "_____" <command>
```

---

### §10 Lucky Block Compatibility Additions

#### §10.1 Bare `@chance` Attribute Normalization

Some 1.8.9 Lucky Block addon packs emit a bare `@chance` attribute token without an assignment value:

```
ID=lootplusplus:xxx@chance@luck=1
```

The modern Lucky Block Fabric parser treats `@` as a key-value dictionary delimiter and expects `key=value` pairs. A bare key without `=` causes a parse error that aborts processing of the affected drop entry.

**Compatibility rule** (implemented in `LuckyAttrParser`):

- A bare `@chance` token (i.e., `chance` present as a key with no `=` and no value) is normalized to `chance=1`.
- A WARN must be emitted:

```
[WARN] [LootPP-Legacy] LuckyAttrBareChance bare @chance treated as chance=1 @ <packId>:<innerPath>:<lineNumber>
```

- All other bare attribute keys are **not** force-fixed; they retain their original (potentially error-causing) behavior.

#### §10.2 Legacy Entity ID Normalization

1.8.9 addons reference entity types using pre-1.13 unqualified names (e.g. `EntityHorse`, `PigZombie`, `Item`). These names fail `Identifier` validation in 1.18.2 and must be remapped.

The following mapping table is implemented in `LegacyEntityIdFixer`:

| Legacy Name | 1.18.2 Name | Notes |
|---|---|---|
| `PigZombie` | `minecraft:zombified_piglin` | Direct rename |
| `EntityHorse` (NBT `Type=0`) | `minecraft:horse` | NBT `Type` field selects subtype |
| `EntityHorse` (NBT `Type=1`) | `minecraft:donkey` | |
| `EntityHorse` (NBT `Type=2`) | `minecraft:mule` | |
| `EntityHorse` (NBT `Type=3`) | `minecraft:zombie_horse` | |
| `EntityHorse` (NBT `Type=4`) | `minecraft:skeleton_horse` | |
| `Item` | `minecraft:item` | Also fix missing namespace on NBT sub-field `Item.id` |
| `Cow`, `Pig`, etc. | `minecraft:<lowercase>` | Simple lowercase + namespace prefix |

WARN format for entity remapping:

```
[WARN] [LootPP-Legacy] LegacyEntityId mapped 'EntityHorse' type=1 -> 'minecraft:donkey' @ <packId>:<innerPath>:<lineNumber>
[WARN] [LootPP-Legacy] LegacyItemId assumed namespace for 'iron_boots' -> 'minecraft:iron_boots' @ <packId>:<innerPath>:<lineNumber>
```

#### §10.3 Legacy Resource Pack Patching

`LegacyResourcePackPatcher` wraps each addon resource pack and applies the following format fixes transparently. Every fix emits a WARN.

| Fix Applied | WARN Type |
|---|---|
| Blockstate `variants` key `"normal"` → `""` (empty string) | `LegacyBlockstate` |
| Model path missing `block/` or `item/` prefix → auto-prepend the appropriate prefix | `LegacyTexture` |
| Texture path `blocks/` → `block/`; `items/` → `item/` | `LegacyTexture` |
| Old 1.8 texture names remapped to 1.18.2 equivalents (wool, terracotta, log names, etc.) | `LegacyTexture` |
| `.lang` file → `.json` translation file (re-encode; strip BOM; UTF-8 strict with Latin-1 fallback) | `LegacyTexture` |

WARN examples:

```
[WARN] [LootPP-Legacy] LegacyBlockstate converted variant 'normal' to '' in lucky/xxx_resources @ <packId>:<innerPath>:<lineNumber>
[WARN] [LootPP-Legacy] LegacyTexture mapped texture blocks/hardened_clay_stained_blue -> block/blue_terracotta @ <packId>:<innerPath>:<lineNumber>
```

---

### §11 File Encoding Rules

All config text files (`.txt`) are decoded using the following procedure, applied in order:

1. Attempt strict UTF-8 decoding.
2. On decoding failure, fall back to ISO-8859-1 / CP1252.
3. Strip a leading BOM (`\uFEFF`, U+FEFF) from the first line of the file before any further processing.
4. Within NBT strings, strip invisible characters (soft hyphen U+00AD, zero-width non-breaking space U+FEFF in non-BOM position, zero-width space U+200B, and similar Unicode formatting characters) before SNBT parsing; emit WARN if any characters were stripped (see §8.1).

---

### §12 Recipe Additional Notes

#### §12.1 OreDict Token Handling in `add_shaped.txt` / `add_shapeless.txt`

In shaped and shapeless recipe files, ingredient tokens that are enclosed in double quotes (`"..."`) are treated as OreDict keys rather than direct item identifiers:

- The surrounding double-quote characters are stripped from the token.
- The resulting string is used as an OreDict lookup key.
- WARN required:

```
[WARN] [LootPP-Legacy] LegacyOreDict key used @ <packId>:<innerPath>:<lineNumber>
```

- If the OreDict key has no matching items in the 1.18.2 item registry, a second WARN is emitted and that ingredient slot is skipped:

```
[WARN] [LootPP-Legacy] LegacyOreDict key 'ingotGold' has no matching items in registry, ingredient skipped @ <packId>:<innerPath>:<lineNumber>
```

---

## 中文版本（Chinese Version）

### §1 命令链拆分（`CommandChain`）

#### §1.1 拆分规则

`CommandChain` 类负责在执行分发之前，将原始命令字符串拆分为一条或多条独立命令。拆分算法如下：

- 唯一的拆分分隔符为分号（`;`）。
- 仅**顶层**分号被视为分隔符。所谓顶层，是指该分号出现在 NBT 复合块（`{...}`）和选择器参数块（`[...]`）之外。通过深度计数器追踪嵌套层级：遇到 `{` 或 `[` 时计数加一，遇到 `}` 或 `]` 时计数减一。
- 若原始字符串中不存在顶层分号，则将整个字符串视为单条命令，以单元素列表返回。
- 拆分后，对每个结果标记仅去除首尾空白字符；标记内部的空白字符原样保留。

#### §1.2 警告要求

- 若字符串中在顶层位置（即 `{...}` 和 `[...]` 之外）出现 `&&` 或 `||`，必须输出以下警告，且这些字符须原样保留——**不得**将其解释为逻辑运算符：

```
[WARN] [LootPP-Legacy] LegacyCommandChain unsupported separator && or || treated as literal @ <packId>:<innerPath>:<lineNumber>
```

- 原因说明：部分 1.8.9 附加包的命令字符串中包含字面量 `&&` 或 `||`，将其解释为逻辑运算符会改变附加包的预期行为。

#### §1.3 EBNF 文法

```ebnf
command-chain  ::= command { ";" command }
command        ::= { token | nbt-block | selector-block }
nbt-block      ::= "{" { nbt-content } "}"
selector-block ::= "[" { selector-content } "]"
token          ::= any-char except ";" | "{" | "}" | "[" | "]"
```

---

### §2 掉落组权重语义（`%%%%%`）

#### §2.1 定义

**掉落组**是以 `%%%%%`（五个百分号）为分隔符拼接的一条或多条掉落条目序列。该分隔符与单条条目内部字段所用的 `_____`（五个下划线）分隔符在语义上相互独立。

#### §2.2 固定语义（禁止更改）

以下语义与 Loot++ 1.8.9 JAR 行为完全一致，必须严格保留：

1. 在加权随机抽取时，**仅使用第一条条目的权重**。同一掉落组中后续条目的权重对本次抽取无效。
2. **若该掉落组被选中**，则组内所有条目按出现顺序依次执行，无一例外。
3. 仅包含单条条目的掉落组退化为标准单条目行为。

#### §2.3 示例

```
i-gold_ingot-1-3-5%%%%%e-Zombie-1-2%%%%%c-1-say hello
```

| 字段 | 值 | 说明 |
|---|---|---|
| 条目 1 | `i-gold_ingot-1-3-5` | 权重 = 5；用于加权抽取 |
| 条目 2 | `e-Zombie-1-2` | 组被选中时执行；权重 2 对抽取无效 |
| 条目 3 | `c-1-say hello` | 组被选中时执行 |

若该掉落组被加权抽取选中，则：掉落若干金锭、召唤一只僵尸、执行命令 `say hello`——三者均会发生。

---

### §3 `config/records/records.txt`

#### §3.1 格式规范

```
<record_name> "-" <description>
```

- 字段分隔符为单个连字符 `-`。
- `RecordsLoader` 读取每一行非空非注释行，并在**第一个** `-` 处进行拆分。
- 左侧标记：唱片名称，用作物品标识符。
- 右侧标记：显示描述字符串。
- 以 `#` 或 `//` 开头的行视为注释，跳过处理。
- 空白行跳过处理。

#### §3.2 注册行为

每行有效数据均在 `lootplusplus` 命名空间下注册一个带有对应描述文本的唱片物品。

---

### §4 `config/stack_size/stack_sizes.txt`

#### §4.1 格式规范

```
<item_name> "_____" <stack_size>
```

- 字段分隔符为 `_____`（五个下划线）。
- `StackSizeLoader` 处理每一行非空非注释行。

#### §4.2 字段规则

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 物品标识符；可能为 1.8 遗留名称——需输出警告并通过 `LegacyItemIdFixer` 进行重映射 |
| `stack_size` | 整数 | 必须为正整数（> 0）；若 ≤ 0，则跳过该行并输出警告 |

#### §4.3 应用方式

覆盖后的最大堆叠数量在运行时通过 `ItemStackSizeMixin` 应用：该混入（Mixin）拦截 `ItemStack.getMaxCount()` 方法，对匹配物品返回配置的数值。

#### §4.4 示例

```
ender_pearl_____64
```

---

### §5 `config/furnace_recipes/`

#### §5.1 `add_smelting_recipes.txt`

格式（字段分隔符 `_____`）：

```
<input_item_id> "_____" <metadata> "_____" <output_item_id> "_____" <output_meta> "_____" <nbt_tag> "_____" <amount> ["_____" <xp_given>]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `input_item_id` | 字符串 | 冶炼输入物品的标识符 |
| `metadata` | 整数 | 物品附加值/元数据；`-1` = 通配符（任意值）。通配符时输出警告 |
| `output_item_id` | 字符串 | 冶炼输出物品的标识符 |
| `output_meta` | 整数 | 输出物品元数据；`-1` = 通配符。输出警告 |
| `nbt_tag` | SNBT 字符串 | 输出物品 NBT；`{}` 表示无 NBT |
| `amount` | 整数 | 输出堆叠数量；必须 > 0 |
| `xp_given` | 浮点数 | 可选；每次冶炼获得的经验值；默认 `0.0` |

通配符元数据警告：

```
[WARN] [LootPP-Legacy] MetaWildcard metadata=-1 treated as wildcard on smelting recipe @ <packId>:<innerPath>:<lineNumber>
```

#### §5.2 `add_furnace_fuels.txt`

格式（字段分隔符 `_____`）：

```
<fuel_item_id> "_____" <metadata> "_____" <burn_time>
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `fuel_item_id` | 字符串 | 燃料物品的标识符 |
| `metadata` | 整数 | 物品元数据；`-1` = 通配符。输出警告 |
| `burn_time` | 整数 | 燃烧时长（游戏刻）；若 ≤ 0，跳过该行并输出警告 |

---

### §6 `config/fishing_loot/` — 三文件拆分

#### §6.1 文件结构

钓鱼战利品目录包含三个独立的战利品类别文件：

| 文件名 | 战利品类别 |
|---|---|
| `fish_additions.txt` | 鱼类 |
| `junk_additions.txt` | 垃圾 |
| `treasure_additions.txt` | 宝藏 |

战利品类别完全由行所在的文件决定，与行内字段内容无关。

#### §6.2 行格式

三个文件共享相同的行格式。字段分隔符为 `-`，使用 `split("-", 7)` 进行拆分，要求至少包含 5 个字段段：

```
<item_name> "-" <stack_size> "-" <enchant_percent> "-" <enchanted> "-" <weight> ["-" <damage>] ["-" <nbt_json>]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 物品标识符 |
| `stack_size` | 整数 | 堆叠数量 |
| `enchant_percent` | 浮点数 | 附魔概率（0.0–1.0） |
| `enchanted` | 布尔值 | 物品是否预先附魔 |
| `weight` | 整数 | 战利品表权重 |
| `damage` | 整数 | 可选；物品耐久损耗值 |
| `nbt_json` | SNBT 字符串 | 可选；附加 NBT 数据 |

完整的字段级校验规则参见 PARSER.md §6.1。

---

### §7 扩展 `block_additions/` 类型

#### §7.1 `blocks.txt` / `generic.txt` — 14 字段

格式（字段分隔符 `_____`）：

```
<block_name> "_____" <display_name> "_____" <material> "_____" <falls> "_____" <beacon_base> "_____" <hardness> "_____" <explosion_resistance> "_____" <harvest_tool> "_____" <harvest_level> "_____" <light_emitted> "_____" <slipperiness> "_____" <fire_spread_speed> "_____" <flammability> "_____" <opacity>
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `block_name` | 字符串 | 方块标识符；规范化（点号 → 下划线，转小写） |
| `display_name` | 字符串 | 显示名称；可包含 §-格式代码 |
| `material` | 字符串 | 1.8 材质名称（如 `iron`、`rock`、`ground`）；输出警告并映射至 1.18.2 等效值 |
| `falls` | 布尔值 | `true` → 方块像沙子/沙砾一样受重力下落 |
| `beacon_base` | 布尔值 | `true` → 方块可作为信标基座 |
| `hardness` | 浮点数 | 方块硬度；负值 → 不可破坏 |
| `explosion_resistance` | 浮点数 | 爆炸抗性值 |
| `harvest_tool` | 字符串 | 所需收获工具类型（如 `pickaxe`、`axe`、`shovel`） |
| `harvest_level` | 整数 | 所需收获等级；`-1` = 任意工具等级 |
| `light_emitted` | 整数 | 发光等级（0–15） |
| `slipperiness` | 浮点数 | 摩擦系数（原版冰块 = 0.98） |
| `fire_spread_speed` | 整数 | 火焰从该方块蔓延的速度 |
| `flammability` | 整数 | 可燃性 |
| `opacity` | 整数 | 光线不透明度；`-1` = 使用默认不透明值 |

1.8 材质名称映射警告：

```
[WARN] [LootPP-Legacy] BlockId material 'rock' mapped to 'stone' @ <packId>:<innerPath>:<lineNumber>
```

示例行：

```
ender.ender_crystal_block_____§2§lEnder Crystal Block_____iron_____false_____false_____5.0_____5.0_____pickaxe_____2_____0_____0.6_____0_____0_____-1
```

#### §7.2 `plants.txt` — 10 字段

格式（字段分隔符 `_____`）：

```
<block_name> "_____" <display_name> "_____" <material> "_____" <hardness> "_____" <explosion_resistance> "_____" <harvest_tool> "_____" <harvest_level> "_____" <light_emitted> "_____" <fire_spread_speed> "_____" <flammability>
```

植物方块以交叉面纹理（花朵/作物风格）渲染。§7.1 中的所有材质重映射规则同样适用。

示例行：

```
water.coral_plant_____Coral Plant_____gourd_____0.0_____0.0_____none_____-1_____0.7_____0_____0
```

#### §7.3 `crops.txt` — 10 字段

格式（字段分隔符 `_____`）：

```
<block_name> "_____" <display_name> "_____" <seed_item_name> "_____" <seed_item_meta> "_____" <light_emitted> "_____" <fire_spread_speed> "_____" <flammability> "_____" <can_bonemeal> "_____" <nether_plant> "_____" <right_click_harvest>
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `block_name` | 字符串 | 方块标识符 |
| `display_name` | 字符串 | 显示名称 |
| `seed_item_name` | 字符串 | 种植该作物所用种子的物品标识符 |
| `seed_item_meta` | 整数 | 种子物品元数据；`-1` = 任意 |
| `light_emitted` | 整数 | 发光等级（0–15） |
| `fire_spread_speed` | 整数 | 火焰蔓延速度 |
| `flammability` | 整数 | 可燃性 |
| `can_bonemeal` | 布尔值 | 是否可用骨粉加速生长 |
| `nether_plant` | 布尔值 | 该作物是否在下界生长 |
| `right_click_harvest` | 布尔值 | 是否可在不破坏方块的情况下右键收获 |

#### §7.4 `cakes.txt` — 13 字段 + 可选药水效果

格式（字段分隔符 `_____`）：

```
<block_name> "_____" <display_name> "_____" <hardness> "_____" <explosion_resistance> "_____" <light_emitted> "_____" <slipperiness> "_____" <fire_spread_speed> "_____" <flammability> "_____" <num_bites> "_____" <hunger_restored> "_____" <saturation_restored> "_____" <always_edible> "_____" <potion_effects>
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `block_name` | 字符串 | 方块标识符 |
| `display_name` | 字符串 | 显示名称 |
| `hardness` | 浮点数 | 方块硬度 |
| `explosion_resistance` | 浮点数 | 爆炸抗性 |
| `light_emitted` | 整数 | 发光等级（0–15） |
| `slipperiness` | 浮点数 | 摩擦系数 |
| `fire_spread_speed` | 整数 | 火焰蔓延速度 |
| `flammability` | 整数 | 可燃性 |
| `num_bites` | 整数 | 方块被消耗前的咬取次数 |
| `hunger_restored` | 整数 | 每次咬取恢复的饥饿值 |
| `saturation_restored` | 浮点数 | 每次咬取恢复的饱和度 |
| `always_edible` | 布尔值 | 饱腹时是否仍可食用 |
| `potion_effects` | 字符串 | 可选；参见下方药水效果规范 |

**药水效果规范**（字段 `potion_effects`）：

每条药水效果由五个以 `-` 分隔的子字段组成：

```
<effect_id> "-" <duration_ticks> "-" <amplifier> "-" <probability> "-" <particle_type>
```

| 子字段 | 类型 | 规则 |
|---|---|---|
| `effect_id` | 字符串 | 药水效果标识符 |
| `duration_ticks` | 整数 | 持续时间（游戏刻） |
| `amplifier` | 整数 | 效果等级（0 = I 级） |
| `probability` | 浮点数 | 效果触发概率（0.0–1.0） |
| `particle_type` | 枚举 | `none` \| `faded` \| `normal` |

若某条药水效果规范字符串的子字段少于 5 个，则该规范被静默跳过。可存在多条药水效果规范。

#### §7.5 延迟实现的方块类型

以下方块添加类型可被解析器识别，但当前**尚未实现**方块注册。对应行会被解析，并输出警告；不会注册任何方块：

`buttons`、`pressure_plates`、`doors`、`slabs`、`stairs`、`panes`、`walls`、`fences`、`fence_gates`、`trapdoors`、`colored_blocks`、`glowing_blocks`、`ores`、`bonemeal_flowers`、`flowers`、`blocks_with_states`

```
[WARN] [LootPP-Legacy] BlockId type 'slabs' not implemented, skipping @ <packId>:<innerPath>:<lineNumber>
```

---

### §8 扩展 `item_additions/` 类型

#### §8.1 全局规则

以下规则适用于所有物品添加类型：

| 规则 | 详细说明 |
|---|---|
| 命名空间默认值 | 不含命名空间前缀的物品名称注册为 `lootplusplus:<name>` |
| 名称规范化 | 名称中的点号和大写字母须规范化：点号 → 下划线，所有字符 → 小写 |
| 布尔值解析 | 仅字符串 `"true"`（大小写不敏感）为 `true`；`"1"` 及其他字符串为 `false`——遵循标准 `Boolean.parseBoolean` 语义 |
| 概率值截断 | 超过 `1.0` 的概率值**不进行截断**；大于 1.0 的值表示效果必定触发 |
| NBT 不可见字符 | 在 SNBT 解析之前，必须从 NBT 字符串中去除不可见字符（软连字符 U+00AD、零宽空格 U+200B 等）；若有字符被去除，则输出警告 |

不可见字符去除警告：

```
[WARN] [LootPP-Legacy] LegacyNBT stripped invisible character U+00AD from NBT string @ <packId>:<innerPath>:<lineNumber>
```

#### §8.2 `generic_items.txt` — 2–3 字段

格式（字段分隔符 `_____`）：

```
<item_name> "_____" <display_name> ["_____" <shiny>]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 物品标识符 |
| `display_name` | 字符串 | 显示名称 |
| `shiny` | 布尔值 | 可选；默认 `false`；若为 `true`，物品渲染附魔光效 |

#### §8.3 `materials.txt` — 工具/盔甲材质定义

格式（字段分隔符 `_____`）：

```
<material_item_id> "_____" <material_meta> "_____" <harvest_level> "_____" <base_durability> "_____" <efficiency> "_____" <damage> "_____" <enchantability> "_____" <armour_durability_factor> "_____" <armour_protection_list>
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `material_item_id` | 字符串 | 作为修复材料的物品标识符 |
| `material_meta` | 整数 | 物品元数据；`-1` = 任意。输出警告 |
| `harvest_level` | 整数 | 工具收获等级 |
| `base_durability` | 整数 | 使用此材质的工具的基础耐久度 |
| `efficiency` | 浮点数 | 挖掘速度倍率 |
| `damage` | 浮点数 | 攻击伤害加成 |
| `enchantability` | 整数 | 附魔能力值 |
| `armour_durability_factor` | 整数 | 盔甲耐久度倍数 |
| `armour_protection_list` | 字符串 | `<helmet>-<chest>-<legs>-<boots>` — 四个以 `-` 分隔的整数 |

#### §8.4 `swords.txt` — 4–5 字段

格式（字段分隔符 `_____`）：

```
<item_name> "_____" <display_name> "_____" <material_item_id> "_____" <damage> ["_____" <material_meta>]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 剑的物品标识符 |
| `display_name` | 字符串 | 显示名称 |
| `material_item_id` | 字符串 | 材质物品标识符（引用 `materials.txt`） |
| `damage` | 浮点数 | 基础攻击伤害加成 |
| `material_meta` | 整数 | 可选；材质物品元数据；默认 `-1` |

#### §8.5 `pickaxes.txt` / `axes.txt` / `shovels.txt` / `hoes.txt` — 3–4 字段

格式（字段分隔符 `_____`）：

```
<item_name> "_____" <display_name> "_____" <material_item_id> ["_____" <material_meta>]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 工具物品标识符 |
| `display_name` | 字符串 | 显示名称 |
| `material_item_id` | 字符串 | 材质物品标识符 |
| `material_meta` | 整数 | 可选；材质物品元数据；默认 `-1` |

#### §8.6 `helmets.txt` / `chestplates.txt` / `leggings.txt` / `boots.txt` — 4–5 字段

格式（字段分隔符 `_____`）：

```
<item_name> "_____" <display_name> "_____" <material_item_id> "_____" <armour_texture_base> ["_____" <material_meta>]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 盔甲物品标识符 |
| `display_name` | 字符串 | 显示名称 |
| `material_item_id` | 字符串 | 材质物品标识符 |
| `armour_texture_base` | 字符串 | 盔甲层纹理的基础文件名；由 `ArmorFeatureRendererMixin` 使用 |
| `material_meta` | 整数 | 可选；材质物品元数据；默认 `-1` |

#### §8.7 `foods.txt` — 8 个基础字段 + 可选药水效果规范

格式（字段分隔符 `_____`）：

```
<item_name> "_____" <display_name> "_____" <shiny> "_____" <food_restored> "_____" <saturation> "_____" <wolves_eat> "_____" <always_edible> "_____" <time_to_eat> ["_____" <potion_spec> ...]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 食物物品标识符 |
| `display_name` | 字符串 | 显示名称 |
| `shiny` | 布尔值 | 附魔光效 |
| `food_restored` | 整数 | 恢复的饥饿值 |
| `saturation` | 浮点数 | 饱和度修正值 |
| `wolves_eat` | 布尔值 | 狼是否可以食用 |
| `always_edible` | 布尔值 | 饱腹时是否可食用 |
| `time_to_eat` | 整数 | 使用时长（游戏刻） |
| `potion_spec` | 字符串 | 零个或多个；每条规范包含 5 个子字段：`<effect_id>-<duration_ticks>-<amplifier>-<probability>-<particle_type>`（子字段规则参见 §7.4） |

#### §8.8 `thrown.txt` — 8 个基础字段 + 可选掉落条目

格式（字段分隔符 `_____`）：

```
<item_name> "_____" <display_name> "_____" <shiny> "_____" <damage> "_____" <velocity> "_____" <gravity> "_____" <inaccuracy> "_____" <drop_chance> ["_____" <drop_entry> ...]
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `item_name` | 字符串 | 投掷物品标识符 |
| `display_name` | 字符串 | 显示名称 |
| `shiny` | 布尔值 | 附魔光效 |
| `damage` | 浮点数 | 命中实体时造成的伤害 |
| `velocity` | 浮点数 | 初始投射速度 |
| `gravity` | 浮点数 | 每游戏刻施加的重力系数 |
| `inaccuracy` | 浮点数 | 轨迹的随机扩散量 |
| `drop_chance` | 浮点数 | 命中时掉落条目执行的概率 |
| `drop_entry` | 字符串 | 零个或多个掉落条目；采用掉落组格式（`%%%%%` 分隔符），定义见 §2 及 PARSER.md §1 |

该物品注册为 `LootThrownItem`，右键时生成 `LootThrownItemEntity`。命中时，已注册的掉落组被评估并执行。

#### §8.9 `bows.txt` / `guns.txt` / `multitools.txt` — 延迟实现

物品注册正常执行（物品对象被创建并注册至 Fabric 注册表）。完整的弹射物机制和弹药行为**尚未实现**。

---

### §9 扩展 `item_effects/` 触发器类型

#### §9.1 `hitting_entity_to_yourself` 触发器

| 属性 | 值 |
|---|---|
| 配置文件 | `hitting_entity_to_yourself.txt`、`command_hitting_entity_to_yourself.txt` |
| 效果目标 | 攻击者本身（持有物品的实体），**而非**被攻击的实体 |
| 行格式 | 与其他物品效果格式完全相同（PARSER.md §4.1 / §4.2） |

该触发器在玩家手持匹配物品攻击任意实体时触发，并将配置的效果或命令应用于攻击者。

#### §9.2 `digging_block` 触发器

三个配置文件用于控制方块破坏触发器：

| 文件 | 类型 | 触发条件 |
|---|---|---|
| `digging_block.txt` | 物品型 | 玩家手持匹配物品破坏任意方块时 |
| `command_digging_block.txt` | 物品型，命令 | 玩家手持匹配物品破坏任意方块时 |
| `digging_block_block.txt` | 方块型 | 玩家破坏特定匹配方块时（持任意物品） |

**方块型效果格式**（`digging_block_block.txt`），字段分隔符 `_____`：

```
<block_name> "_____" <block_meta> "_____" <effect_id> "_____" <duration> "_____" <amplifier> "_____" <probability> "_____" <particle_type>
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `block_name` | 字符串 | 待匹配的方块标识符 |
| `block_meta` | 整数 | 方块元数据；`-1` = 任意 |
| `effect_id` | 字符串 | 药水效果标识符 |
| `duration` | 整数 | 持续时间（游戏刻） |
| `amplifier` | 整数 | 效果等级 |
| `probability` | 浮点数 | 触发概率 |
| `particle_type` | 枚举 | `none` \| `faded` \| `normal` |

**方块型命令格式**（`command_digging_block.txt` 中以方块为键时），字段分隔符 `_____`：

```
<block_name> "_____" <block_meta> "_____" <command_probability> "_____" <command>
```

---

### §10 Lucky Block 兼容层补充

#### §10.1 裸 `@chance` 属性规范化

部分 1.8.9 Lucky Block 附加包以裸 `@chance` 属性标记形式输出，不带赋值：

```
ID=lootplusplus:xxx@chance@luck=1
```

现代 Lucky Block Fabric 解析器将 `@` 视为键值字典的分隔符，并期望 `key=value` 键值对。无 `=` 的裸键会导致解析错误，中止对该掉落条目的处理。

**兼容规则**（在 `LuckyAttrParser` 中实现）：

- 裸 `@chance` 标记（即 `chance` 作为键存在，但无 `=` 且无值）规范化为 `chance=1`。
- 必须输出警告：

```
[WARN] [LootPP-Legacy] LuckyAttrBareChance bare @chance treated as chance=1 @ <packId>:<innerPath>:<lineNumber>
```

- 其他所有裸属性键**不进行强制修复**；保留其原始（可能导致错误）的行为。

#### §10.2 遗留实体 ID 规范化

1.8.9 附加包使用 1.13 版本之前的无命名空间实体名称（如 `EntityHorse`、`PigZombie`、`Item`）。这些名称在 1.18.2 中无法通过 `Identifier` 校验，必须进行重映射。

`LegacyEntityIdFixer` 中实现的映射表如下：

| 遗留名称 | 1.18.2 名称 | 备注 |
|---|---|---|
| `PigZombie` | `minecraft:zombified_piglin` | 直接重命名 |
| `EntityHorse`（NBT `Type=0`） | `minecraft:horse` | NBT `Type` 字段决定子类型 |
| `EntityHorse`（NBT `Type=1`） | `minecraft:donkey` | |
| `EntityHorse`（NBT `Type=2`） | `minecraft:mule` | |
| `EntityHorse`（NBT `Type=3`） | `minecraft:zombie_horse` | |
| `EntityHorse`（NBT `Type=4`） | `minecraft:skeleton_horse` | |
| `Item` | `minecraft:item` | 同时修复 NBT 子字段 `Item.id` 中缺失的命名空间 |
| `Cow`、`Pig` 等 | `minecraft:<lowercase>` | 简单转小写并添加命名空间前缀 |

实体重映射警告格式：

```
[WARN] [LootPP-Legacy] LegacyEntityId mapped 'EntityHorse' type=1 -> 'minecraft:donkey' @ <packId>:<innerPath>:<lineNumber>
[WARN] [LootPP-Legacy] LegacyItemId assumed namespace for 'iron_boots' -> 'minecraft:iron_boots' @ <packId>:<innerPath>:<lineNumber>
```

#### §10.3 遗留资源包补丁

`LegacyResourcePackPatcher` 透明地包装每个附加包的资源包，并应用以下格式修复。每次修复均输出警告。

| 应用的修复 | 警告类型 |
|---|---|
| 方块状态 `variants` 键 `"normal"` → `""`（空字符串） | `LegacyBlockstate` |
| 模型路径缺少 `block/` 或 `item/` 前缀 → 自动补充相应前缀 | `LegacyTexture` |
| 纹理路径 `blocks/` → `block/`；`items/` → `item/` | `LegacyTexture` |
| 旧版 1.8 纹理名称重映射至 1.18.2 等效名称（羊毛、陶瓦、原木名称等） | `LegacyTexture` |
| `.lang` 文件 → `.json` 翻译文件（重新编码；去除 BOM；UTF-8 严格模式，回退至 Latin-1） | `LegacyTexture` |

警告示例：

```
[WARN] [LootPP-Legacy] LegacyBlockstate converted variant 'normal' to '' in lucky/xxx_resources @ <packId>:<innerPath>:<lineNumber>
[WARN] [LootPP-Legacy] LegacyTexture mapped texture blocks/hardened_clay_stained_blue -> block/blue_terracotta @ <packId>:<innerPath>:<lineNumber>
```

---

### §11 文件编码规则

所有配置文本文件（`.txt`）按以下步骤依序进行解码：

1. 优先尝试严格 UTF-8 解码。
2. 若解码失败，回退至 ISO-8859-1 / CP1252。
3. 在进行任何后续处理之前，去除文件首行的 BOM 标记（`\uFEFF`，U+FEFF）。
4. 在 NBT 字符串中，在 SNBT 解析之前去除不可见字符（软连字符 U+00AD、非 BOM 位置的零宽不换行空格 U+FEFF、零宽空格 U+200B 及类似 Unicode 格式控制字符）；若有字符被去除，则输出警告（参见 §8.1）。

---

### §12 配方补充说明

#### §12.1 `add_shaped.txt` / `add_shapeless.txt` 中的矿物词典（OreDict）标记处理

在有序配方文件和无序配方文件中，以双引号（`"..."`）括起的原料标记被视为矿物词典键，而非直接物品标识符：

- 去除标记两端的双引号字符。
- 将剩余字符串用作矿物词典查询键。
- 必须输出警告：

```
[WARN] [LootPP-Legacy] LegacyOreDict key used @ <packId>:<innerPath>:<lineNumber>
```

- 若矿物词典键在 1.18.2 物品注册表中无匹配物品，则输出第二条警告，并跳过该原料槽位：

```
[WARN] [LootPP-Legacy] LegacyOreDict key 'ingotGold' has no matching items in registry, ingredient skipped @ <packId>:<innerPath>:<lineNumber>
```
