# Re-LootPlusPlus — Architecture

This document describes the actual package structure and module responsibilities of the **1.18.2 Fabric** implementation. The mod replicates Loot++ 1.8.9 addon behavior natively (no zip modification) and natively reimplements the Lucky Block mod.

---

## Source layout

```
src/main/java/ie/orangep/reLootplusplus/
├── ReLootPlusPlus.java                    // ModInitializer entry point
│
├── bootstrap/
│   ├── Bootstrap.java                     // Phase orchestrator (see sequence below)
│   └── BootstrapReport.java               // Pack/rule/warn counts collected during bootstrap
│
├── config/
│   ├── ReLootPlusPlusConfig.java          // JSON config model + load/save/override logic (25 fields)
│   ├── AddonDisableStore.java             // Persists per-pack enable/disable state
│   ├── CustomRemapStore.java              // User-defined ID remaps
│   ├── TextureAdditionStore.java          // Extra texture paths from config
│   │
│   ├── loader/                            // One loader class per config domain
│   │   ├── BlockAdditionsLoader.java      // config/block_additions/*.txt
│   │   ├── BlockDropsLoader.java          // config/block_drops/adding.txt + removing.txt
│   │   ├── ChestContentLoader.java        // config/chest_content/*.txt
│   │   ├── CommandEffectLoader.java       // config/item_effects/command_*.txt
│   │   ├── CreativeMenuLoader.java        // config/general/creative_menu_additions.txt
│   │   ├── EffectLoader.java              // config/item_effects/*.txt (potion effects)
│   │   ├── EntityDropsLoader.java         // config/entity_drops/*.txt
│   │   ├── FishingLootLoader.java         // config/fishing_loot/*.txt
│   │   ├── FurnaceRecipesLoader.java      // config/furnace_recipes/*.txt
│   │   ├── ItemAdditionsLoader.java       // config/item_additions/*.txt
│   │   ├── RecipesLoader.java             // config/recipes/*.txt
│   │   ├── RecordsLoader.java             // config/records/records.txt
│   │   ├── StackSizeLoader.java           // config/stack_size/stack_sizes.txt
│   │   ├── ThrownLoader.java              // config/item_additions/thrown.txt
│   │   └── WorldGenLoader.java            // config/world_gen/surface.txt + underground.txt
│   │
│   ├── model/                             // Parsed rule/def data models
│   │   ├── block/                         // GenericBlockDef, PlantBlockDef, CropBlockDef, CakeBlockDef, ...
│   │   ├── drop/                          // DropEntry, DropGroup, DropRoller (weight semantics)
│   │   ├── general/                       // CreativeMenuEntry
│   │   ├── item/                          // GenericItemDef, FoodDef, SwordDef, ToolDef, ArmorDef, ...
│   │   ├── key/                           // ItemKey (id+meta+nbtPredicate), BlockKey
│   │   ├── recipe/                        // ShapedRecipeDef, ShapelessRecipeDef, RecipeInput, ...
│   │   └── rule/                          // EffectRule, CommandRule, BlockDropRule, ChestLootRule, ...
│   │
│   └── parse/
│       ├── LineReader.java                // Skip blank lines and # / // comments; preserve SourceLoc
│       ├── Splitter.java                  // Java-split semantics for "_____" and "%%%%%"
│       └── NumberParser.java             // int/float/bool with WARN on failure
│
├── diagnostic/
│   ├── Log.java                           // Unified logger; DetailLevel: SUMMARY / DETAIL / TRACE
│   ├── DebugFileWriter.java               // Writes all debug/trace lines to file regardless of console level
│   ├── DiagnosticExporter.java            // Exports report.json + warnings.tsv + thrown.tsv
│   ├── LegacyWarnReporter.java            // Centralized warn collection; console cap + suppression summary
│   ├── SourceLoc.java                     // packId / packPath / innerPath / lineNumber / rawLine
│   ├── WarnEntry.java                     // Single warning record
│   └── WarnKey.java                       // De-dup key for warnOnce()
│
├── legacy/
│   ├── LegacyDropSanitizer.java           // Pre-processes drop strings before Lucky parsing
│   ├── mapping/
│   │   ├── LegacyBlockIdMapper.java       // 1.8 block IDs + tile entity IDs → 1.18.2
│   │   ├── LegacyChestTypeMapper.java     // Loot++ chest type names → 1.18.2 loot-table IDs
│   │   ├── LegacyEffectIdMapper.java      // Numeric/legacy effect names → registry Identifier
│   │   ├── LegacyEnchantmentIdMapper.java // Numeric/legacy enchantment IDs → registry Identifier
│   │   ├── LegacyEntityIdFixer.java       // Entity ID normalization (EntityHorse→horse, PigZombie→zombified_piglin, …)
│   │   ├── LegacyItemIdMapper.java        // 1.8 item IDs + meta → 1.18.2 flat IDs
│   │   ├── LegacyItemNbtFixer.java        // 1.8 item NBT repair (attribute names, display, ench tags)
│   │   ├── LegacyParticleIdMapper.java    // Legacy particle names → 1.18.2 Identifier
│   │   └── LegacyTileEntityNbtFixer.java  // Tile entity NBT repair; SpawnData migration
│   ├── nbt/
│   │   ├── LenientNbtParser.java          // Tolerant SNBT parser; normalizes and WARNs
│   │   └── NbtPredicate.java              // NBT contains-match used for item/block key filtering
│   ├── selector/
│   │   ├── LegacySelectorParser.java      // @p/@a/@r/@e with legacy r/rm/score_*/c params
│   │   └── SelectorContext.java           // Selector evaluation context (world, origin, random)
│   └── text/
│       └── LegacyText.java                // § color code + modifier parsing for display names
│
├── pack/
│   ├── AddonPack.java                     // Single addon zip or directory (id, path, files)
│   ├── PackDiscovery.java                 // Scans game directories; deduplicates by pack ID
│   ├── PackIndex.java                     // Per-pack file listing with SourceLoc-aware line reading
│   └── io/
│       └── PackFileReader.java            // UTF-8 strict + ISO-8859-1 fallback; BOM strip
│
├── registry/
│   ├── CreativeMenuRegistrar.java         // Registers dynamic items into creative tabs
│   ├── DynamicBlockRegistrar.java         // Registers addon blocks from block_additions defs
│   ├── DynamicItemRegistrar.java          // Registers addon items + thrown items
│   └── EntityRegistrar.java              // Registers LootThrownItemEntity + NativeLuckyProjectile
│
├── runtime/
│   ├── BlockDropRegistry.java             // block_drops rules indexed by block ID
│   ├── ChestLootRegistry.java             // chest_content rules indexed by loot table ID
│   ├── EntityDropRegistry.java            // entity_drops rules indexed by entity type
│   ├── RuleEngine.java                    // Entry point: tick/event → index lookup → execute
│   ├── RuntimeContext.java                // server, world, player, random, warnReporter
│   ├── RuntimeIndex.java                  // Maps TriggerType → itemId/blockId → List<Rule>
│   ├── RuntimeState.java                  // Static holder for config + all registries
│   ├── StackSizeRegistry.java             // Per-item stack size overrides
│   ├── ThrownRegistry.java                // Thrown-item defs indexed by item ID
│   └── trigger/
│       └── TriggerType.java               // HELD, IN_INVENTORY, BLOCKS_IN_INVENTORY, WEARING_ARMOUR, STANDING_ON_BLOCK, INSIDE_BLOCK
│
├── hooks/
│   ├── HookInstaller.java                 // Installs all Fabric events on server start
│   ├── ServerTickHook.java                // Tick scan: held/armour/inventory/standing/inside
│   ├── UseItemHook.java                   // Right-click item trigger
│   ├── AttackHook.java                    // hitting_entity_to_entity trigger
│   ├── ThrownUseHook.java                 // Thrown-item entity impact
│   ├── BlockBreakHook.java                // block_drops trigger
│   ├── EntityDeathHook.java               // entity_drops trigger
│   └── ChestLootHook.java                 // chest_content injection via LootManagerMixin
│
├── command/
│   ├── DumpNbtCommand.java                // /dumpnbt item|block
│   ├── LuckyDropEvalCommand.java          // /lppdrop eval|eval_dry|eval_counts|lucky_eval|...
│   ├── LegacyCommandRunner.java           // Interprets 1.8 command subset (successCount semantics)
│   └── exec/
│       ├── CommandChain.java              // Splits on top-level `;`; warns on && / ||
│       ├── ExecContext.java               // Executor, position, world, random, SourceLoc
│       └── ExecResult.java                // successCount + optional debug info
│
├── content/
│   ├── entity/
│   │   └── LootThrownItemEntity.java      // Thrown item entity; executes drop group on impact
│   ├── item/
│   │   ├── LegacyNamedItem.java           // Base dynamic item with translated display name
│   │   ├── LootThrownItem.java            // Right-click to throw LootThrownItemEntity
│   │   └── LegacyNamed{Sword,Bow,Axe,Shovel,Pickaxe,Hoe,Armor,BlockItem}.java
│   └── material/
│       ├── LegacyArmorMaterial.java       // Armor material from addon spec
│       └── LegacyToolMaterial.java        // Tool material from addon spec
│
├── recipe/
│   ├── ModRecipes.java                    // Registers NbtShapedRecipe + NbtShapelessRecipe serializers
│   ├── NbtShapedRecipe.java               // Shaped recipe that preserves NBT on crafting output
│   └── NbtShapelessRecipe.java            // Shapeless variant
│
├── resourcepack/
│   ├── AddonResourceIndex.java            // Scans all textures in an addon pack's assets/
│   ├── AddonBlockResourcePack.java        // Generates blockstate/model JSON for dynamic blocks
│   ├── CombinedResourcePack.java          // Combines multiple resource packs as one
│   ├── ExternalPackProvider.java          // ResourcePackProvider: mounts addon zips as resource packs
│   ├── ExternalZipResourcePack.java       // AbstractFileResourcePack backed by a zip entry
│   ├── LegacyPatchingResourcePack.java    // Wraps a pack to apply LegacyResourcePackPatcher fixes
│   ├── LegacyResourcePackPatcher.java     // Fixes 1.8 blockstates, texture paths, .lang→.json
│   └── ResourcePackUiHelper.java          // Helpers for the in-game pack list screen
│
├── lucky/
│   ├── attr/
│   │   ├── LuckyAttr.java                 // Parsed key=value attr from a Lucky drop line
│   │   └── LuckyAttrParser.java           // Parses the `@attr=val` syntax; bare `@chance` → `chance=1` + WARN
│   ├── block/
│   │   ├── NativeLuckyBlock.java          // Lucky Block; reads luck from block entity + defaultLuck config
│   │   ├── NativeLuckyBlockEntity.java    // Stores luck value + optional custom drop list
│   │   └── NativeLuckyBlockItem.java      // Block item with luck tooltip
│   ├── crafting/
│   │   ├── LuckyLuckCraftingLoader.java   // Loads luck-modifier crafting recipes from addon data
│   │   └── LuckyLuckModifierRecipe.java   // Recipe that sets luck NBT on a lucky block item
│   ├── drop/
│   │   ├── LuckyDropContext.java          // Context passed to all drop actions (world, pos, player, luck)
│   │   ├── LuckyDropEngine.java           // Selects + executes a drop; chat message; debug trace
│   │   ├── LuckyDropEvaluator.java        // Evaluates a single LuckyDropLine
│   │   ├── LuckyDropFieldRegistry.java    // Maps field names to attribute setters
│   │   ├── LuckyDropLine.java             // Parsed drop line: type, weight, attrs, group entries
│   │   ├── LuckyDropParser.java           // Parses Lucky drop format (key=value attrs separated by commas)
│   │   └── LuckyDropRoller.java           // Weighted random selection; applies luckModifier config
│   ├── drop/action/                       // One class per Lucky drop `type=` value
│   │   ├── LuckyItemDropAction.java       // type=item
│   │   ├── LuckyEntityDropAction.java     // type=entity
│   │   ├── LuckyBlockDropAction.java      // type=block
│   │   ├── LuckyStructureDropAction.java  // type=structure
│   │   ├── LuckyCommandDropAction.java    // type=command (respects commandDropEnabled config)
│   │   ├── LuckyEffectDropAction.java     // type=effect
│   │   ├── LuckySoundDropAction.java      // type=sound
│   │   ├── LuckyExplosionDropAction.java  // type=explosion
│   │   ├── LuckyFillDropAction.java       // type=fill
│   │   ├── LuckyParticleDropAction.java   // type=particle
│   │   ├── LuckyProjectileDropAction.java // type=projectile
│   │   ├── LuckyThrowDropAction.java      // type=throw
│   │   ├── LuckyMessageDropAction.java    // type=message
│   │   ├── LuckyTimeDropAction.java       // type=time
│   │   ├── LuckyChestDropAction.java      // type=chest
│   │   ├── LuckyDifficultyDropAction.java // type=difficulty
│   │   └── LuckyAttrToNbt.java            // Converts Lucky attrs to NBT compound
│   ├── entity/
│   │   ├── NativeLuckyProjectile.java     // Lucky projectile entity (type=projectile)
│   │   └── NativeThrownLuckyPotion.java   // Lucky potion throw entity
│   ├── hook/
│   │   └── LuckyBlockBreakHook.java       // Hooks into block-break to fire LuckyDropEngine
│   ├── item/
│   │   ├── NativeLuckyBow.java            // Lucky bow item
│   │   ├── NativeLuckyPotion.java         // Lucky potion item
│   │   └── NativeLuckySword.java          // Lucky sword item
│   ├── loader/
│   │   ├── LuckyAddonData.java            // Parsed per-addon data (drops, structures, natural gen, properties)
│   │   ├── LuckyAddonLoader.java          // Reads Lucky addon zip files; merges drops from all addons
│   │   ├── LuckyAddonProperties.java      // Parsed properties.txt fields (luck ranges, block textures, …)
│   │   ├── LuckyNaturalGenEntry.java      // One entry from natural_gen.txt
│   │   ├── LuckyPluginInit.java           // Parses plugin_init.txt per addon
│   │   └── LuckyStructureEntry.java       // One entry from structures.txt
│   ├── registry/
│   │   ├── AddonLuckyRegistrar.java       // Registers one Lucky Block variant per addon
│   │   └── LuckyRegistrar.java            // Registers base lucky:lucky_block + items
│   ├── structure/
│   │   ├── LuckyStructReader.java         // Reads Lucky .luckystruct format
│   │   ├── NbtStructureReader.java        // Reads vanilla .nbt structure files
│   │   ├── ParsedStructure.java           // record(width, height, length, blocks)
│   │   ├── SchematicReader.java           // Reads MCEdit .schematic; respects structureMaxDimension config
│   │   ├── StructureBlock.java            // One block within a ParsedStructure
│   │   ├── StructureFileLoader.java       // Dispatches to the right reader by file extension
│   │   └── StructurePlacer.java           // Places a ParsedStructure in the world
│   ├── template/
│   │   └── LuckyTemplateVars.java         // Expands #posX / #rand / #circleOffset / … template vars
│   └── worldgen/
│       ├── LuckyNaturalGenFeature.java    // Fabric Feature that places Lucky Blocks naturally
│       └── LuckyNaturalGenRegistrar.java  // Registers per-addon natural-gen features (gated by naturalGenEnabled)
│
├── mixin/
│   ├── ArmorFeatureRendererMixin.java     // Client: dynamic armor texture rendering
│   ├── BlockDropMixin.java                // Fine-grained block drop interception (fortune/silk)
│   ├── EntityRenderDispatcherMixin.java   // Client: suppress missing-renderer errors (skipMissingEntityRenderers)
│   ├── GameMenuScreenMixin.java           // Injects "✦ Loot++" button into pause menu
│   ├── ItemStackSizeMixin.java            // Applies StackSizeRegistry overrides
│   ├── LootManagerMixin.java              // Injects chest_content rules into loot table loading
│   ├── MinecraftClientMixin.java          // Client-side resource-pack reload hook
│   ├── PackScreenMixin.java               // Resource pack screen: shows addon packs
│   ├── PlayerEntityEatMixin.java          // Food effect application for addon foods
│   ├── PlayerManagerMixin.java            // Server-side player join hook
│   ├── RecipeManagerMixin.java            // Injects dynamic recipes from RecipesLoader
│   └── ResourcePackProfile{Mixin,Accessor,EntryMixin}.java   // Resource pack list injection
│
└── client/
    ├── AddonTextureLoader.java            // Loads and registers atlas sprites for addon textures
    ├── ReLootPlusPlusClient.java          // ClientModInitializer: texture loader + resource pack provider
    └── screen/
        ├── LppUi.java                     // Shared drawing helpers (header bar, tab bar, row stripe, …)
        ├── PackTextureGalleryScreen.java  // Texture gallery tab — sprite grid with hover tooltip
        ├── ReLootPlusPlusDropLinesScreen.java  // Drop lines viewer (scrollable, type-colored)
        ├── ReLootPlusPlusItemDetailScreen.java // Item detail: kind + drop-line references
        ├── ReLootPlusPlusMenuScreen.java   // Main pack list screen (access via ✦ Loot++ in pause menu)
        ├── ReLootPlusPlusPackDetailScreen.java // Per-pack detail: Overview / Items / Drops / Structures / Textures tabs
        └── ReLootPlusPlusRawLineScreen.java    // Source text viewer with line numbers and syntax coloring
```

---

## Bootstrap Sequence

`Bootstrap.java` runs these phases in order inside `ModInitializer#onInitialize`:

1. **Load config** — `ReLootPlusPlusConfig.load()`; open `DebugFileWriter` if `debugFileEnabled` and level ≥ DETAIL.
2. **Pack discovery** — `PackDiscovery.discover()` → `List<AddonPack>`; deduplicates by pack ID.
3. **Pack indexing** — `PackIndex` with `SourceLoc` (zip / inner path / line number / raw text).
4. **Parse all rules** — one `*Loader` per config domain. `LuckyAddonLoader.load()` also runs here.
5. **Register content** — `DynamicItemRegistrar`, `DynamicBlockRegistrar`, `EntityRegistrar`, `CreativeMenuRegistrar`, `LuckyRegistrar`, `AddonLuckyRegistrar` — registry writes happen **only here**.
6. **World gen** — `LuckyNaturalGenRegistrar.register()` (gated by `naturalGenEnabled`).
7. **Build `RuntimeIndex`** — maps `TriggerType → itemId/blockId → List<Rule>`.
8. **Install hooks** — `HookInstaller.install()` registers Fabric events on server start.
9. **Export diagnostics** — `DiagnosticExporter.export()` if `exportReports=true`.

**Hard constraint:** item/block/entity registration is impossible after bootstrap. `/reload` must not attempt it. Reload only rebuilds `RuntimeIndex` and rules.

---

## Mixin Inventory

| Mixin | Side | Purpose |
|---|---|---|
| `ArmorFeatureRendererMixin` | Client | Renders dynamic armor textures from addon resource packs |
| `BlockDropMixin` | Server | Intercepts block drops for fortune/silk-touch interactions |
| `EntityRenderDispatcherMixin` | Client | Suppresses missing entity renderer errors when `skipMissingEntityRenderers=true` |
| `GameMenuScreenMixin` | Client | Injects "✦ Loot++" button (top-left, 96×20px) into the pause menu |
| `ItemStackSizeMixin` | Both | Applies `StackSizeRegistry` overrides to `ItemStack.getMaxCount()` |
| `LootManagerMixin` | Server | Injects `ChestLootRegistry` rules into vanilla loot table evaluation |
| `MinecraftClientMixin` | Client | Hooks resource-pack reload to re-initialize addon texture atlases |
| `PackScreenMixin` | Client | Makes addon zip packs appear in the resource pack selection screen |
| `PlayerEntityEatMixin` | Both | Applies addon food effects via `EffectLoader` rules |
| `PlayerManagerMixin` | Server | Server-side player join hook for initial state setup |
| `RecipeManagerMixin` | Both | Injects dynamic shaped/shapeless recipes from `RecipesLoader` |
| `ResourcePackProfileMixin` + Accessor + EntryMixin | Client | Injects addon packs into the ordered resource pack profile list |

---

## Lucky Drop Action Classes

17 action classes in `lucky/drop/action/`:

| Class | `type=` value | What it does |
|---|---|---|
| `LuckyItemDropAction` | `item` | Spawns an item entity with legacy ID + NBT fix |
| `LuckyEntityDropAction` | `entity` | Summons entity at computed pos; legacy entity ID fix |
| `LuckyBlockDropAction` | `block` | Places block at computed pos; applies tile-entity NBT |
| `LuckyStructureDropAction` | `structure` | Loads `.nbt`/`.luckystruct`/`.schematic` and places via `StructurePlacer` |
| `LuckyCommandDropAction` | `command` | Executes via `LegacyCommandRunner`; gated by `commandDropEnabled` |
| `LuckyEffectDropAction` | `effect` | Applies potion effect to nearby players; legacy effect ID fix |
| `LuckySoundDropAction` | `sound` | Plays sound at drop pos; legacy sound ID fix |
| `LuckyExplosionDropAction` | `explosion` | Creates explosion at computed pos |
| `LuckyFillDropAction` | `fill` | Fills a volume with a block |
| `LuckyParticleDropAction` | `particle` | Spawns particles; legacy particle ID fix |
| `LuckyProjectileDropAction` | `projectile` | Fires a `NativeLuckyProjectile` |
| `LuckyThrowDropAction` | `throw` | Spawns a thrown item entity |
| `LuckyMessageDropAction` | `message` | Sends chat message to player (gated by `dropChatEnabled`) |
| `LuckyTimeDropAction` | `time` | Sets world time |
| `LuckyChestDropAction` | `chest` | Spawns a chest filled with loot |
| `LuckyDifficultyDropAction` | `difficulty` | Changes game difficulty |
| `LuckyAttrToNbt` | *(helper)* | Converts Lucky `key=value` attrs to NBT compound |

---

## Key Design Constraints

### Config text separators
- **Field separator:** `_____` (five underscores)
- **Drop group separator:** `%%%%%` (five percent signs)
- Always use `Splitter.splitRegex(input, "_____")` — simulates Java `String.split(regex, 0)` semantics (drops trailing empty fields).

### DropGroup weight semantics
Only the **first entry's weight** in a `%%%%%`-separated group participates in the weighted roll. If the group is selected, **all entries** execute. This matches Loot++ 1.8.9 behavior exactly.

### Per-line error handling
Parse failures are per-line: WARN + skip. Only unreadable zip/file I/O is a hard error. The bootstrap never crashes on a single bad line.

### Legacy compat — always WARN, never silent
All 1.8 → 1.18.2 adaptations go through `legacy/` classes and always call `LegacyWarnReporter.warn()` or `warnOnce()`.

WARN format:
```
[WARN] [LootPP-Legacy] <Type> <detail> @ <packId>:<innerPath>:<lineNumber>
```

Common WARN types: `MetaWildcard`, `SelectorParam`, `EffectName`, `SoundId`, `EntityId`, `BlockId`, `ItemId`, `LegacyNBT`, `LegacyChestType`, `LuckyAttrBareChance`, `LegacyBlockstate`, `LegacyTexture`.

### Tick scan order (fixed for determinism)
1. `held` (main hand)
2. `wearing_armour`
3. `in_inventory`
4. `standing_on_block`
5. `inside_block`

### Item/block ID normalization
External addon names often contain dots or uppercase (e.g. `astral.fairy`). Normalize to lowercase with dots → underscores; register under `lootplusplus:<normalized>` namespace; preserve the raw name for logging.

### File encoding
Read config text UTF-8 strict first; fall back to ISO-8859-1/CP1252. Strip leading BOM (`\uFEFF`).
