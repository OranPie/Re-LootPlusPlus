# Re-LootPlusPlus

Re-LootPlusPlus is a Fabric mod that ports legacy Loot++/Lucky Block addon behavior to modern Minecraft without editing addon zip files.

Current target in this repository:
- Minecraft `1.18.2`
- Fabric Loader `0.18.4`
- Fabric API `0.77.0+1.18.2`
- Java `17`

## What This Mod Does
- Scans legacy addon packs in `run/addons/`.
- Parses legacy `config/*.txt` formats into runtime rules.
- Registers dynamic items/blocks/entities needed by addons.
- Injects/normalizes legacy crafting recipes at runtime.
- Adapts legacy IDs/meta/NBT/selectors/commands with explicit warnings.
- Exports diagnostics under `run/logs/re_lpp/Latest/<timestamp>/`.

## Project Status (March 4, 2026)
Implemented and validated:
- Legacy recipe item remaps (for example `minecraft:web -> minecraft:cobweb`).
- Runtime recipe sanitizer for invalid/missing ingredients and bad shaped keys.
- Shapeless recipe empty-ingredient guard.
- Lucky drop eval/debug commands (`/lppdrop ...`).
- Warning throttling controls (console cap per warning type + summary).

Known remaining upstream issues (outside Re-LootPlusPlus parser fixes):
- Lucky loader can still emit `Error reading addon crafting recipe` (`ReadCraftingKt` IndexOutOfBounds).
- Missing external structure files in some addons (`structures.txt` path not found).

## Build and Run
Build:
```bash
./gradlew compileJava --no-daemon
```

Run client:
```bash
./gradlew runClient --no-daemon
```

Run dedicated server:
```bash
./gradlew runServer --no-daemon
```

## Runtime Commands
Admin commands registered by this mod:

- `dumpnbt item`
- `dumpnbt block`
- `lppdrop eval [x y z]`
- `lppdrop eval_dry [x y z]`
- `lppdrop eval_counts <times> [x y z]`
- `lppdrop lucky_eval [x y z]`
- `lppdrop lucky_eval_bulk <times> [x y z]`
- `lppdrop lucky_eval_bulk_dry <times> [x y z]`

These are used to validate real runtime drop parsing and Lucky integration behavior.

## Config (`config/relootplusplus.json`)
Main options:

- `dryRun`: parse-only mode, no registration/hooks.
- `exportReports`: write diagnostics files.
- `exportRawLines`: include source raw lines in warning exports.
- `exportDir`: diagnostics output directory (default `logs/re_lpp`).
- `injectResourcePacks`: enable addon asset injection.
- `logWarnings`: global warning logging gate.
- `logLegacyWarnings`: legacy warning console output switch.
- `logDetailLevel`: shared Re-Loot++ log detail level (`summary`, `detail`, `trace`).
- `logDetailFilters`: optional module filter list for Re-Loot++ logs.
- `legacyWarnConsoleLimitPerType`: max console entries per warning type (`<=0` = unlimited).
- `legacyWarnConsoleSummary`: print suppressed warning summary at end of bootstrap.
- `disabledAddonPacks`: disable specific addon pack IDs.

System/env overrides are supported (see `ReLootPlusPlusConfig`).

## Diagnostics
Each run exports:
- `warnings.tsv`: full warning list with source location.
- `warn_types.txt`: warning counts by type.
- `summary.txt`: pack counts + load summary.
- `counts.txt`, `packs.txt`, `thrown_items.txt`.

Latest export pointer:
- `run/logs/re_lpp/latest.txt`

## Performance Notes
Recent optimizations:
- Warning collection switched from copy-on-write list to locked array list (better for high warning volume).
- Recipe item resolution now caches normalized lookups during recipe injection.
- Console warning spam can be capped per type while preserving full exported warning data.

## Documentation Map
The following docs are still useful as design/spec references:

- `STRUCTURE.md`: architecture layout and module boundaries.
- `PARSER.md`: strict legacy text grammar details.
- `ADAPTION.md`: selector/legacy command semantics.
- `INJECTION.md`: Fabric injection/registration strategy.
- `REFERENCE.md`: API/hook references and execution notes.
- `ADDITION.md`: compatibility backlog and implementation notes.
- `PLAN.md`: staged migration plan.

Note: Some docs discuss broader 1.20.1 planning history; this repository is currently wired for 1.18.2 runtime.

## Troubleshooting
If recipe errors appear again:
1. Check `run/logs/latest.log` for `Parsing error loading recipe`.
2. Check `run/logs/re_lpp/Latest/<ts>/warnings.tsv` for `LegacyRecipe*` and `LegacyItemId` entries.
3. Run `/lppdrop eval_counts` and `/lppdrop lucky_eval_bulk_dry` to validate runtime parsing behavior quickly.

If server startup stops early:
- Ensure `run/eula.txt` contains `eula=true`.
