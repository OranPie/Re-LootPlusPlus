# Plan (Loot++ 1.20.1 Fabric Compat)

## Goals
- Reproduce Loot++ 1.8.9 behavior in 1.20.1 without modifying addon zips.
- Ensure all legacy syntax/IDs/params map with WARN (warnOnce allowed, never silent).
- Maintain 1:1 parser behavior (split rules, defaults, clamps, edge cases).

## Milestones & Order (lowest rework risk)
1) Pack discovery + indexing
   - Scan addon zips and build PackIndex with SourceLoc (zip/path/line/raw).
   - Read config text lines exactly (no trim before comment check; ignore empty lines only).
2) Strict parser layer
   - Implement split rules (Java String.split semantics; limit usage; drop trailing empties).
   - Implement per-file parsers per PARSER.md (block/entity drops, effects, chest/fishing, world_gen, item/block additions).
   - Enforce legacy WARN at parse time (meta, old ids, wildcard, old names, NBT leniency).
3) Legacy warn system
   - Centralized LegacyWarnReporter + warnOnce keying by type + SourceLoc.
   - All mappings (id/meta/selector/sound/effect/nbt) go through LegacyCompat.
4) LegacySelectorParser
   - Support @p/@a/@r/@e with legacy args (r/rm/score_* etc) and strict eval order.
   - Stable sorting, c semantics, and WARN for legacy params/negation/unknown.
5) LegacyCommandRunner (subset)
   - Implement tokenization with NBT/selector-aware parsing.
   - Implement commands used by packs: lppcondition/lppeffect/clear/effect/playsound/scoreboard/execute/testfor/summon/setblock/kill/enchant/gamerule.
   - Maintain successCount semantics (clear = removed item count; execute sums; others = affected targets).
   - CommandChain: split on top-level ';' only (warn on &&/|| as literal).
6) Registry & content bootstrap
   - Register items/blocks/entities/blockEntities/itemGroup at init only.
   - Build runtime definitions for thrown items + command_trigger_block.
7) Runtime index + hooks
   - Build RuntimeIndex (trigger -> id -> rules) for fast tick scans.
   - Install Fabric events for tick/use/attack/break/loot/worldgen; mixin only if needed.
8) Resource pack injection (client)
   - Inject assets/lootplusplus/** from addon zip via ResourcePackProvider.
9) Reload
   - /reload rebuilds runtime index and rules only; no new registries.

## Required Behavior Constraints
- Legacy parameters and mappings always WARN (never silent).
- Parsing failures are per-line WARN + skip (never crash whole pack).
- successCount definitions fixed per ADAPTION/ADDITION.
- DropGroup semantics: only first entry weight counts; if selected, execute all entries.

## Regression Targets (initial)
- Astral: lppcondition clear + score selectors + playsound/effect chain.
- Plural: thrown chaos ball command drops with r= selectors and legacy effect names.
- command_trigger_block world_gen line with CommandList NBT.

## Open TODOs (verify later)
- fishing_amounts exact grammar (if needed, confirm bytecode or add spec).
- entity_drops removing/add spec details (if needed, confirm bytecode).
- Legacy mappings json (sound/effect/item/block/entity) and chest_type mapping table.
- Addition 2 backlog:
  - Block_additions: support additional raw types (buttons, pressure_plates, slabs/stairs/panes/walls/etc) beyond generic/plants/crops/cakes.
  - LuckyBlock resource pack: confirm whole-zip mount order vs user packs.
  - Item additions: bows/guns/multitools full behavior (projectiles/ammo/tool types) beyond registration.
  - Effect mappings: expand numeric/legacy effect name tables as needed.
