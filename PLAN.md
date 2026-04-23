# Re-LootPlusPlus — Implementation Notes

> **Status: Implementation complete.**
> This file was originally the staged build plan. It is now kept as architectural reference and a log of key decisions.

---

## What was built

Re-LootPlusPlus is a Minecraft **1.18.2 Fabric** mod that:

1. **Natively reimplements Loot++ 1.8.9 behavior** — scans addon zips/folders, parses `config/**/*.txt`, wires rules into Fabric events. No modification of addon zips.
2. **Natively reimplements Lucky Block** — registers `lucky:lucky_block`, `lucky:lucky_sword`, `lucky:lucky_bow`, `lucky:lucky_potion` under the `lucky:` namespace. Parses `drops.txt`/`bow_drops.txt` from Lucky addon zips. No dependency on the Lucky Block Fabric jar at runtime.
3. **Provides a full in-game UI** — accessible via "✦ Loot++" button injected into the pause menu.

---

## Architecture summary

See `STRUCTURE.md` for the full package tree. Key architectural points:

- **`Bootstrap.java`** is the single orchestrator. Nine sequential phases: load config → discover packs → index → parse rules → register content → world gen → build RuntimeIndex → install hooks → export diagnostics.
- **Registry lock**: item/block/entity registration happens only during phase 5. `/reload` rebuilds only `RuntimeIndex` and rules.
- **`LegacyWarnReporter`**: all 1.8→1.18.2 adaptations must call `warn()` or `warnOnce()`. Never silent.
- **`DebugFileWriter`**: when `debugFileEnabled=true` and `logDetailLevel≥detail`, all debug/trace lines are written to `<exportDir>/debug-<timestamp>.log` regardless of console filter level.

---

## Config

Runtime config at `.minecraft/config/relootplusplus.json`. 25 fields in four categories:

- **Core**: `dryRun`, `exportReports`, `exportRawLines`, `exportDir`, `extraAddonDirs`, `duplicateStrategy`, `potioncoreNamespace`, `skipMissingEntityRenderers`, `injectResourcePacks`, `disabledAddonPacks`
- **Logging**: `logWarnings`, `logLegacyWarnings`, `logDebug`, `logDetailLevel`, `logDetailFilters`, `legacyWarnConsoleLimitPerType`, `legacyWarnConsoleSummary`, `debugFileEnabled`, `debugFileMaxLines`
- **Drop engine**: `luckModifier`, `defaultLuck`, `commandDropEnabled`, `dropChatEnabled`
- **Tick / world**: `tickIntervalTicks`, `enabledTriggerTypes`, `structureMaxDimension`, `scanModsDir`, `naturalGenEnabled`, `legacySanitizeEnabled`

---

## Behavioral constraints (fixed)

### DropGroup weight semantics
Only the first entry's weight in a `%%%%%`-separated group participates in the weighted roll. If the group is selected, **all** entries execute. 1:1 match of Loot++ 1.8.9.

### successCount definitions
| Command | successCount |
|---|---|
| `clear` | actual number of items removed |
| `effect`, `playsound`, `scoreboard`, `kill`, `testfor` | number of targets successfully acted on |
| `execute` | sum of sub-command successCounts across all targets |
| `summon`, `setblock`, `gamerule` | 1 on success, 0 on failure |
| `lppcondition` | branch's successCount |

### Tick scan order (deterministic)
`held` → `wearing_armour` → `in_inventory` → `standing_on_block` → `inside_block`

---

## Known gaps / deferred items

- `fishing_amounts` grammar: partially implemented; edge cases not fully verified against JAR bytecode.
- `entity_drops` removing spec: basic support; complex selector interactions may differ from 1.8.9.
- Item additions: bows/guns/multitools — registration works; full projectile/ammo behavior is partial.
- Block additions: additional raw types (buttons, pressure plates, slabs, stairs, panes, walls) beyond generic/plants/crops/cakes are not implemented.
- `.luckystruct` structure format: read support exists but placement fidelity may differ from original.

---

## Spec documents

The other markdown files describe the Loot++ 1.8.9 behavioral spec extracted from JAR bytecode analysis. They define what this mod implements against:

- `PARSER.md` — Config file grammar (BNF/EBNF for every `config/*.txt` format)
- `ADAPTION.md` — Legacy selector parser and command runner semantics
- `INJECTION.md` — Fabric injection strategy and lifecycle constraints
- `REFERENCE.md` — Hook matrix, execution order, and parse/execute examples
- `ADDITION.md` — Additional format specs (command chains, record format, etc.)
