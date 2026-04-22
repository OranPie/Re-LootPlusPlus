# Re-LootPlusPlus — Copilot Instructions

## What this mod does

Re-LootPlusPlus is a Minecraft 1.18.2 Fabric mod (`mod id: re-lootplusplus`, package root `ie.orangep.reLootplusplus`) that replicates the behavior of **Loot++ 1.8.9** on modern Fabric **without modifying existing addon zips**. It scans addon zips/folders from multiple game directories (see Pack discovery below), reads their `config/**/*.txt` files, and wires the parsed rules into Fabric events and loot tables.

The Lucky Block dependency (`run/mods/lucky-block-fabric-1.18.2-13.0.jar`) is compile-only; `fabric.mod.json` at the repo root belongs to that jar — the actual mod's `fabric.mod.json` is in `src/main/resources/`.

## Build and run

```bash
./gradlew build --no-daemon          # compile + package → build/libs/
./gradlew runClient --no-daemon      # launch game client (dev environment)
./gradlew runServer --no-daemon      # launch dedicated server (dev environment)
./gradlew compileJava --no-daemon    # compile only (faster check)
```

There is no test suite in this project.

## Architecture

### Bootstrap sequence (must follow this order)

`Bootstrap.java` is the single orchestrator. On `ModInitializer#onInitialize` it runs these phases in order:

1. **Pack discovery** — scan multiple directories for addon zips/folders → `List<AddonPack>` (see below)
2. **Pack indexing** — build `PackIndex` with `SourceLoc` per line (zip / inner path / line number / raw text)
3. **Parse all rules** — one `*Loader` per config domain (effects, commands, drops, chest, fishing, world_gen, recipes, …)
4. **Register content** — `DynamicItemRegistrar`, `DynamicBlockRegistrar`, `EntityRegistrar`, `CreativeMenuRegistrar` — **registry writes happen here only**
5. **Build `RuntimeIndex`** — maps `TriggerType → itemId/blockId → List<Rule>` for fast tick lookups
6. **Install hooks** — `HookInstaller` registers Fabric events
7. **Register reload listener** — `/reload` only rebuilds `RuntimeIndex` and rules, never touches registries

**Hard constraint:** item/block/entity registration is impossible after bootstrap. `/reload` must not attempt it.

### Addon pack discovery

`PackDiscovery` scans these directories (in the game directory) in a fixed order, deduplicating by pack id:

1. `lootplusplus_addons/`
2. `addons/`, `addons/lucky/`, `addons/lucky_block/`
3. `packs/`
4. `mods/`
5. Directories from `config.extraAddonDirs`, `RELOOTPLUSPLUS_ADDONS` env var, or `-Drelootplusplus.addons` system property

Both `.zip` files **and** unpacked directories (any subdirectory containing a `config/` subfolder) are recognized as addon packs.

### Addon zip dual-role

Each addon zip is both a **config source** (server-side: `config/**/*.txt`) and a **resource pack** (client-side: `assets/**`). `AddonPack` is shared by both sides. `ExternalPackProvider` / `ExternalZipResourcePack` expose the full `assets/**` tree — not just one namespace — because many packs include `assets/lucky/**`. `LegacyPatchingResourcePack` / `LegacyResourcePackPatcher` fix up 1.8-era resource formats (old blockstate variant keys, `blocks/` → `block/` texture paths, `.lang` → `.json`, etc.) and always emit a WARN for each fix.

### Key package responsibilities

| Package | Role |
|---|---|
| `bootstrap/` | Orchestration and phase reporting |
| `pack/` | Zip/dir discovery, `PackIndex`, `SourceLoc`-aware line reading |
| `config/parse/` | Low-level utilities: `Splitter`, `LineReader`, `NumberParser` |
| `config/loader/` | One loader class per config domain |
| `config/model/` | Data model: `key/`, `rule/`, `drop/`, `item/`, `block/` |
| `legacy/` | All legacy-compat mappings (id, sound, effect, entity, NBT, selector) |
| `registry/` | Dynamic registration of items/blocks/entities during bootstrap |
| `runtime/` | `RuntimeIndex`, `RuleEngine`, `RuntimeContext`, trigger types, drop/chest/entity/stack registries |
| `hooks/` | Fabric event hooks (tick, use, attack, break, chest loot, entity death) |
| `command/` | `LegacyCommandRunner`, `CommandChain`, `ExecContext`/`ExecResult` |
| `recipe/` | `NbtShapedRecipe`, `NbtShapelessRecipe`, `ModRecipes` — runtime recipe injection |
| `resourcepack/` | Addon zip resource pack injection (client) |
| `diagnostic/` | `LegacyWarnReporter`, `SourceLoc`, `DiagnosticExporter` |
| `client/` | `ReLootPlusPlusClient` and debug UI screens |
| `mixin/` | Thin Fabric/LuckyBlock mixins (see below) |

### LuckyBlock mixin layer

The `mixin/Lucky*` classes form a dedicated compatibility shim between Re-LootPlusPlus and the LuckyBlock Fabric mod (compile-only dep). These mixins are **thicker** than others because there is no Fabric API equivalent for LuckyBlock internals:

- `LuckyParserMixin` — tolerates bare `@chance` attribute (no `=` value) by normalizing it to `chance=1` + WARN
- `LuckyFabricGameApiMixin` — normalizes legacy entity IDs in NBT before `EntityType.loadEntityWithPassengers` (e.g. `PigZombie` → `zombified_piglin`, `EntityHorse` with NBT `Type` field → correct horse variant)
- `LuckyLoaderMixin` — patches addon crafting recipe parsing errors that would otherwise crash the entire addon load
- `LuckyDropActionsMixin`, `LuckyDropEvaluatorMixin`, `LuckyDropSanitizerMixin`, `LuckySingleDropMixin`, `LuckyWeightedDropMixin` — intercept Lucky drop evaluation to apply sanitization and compat fixes
- `LuckyAddonCraftingMixin`, `LuckyAddonCraftingSafeMixin`, `LuckyAddonIdFallbackMixin` — handle item ID fallback for addon-registered items
- `LuckyPluginInitMixin` — hook into LuckyBlock plugin initialization

All Lucky* mixin findings must still go through `LegacyWarnReporter`.

## Config file

At runtime the mod reads/writes `.minecraft/config/relootplusplus.json`. Key fields:

```json
{
  "dryRun": false,
  "exportReports": true,
  "exportRawLines": true,
  "exportDir": "logs/re_lpp",
  "extraAddonDirs": [],
  "duplicateStrategy": "suffix",
  "potioncoreNamespace": "re_potioncore",
  "skipMissingEntityRenderers": false,
  "injectResourcePacks": true,
  "logWarnings": false,
  "logLegacyWarnings": false,
  "legacyWarnConsoleLimitPerType": 5,
  "legacyWarnConsoleSummary": true,
  "disabledAddonPacks": []
}
```

All fields can be overridden via system properties (`-Drelootplusplus.<field>=<value>`) or environment variables (`RELOOTPLUSPLUS_<FIELD>=<value>`). `dryRun=true` parses everything but skips registration and hook installation.

`legacyWarnConsoleLimitPerType` caps console output per WARN type (≤0 = unlimited) while still writing all warnings to the export file. `legacyWarnConsoleSummary` prints a suppressed-count summary at bootstrap end.

## Runtime commands

Admin commands (require op):

- `dumpnbt item` / `dumpnbt block` — print NBT of held item or targeted block
- `lppdrop eval [x y z]` / `lppdrop eval_dry [x y z]` — evaluate lucky drops (with/without executing)
- `lppdrop eval_counts <times> [x y z]` — simulate N evaluations and print counts
- `lppdrop lucky_eval [x y z]` / `lppdrop lucky_eval_bulk <times> [x y z]` / `lppdrop lucky_eval_bulk_dry <times> [x y z]` — Lucky drop pipeline variants

## Diagnostics export

When `exportReports=true`, each run writes to `<exportDir>/Latest/<timestamp>/`:
`warnings.tsv`, `warn_types.txt`, `summary.txt`, `counts.txt`, `packs.txt`, `thrown_items.txt`.
`<exportDir>/latest.txt` points to the most recent timestamp directory.

## Key conventions

### Config text separators

- **Field separator:** `_____` (five underscores) — used in nearly every `.txt` format
- **Drop group separator:** `%%%%%` (five percent signs) — separates entries within a drop group

Always use `Splitter.splitRegex(input, "_____")` / `Splitter.splitRegex(input, "%%%%%")`, not `String.split()` directly. This mirrors Java's `Pattern.compile(regex).split(input, limit)` semantics, which **drops trailing empty fields** at `limit=0`. Preserve this behavior.

### Per-line error handling

Parse failures are per-line: WARN + skip that line. Only unreadable zip/file I/O is a hard error (and should still load remaining packs). Never throw or crash the bootstrap on a single bad line.

### DropGroup weight semantics

Only the **first entry's weight** in a `%%%%%`-separated group participates in the weighted roll. If the group is selected, **all entries** in it execute. This is a 1:1 match of Loot++ 1.8.9 behavior and must not be changed.

### successCount definitions (fixed — required for `lppcondition`)

| Command | successCount |
|---|---|
| `clear` | actual number of items removed |
| `effect`, `playsound`, `scoreboard`, `kill`, `testfor` | number of targets successfully acted on |
| `execute` | sum of sub-command successCounts across all targets |
| `summon`, `setblock`, `gamerule` | 1 on success, 0 on failure |

`lppcondition` evaluates the condition's `successCount > 0` to choose the `_if_true_` / `_if_false_` branch and returns the branch's successCount.

### Legacy compat — always WARN, never silent

All 1.8 → 1.18 adaptations must go through `legacy/` classes and always call `LegacyWarnReporter.warn()` or `warnOnce()`. No loader, runtime class, or command impl may silently fix legacy values.

WARN format:
```
[WARN] [LootPP-Legacy] <Type> <detail> @ <packId>:<innerPath>:<lineNumber>
```

Common WARN types: `MetaWildcard`, `SelectorParam`, `EffectName`, `SoundId`, `EntityId`, `BlockId`, `ItemId`, `LegacyNBT`, `LegacyChestType`, `LuckyAttrBareChanc`, `LegacyBlockstate`, `LegacyTexture`.

`SourceLoc` (packId / packPath / innerPath / lineNumber / rawLine) must accompany every warn/error for precise traceability.

### File encoding

Read config text UTF-8 strict first; fall back to ISO-8859-1/CP1252. Strip leading BOM (`\uFEFF`) before processing.

### Item/block ID normalization

External addon names often contain dots or uppercase (e.g., `astral.fairy`). Normalize to a stable Fabric `Identifier` (lowercase, dots → underscores) and store the raw name for logging. Register under `lootplusplus:<normalized>` namespace.

### `potioncoreNamespace`

Addon packs may reference `potioncore:*` effect IDs. The config field `potioncoreNamespace` (default `re_potioncore`) controls the namespace used when remapping these IDs — always WARN when remapping.

### Tick scan order (fixed for determinism)

When scanning players each server tick, process trigger types in this fixed order:
1. `held` (main hand)
2. `wearing_armour`
3. `in_inventory`
4. `standing_on_block`
5. `inside_block`

Order matters for chains that combine `clear` + `effect` (e.g., Astral Fairy pack).

### Mixin policy

Prefer Fabric API events over mixins. Add mixins only when the API is insufficient (e.g., fine-grained drop/looting interaction, resource pack list injection). Keep mixins thin — pass data to `RuleEngine` rather than embedding logic.
