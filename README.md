# Re-LootPlusPlus

Re-LootPlusPlus is a Fabric mod that replicates **Loot++ 1.8.9** and **Lucky Block** addon behavior on modern Minecraft **without modifying addon zip files**. It is a native re-implementation — not an injection shim — that scans addon zips, parses their `config/**/*.txt` rules, and wires everything into Fabric events and loot tables.

| Property | Value |
|---|---|
| Minecraft | `1.18.2` |
| Fabric Loader | `0.18.4` |
| Fabric API | `0.77.0+1.18.2` |
| Java | `17` |
| Mod ID | `re-lootplusplus` |

## What This Mod Does

- **Pack discovery** — scans multiple directories for addon zips/folders (see below).
- **Config parsing** — reads `config/**/*.txt` rules from every discovered pack.
- **Dynamic registration** — registers items, blocks, entities, and creative-tab entries required by addons at bootstrap time.
- **Lucky Block native impl** — own `NativeLuckyBlock` / `NativeLuckyBlockEntity` that evaluates Lucky drop lines without depending on the Lucky Block runtime at play time.
- **Legacy adaptation** — maps 1.8 IDs, sounds, effects, NBT, selectors, and commands to 1.18.2 equivalents, with mandatory WARN for every adaptation.
- **In-game UI** — `✦ Loot++` button in the pause menu opens a full pack-browser with drop-line viewer and texture gallery.
- **Diagnostics** — exports `report.json`, `warnings.tsv`, `thrown.tsv` per run.

## Addon Pack Discovery

`PackDiscovery` scans these directories (in the game directory) in fixed order:

1. `lootplusplus_addons/`
2. `addons/`, `addons/lucky/`, `addons/lucky_block/`
3. `packs/`
4. `mods/` (unless `scanModsDir=false`)
5. Paths in `config.extraAddonDirs`, `RELOOTPLUSPLUS_ADDONS` env var, or `-Drelootplusplus.addons` system property

Both `.zip` files and unpacked directories (any subdirectory containing a `config/` subfolder) are recognized.

## Build and Run

```bash
./gradlew compileJava --no-daemon    # compile only (fast check)
./gradlew build --no-daemon          # compile + package → build/libs/
./gradlew runClient --no-daemon      # launch game client (dev environment)
./gradlew runServer --no-daemon      # launch dedicated server (dev environment)
```

There is no automated test suite; use the in-game commands to validate behavior.

## Runtime Commands (require op)

| Command | Description |
|---|---|
| `dumpnbt item` | Print NBT of held item |
| `dumpnbt block` | Print NBT of targeted block |
| `lppdrop eval [x y z]` | Evaluate Lucky drops at position |
| `lppdrop eval_dry [x y z]` | Evaluate without executing |
| `lppdrop eval_counts <n> [x y z]` | Simulate N evaluations and print counts |
| `lppdrop lucky_eval [x y z]` | Run Lucky drop pipeline |
| `lppdrop lucky_eval_bulk <n> [x y z]` | Run Lucky drop pipeline N times |
| `lppdrop lucky_eval_bulk_dry <n> [x y z]` | Same but without executing |

## Config (`config/relootplusplus.json`)

The file is auto-created on first run. All fields can be overridden via system properties (`-Drelootplusplus.<field>=<value>`) or environment variables (`RELOOTPLUSPLUS_<FIELD>=<value>`).

### Core

| Field | Default | Description |
|---|---|---|
| `dryRun` | `false` | Parse-only mode — skip registration and hook installation |
| `exportReports` | `true` | Write diagnostics files on shutdown |
| `exportRawLines` | `true` | Include raw source lines in `warnings.tsv` |
| `exportDir` | `"logs/re_lootplusplus"` | Root directory for diagnostic exports |
| `extraAddonDirs` | `[]` | Additional directories to scan for addon packs |
| `duplicateStrategy` | `"suffix"` | How to handle duplicate pack/block/item IDs: `suffix` or `ignore` |
| `potioncoreNamespace` | `"re_potioncore"` | Namespace used when remapping `potioncore:*` effect IDs |
| `disabledAddonPacks` | `[]` | Pack IDs to skip during load |
| `injectResourcePacks` | `true` | Mount addon zip `assets/**` as a resource pack |
| `skipMissingEntityRenderers` | `false` | Suppress errors for entities without client renderers |

### Logging

| Field | Default | Description |
|---|---|---|
| `logWarnings` | `false` | Global warning logging gate |
| `logLegacyWarnings` | `false` | Print legacy WARN lines to console |
| `logDetailLevel` | `null` | Detail log level: `summary`, `detail`, or `trace` |
| `logDetailFilters` | `[]` | Module filter list (empty = all modules) |
| `legacyWarnConsoleLimitPerType` | `5` | Max console entries per WARN type (`0` = unlimited) |
| `legacyWarnConsoleSummary` | `true` | Print suppressed-count summary at bootstrap end |
| `debugFileEnabled` | `true` | Enable debug log file (requires level ≥ `detail`) |
| `debugFileMaxLines` | `0` | Truncate debug log file at N lines (`0` = unlimited) |

### Drop Engine

| Field | Default | Description |
|---|---|---|
| `luckModifier` | `0.1` | Luck-to-weight scale factor in `LuckyDropRoller` |
| `defaultLuck` | `0` | Baseline luck added to every Lucky Block break |
| `commandDropEnabled` | `true` | `false` skips all `type=command` Lucky drops |
| `dropChatEnabled` | `true` | `false` suppresses in-game chat messages for Lucky drops |

### Tick Hooks

| Field | Default | Description |
|---|---|---|
| `tickIntervalTicks` | `1` | Run player tick scan every N ticks |
| `enabledTriggerTypes` | `[]` | Trigger types to run (empty = all): `held`, `wearing_armour`, `in_inventory`, `standing_on_block`, `inside_block` |

### World & Pack

| Field | Default | Description |
|---|---|---|
| `structureMaxDimension` | `256` | Skip schematics with any axis larger than this (`0` = no limit) |
| `scanModsDir` | `true` | `false` prevents scanning the `mods/` directory for addon packs |
| `naturalGenEnabled` | `true` | `false` disables Lucky Block natural generation feature registration |
| `legacySanitizeEnabled` | `true` | `false` disables legacy drop-string sanitization |

## In-Game UI

The `✦ Loot++` button appears in the pause menu (top-left corner). It opens:

- **MenuScreen** — lists all discovered addon packs with enable/disable toggle, search filter, and status badges.
- **PackDetailScreen** — tabs: Overview, Items, Drops, Structures, Textures.
- **DropLinesScreen** — scrollable list of parsed Lucky drop lines for a pack.
- **ItemDetailScreen** — item kind, drop-line references.
- **RawLineScreen** — source text with line numbers and syntax coloring.
- **PackTextureGalleryScreen** — previews all textures found in a pack's assets.

## Diagnostics

When `exportReports=true`, each run writes to `<exportDir>/<timestamp>/`:

| File | Contents |
|---|---|
| `report.json` | Structured summary: config, bootstrap stats, warn-type counts, pack list |
| `warnings.tsv` | All legacy warnings, one per line, tab-separated with source location |
| `thrown.tsv` | Thrown-item definitions |

`<exportDir>/latest.txt` points to the most recent timestamp directory.

When `logDetailLevel=detail` or `trace` and `debugFileEnabled=true`, a `debug-<timestamp>.log` file is written to `<exportDir>/` with all `Log.debug` and `Log.trace` calls regardless of console filter.

## Documentation Map

| File | Contents |
|---|---|
| `STRUCTURE.md` | Package-level architecture: module responsibilities, bootstrap sequence, key design constraints |
| `PARSER.md` | Original Loot++ 1.8.9 parser spec (EBNF grammar, split rules, defaults, clamping) — the reference we implement against |
| `ADAPTION.md` | Legacy selector and command semantics spec |
| `INJECTION.md` | Fabric injection/registration strategy notes |
| `REFERENCE.md` | Hook matrix, execution order, and example parse traces |
| `ADDITION.md` | Additional config file formats and compatibility notes |
| `PLAN.md` | Original implementation plan (for historical context) |

## Troubleshooting

**Lucky drops produce no result / entity marked as removed:**
- The `minecraft:item` entity is spawned as a LootThrownItem; if the underlying item entity was already marked removed, the drop silently fails. Check `lppdrop eval_dry` output.

**Debug log is too large:**
- Set `debugFileMaxLines` to a non-zero value (e.g. `50000`) to truncate.

**Addon pack not loading:**
- Check `disabledAddonPacks` and ensure the zip contains a `config/` subfolder.
- Run with `logLegacyWarnings=true` to see per-line parse errors in the console.

**Server startup stops early:**
- Ensure `run/eula.txt` contains `eula=true`.
