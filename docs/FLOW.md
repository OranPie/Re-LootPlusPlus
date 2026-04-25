> **Language / 语言:** [English](#re-lootplusplus--execution-flow) · [中文](#中文版本chinese-version)

---

# Re-LootPlusPlus — Execution Flow

**Version:** Minecraft 1.18.2 · Fabric Loader 0.18.4 · Fabric API 0.77.0+1.18.2  
**Module:** `ie.orangep.reLootplusplus`  
**Mod ID:** `re-lootplusplus`

This document traces every significant code path from mod initialization through live game events. It is the single reference for understanding how data moves between phases and how each trigger is evaluated at runtime.

---

## §1 Bootstrap Sequence

**Source:** `bootstrap/Bootstrap.java` · Invoked from `ReLootPlusPlus#onInitialize`

The bootstrap runs exactly once at mod initialization. It is divided into nine sequential phases. No phase may be re-ordered; the constraints imposed by Minecraft's registry lifecycle enforce the order.

```
onInitialize()
└─ Bootstrap.run()
     │
     ├─ [Pre-phase] ModRecipes.register()
     │     Registers shaped/shapeless recipe serializers before any pack data loads.
     │
     ├─ Phase 1  Load config
     │     ReLootPlusPlusConfig.load()
     │       → reads .minecraft/config/relootplusplus.json
     │       → applies system property overrides (-Drelootplusplus.<field>=<value>)
     │       → applies env var overrides (RELOOTPLUSPLUS_<FIELD>=<value>)
     │     DebugFileWriter.open()  [if debugFileEnabled && level >= DETAIL]
     │     LegacyWarnReporter constructed with console/limit/summary settings
     │
     ├─ Phase 2  Pack discovery
     │     PackDiscovery.discover()  →  List<AddonPack>
     │       (see §2 for full directory scan order)
     │     AddonDisableStore.isEnabled(packId) filters disabled packs
     │
     ├─ Phase 3  Pack indexing
     │     PackIndex.indexAll(packs)
     │       → reads every config/**/*.txt in every pack (zip or dir)
     │       → builds SourceLoc (packId / innerPath / lineNumber / rawLine) per line
     │       → result is immutable after this phase; reused as-is on /reload
     │
     ├─ Phase 4  Parse all rules
     │     One loader per config domain (all use PackIndex, emit WARN on bad lines):
     │       CommandEffectLoader  → List<CommandRule>
     │       EffectLoader         → List<EffectRule>
     │       BlockDropsLoader     → List<BlockDropRule>, List<BlockDropRemoval>
     │       ChestContentLoader   → List<ChestAmountRule>, List<ChestLootRule>
     │       FishingLootLoader    → List<FishingRule>
     │       FurnaceRecipesLoader → List<SmeltingRule>, List<FuelRule>
     │       RecordsLoader        → List<RecordDef>
     │       RecipesLoader        → RecipeSet { shaped, shapeless }
     │       CreativeMenuLoader   → List<CreativeMenuEntry>
     │       StackSizeLoader      → List<StackSizeRule>
     │       ItemAdditionsLoader  → ItemAdditions (8 sub-lists)
     │       BlockAdditionsLoader → BlockAdditions (5 sub-lists)
     │       WorldGenLoader       → List<WorldGenEntry> surface + underground
     │       ThrownLoader         → List<ThrownDef>
     │       EntityDropsLoader    → List<EntityDropRule>
     │     Lucky addon loaders (separate from PackIndex):
     │       LuckyAddonLoader.load(packs)     → per-pack drops.txt/bow/sword/potion
     │       LuckyLuckCraftingLoader.load(packs) → luck_crafting.txt
     │
     │     [dryRun=true exits here after exporting diagnostics]
     │
     ├─ Phase 5  Register content  ◄── ONLY registry writes happen here
     │     EntityRegistrar.THROWN_ENTITY  (force static init)
     │     LuckyRegistrar.register()           lucky:lucky_block + lucky items
     │     AddonLuckyRegistrar.register(...)   per-addon lucky blocks/items
     │     LuckyNaturalGenRegistrar.register() [if naturalGenEnabled=true]
     │     DynamicBlockRegistrar.registerAll(blockAdditions)
     │     DynamicItemRegistrar.registerItemAdditions(itemAdditions)
     │     DynamicItemRegistrar.registerThrownItems(thrownDefs)
     │
     ├─ Phase 6  Build runtime registries
     │     ThrownRegistry       ← thrownDefs
     │     BlockDropRegistry    ← blockDropAdds + blockDropRemovals
     │     StackSizeRegistry    ← stackSizeRules
     │     EntityDropRegistry   ← entityDropAdds
     │     ChestLootRegistry    ← chestAmounts + chestLoots
     │     RuntimeState.init(config, blockDropRegistry, stackSizeRegistry, warnReporter)
     │     ChestLootHook.install()   [installs LootTableEvents.MODIFY immediately]
     │
     ├─ Phase 7  Build RuntimeIndex
     │     RuntimeIndex runtimeIndex = new RuntimeIndex()
     │     for each CommandRule: parseTrigger() → runtimeIndex.addCommandRule(trigger, rule)
     │     for each EffectRule:  parseTrigger() → runtimeIndex.addEffectRule(trigger, rule)
     │     Unknown trigger string → WARN LegacyTrigger + skip
     │
     ├─ Phase 8  Install event hooks  (deferred to SERVER_STARTED)
     │     ServerLifecycleEvents.SERVER_STARTED.register(hooks::install)
     │     On SERVER_STARTED:
     │       RuleEngine constructed (RuntimeIndex + LegacyCommandRunner)
     │       RuntimeState.setRuleEngine(ruleEngine)
     │       ServerTickHook.install()
     │       UseItemHook.install()
     │       AttackHook.install()
     │       ThrownUseHook.install()
     │       BlockBreakHook.install()
     │       EntityDeathHook.install()
     │
     └─ Phase 9  Export diagnostics
           DiagnosticExporter.export(config, warnReporter, report, packs)
             → logs/re_lootplusplus/<timestamp>/report.json
             → logs/re_lootplusplus/<timestamp>/warnings.tsv
             → logs/re_lootplusplus/<timestamp>/thrown.tsv
             → logs/re_lootplusplus/latest.txt
```

### Hard constraints

| Constraint | Reason |
|---|---|
| Registry writes in Phase 5 only | Minecraft freezes registries after `onInitialize` returns |
| Pack discovery in Phase 2 only | `/reload` must not discover new packs |
| `PackIndex` immutable after Phase 3 | Rule loaders cache line lists; rebuilding would break `SourceLoc` references |
| Hooks deferred to `SERVER_STARTED` | `ServerWorld` is not available during `onInitialize` |

---

## §2 Pack Discovery Flow

**Source:** `pack/PackDiscovery.java`

```
PackDiscovery.discover()
│
├─ discoverAll()
│    │
│    │  Scan directories in this fixed order (env/prop dirs checked first):
│    │
│    ├─ 1. $RELOOTPLUSPLUS_ADDONS  (env var, if set)
│    ├─ 2. -Drelootplusplus.addons  (system property, if set)
│    ├─ 3. <gameDir>/lootplusplus_addons/
│    ├─ 4. <gameDir>/addons/
│    ├─ 5. <gameDir>/addons/lucky/
│    ├─ 6. <gameDir>/addons/lucky_block/
│    ├─ 7. <gameDir>/packs/
│    ├─ 8. <gameDir>/mods/           [if config.scanModsDir=true]
│    └─ 9. config.extraAddonDirs[]   (each entry)
│
│    For each directory:
│      scanDir(dir):
│        • *.zip files → AddonPack(stripZip(filename), zipPath)
│        • subdirectories containing a config/ subfolder
│                      → AddonPack(dirname, dirPath)
│
│    Duplicate pack IDs:
│      duplicateStrategy="suffix"  → rename later pack to "<id>_2", "<id>_3", …
│      duplicateStrategy="skip"    → discard later pack, WARN
│      duplicateStrategy="replace" → discard earlier pack
│
└─ filterEnabled(packs)
     AddonDisableStore.isEnabled(packId) for each pack
     → false entries read from config/relootplusplus_addons.json
     → disabled packs removed from final list
```

---

## §3 Server Tick Flow

**Source:** `hooks/ServerTickHook.java`  
**Fabric event:** `ServerTickEvents.END_SERVER_TICK`

```
END_SERVER_TICK fires every server tick
│
├─ tickCount++ % config.tickIntervalTicks == 0 ?  (clamped: < 1 → 1)
│    No  → return (skip this tick)
│    Yes → continue
│
└─ for each ServerWorld in server.getWorlds():
     RuntimeContext ctx = new RuntimeContext(server, world, world.getRandom(), warnReporter)
     │
     └─ for each ServerPlayerEntity player in world.getPlayers():
          │
          │  Fixed scan order (all enabled by default; filtered by config.enabledTriggerTypes):
          │
          ├─ 1. HELD
          │     ruleEngine.executeForPlayer(ctx, player, HELD)
          │       itemId = Registry.ITEM.getId(mainHandStack.getItem())
          │       → runRules(ctx, player, HELD, itemId, mainHandStack, player.getBlockPos())
          │
          ├─ 2. WEARING_ARMOUR
          │     ruleEngine.executeArmour(ctx, player, WEARING_ARMOUR)
          │       for each of 4 armor slots (index 0–3):
          │         itemId = Registry.ITEM.getId(armorStack.getItem())
          │         → runRules(ctx, player, WEARING_ARMOUR, itemId, armorStack, player.getBlockPos())
          │
          ├─ 3. IN_INVENTORY
          │     ruleEngine.executeInventory(ctx, player, IN_INVENTORY)
          │       for each slot in player.getInventory() (0 to size-1):
          │         itemId = Registry.ITEM.getId(stack.getItem())
          │         → runRules(ctx, player, IN_INVENTORY, itemId, stack, player.getBlockPos())
          │
          ├─ 4a. BLOCKS_IN_INVENTORY (block-keyed variant of in_inventory)
          │     ruleEngine.executeBlocksInInventory(ctx, player)
          │       for each inventory slot containing a BlockItem:
          │         blockId = Registry.BLOCK.getId(blockItem.getBlock())
          │         → handleBlockTrigger(ctx, player, BLOCKS_IN_INVENTORY, player.getBlockPos(), blockId)
          │
          ├─ 4b. STANDING_ON_BLOCK
          │     ruleEngine.executeStandingOnBlock(ctx, player)
          │       blockPos = player.getBlockPos().down()
          │       blockId  = world.getBlockState(blockPos).getBlock() → Registry lookup
          │       → handleBlockTrigger(ctx, player, STANDING_ON_BLOCK, blockPos, blockId)
          │
          └─ 5. INSIDE_BLOCK
                ruleEngine.executeInsideBlock(ctx, player)
                  blockPos = player.getBlockPos()
                  blockId  = world.getBlockState(blockPos).getBlock() → Registry lookup
                  → handleBlockTrigger(ctx, player, INSIDE_BLOCK, blockPos, blockId)
```

---

## §4 RuleEngine Evaluation Flow

**Source:** `runtime/RuleEngine.java`

### §4.1 Item-keyed rules (`runRules`)

Called for all item-triggered TriggerTypes (HELD, WEARING_ARMOUR, IN_INVENTORY, RIGHT_CLICK, HITTING_*, BREAKING_BLOCK, DIGGING_BLOCK).

```
runRules(ctx, player, trigger, itemId, stack, origin)
│
├─ CommandRule loop:
│    rules = RuntimeIndex.commandRules(trigger, itemId)
│      → merge direct rules for itemId + wildcard rules for "*"
│      → direct rules first, wildcard rules appended
│    │
│    for each CommandRule rule:
│      ├─ matchesItem(rule.itemKey(), stack)?
│      │    • meta == 32767 → wildcard (always passes)
│      │    • nbtRaw non-empty → NbtPredicate.matches(stack.nbt, parsedNeedle)
│      │    • no nbt → passes
│      ├─ matchesSetBonus(player, trigger, rule.setBonusItems())?
│      │    • only relevant for WEARING_ARMOUR trigger
│      │    • all setBonusItems must be present in armor slots
│      ├─ roll(rule.probability(), ctx)?
│      │    • probability >= 1.0 → always true
│      │    • probability <= 0.0 → always false
│      │    • else → ctx.random().nextDouble() <= probability
│      └─ LegacyCommandRunner.run(rule.command(), ExecContext)
│
└─ EffectRule loop:
     rules = RuntimeIndex.effectRules(trigger, itemId)
       → same wildcard merge as above
     │
     for each EffectRule rule:
       ├─ matchesItem(rule.itemKey(), stack)?
       ├─ matchesSetBonus(player, trigger, rule.setBonusItems())?
       ├─ roll(rule.probability(), ctx)?
       └─ LegacyEffectIdMapper.resolve(rule.effectId())
            → registry lookup → StatusEffectInstance
            → player.addStatusEffect(...)
            • particleType="none"  → showParticles=false, ambient=false
            • particleType="faded" → showParticles=false, ambient=true
            • otherwise            → showParticles=true,  ambient=false
```

### §4.2 Block-keyed rules (`handleBlockTrigger`)

Called for STANDING_ON_BLOCK, INSIDE_BLOCK, BLOCKS_IN_INVENTORY, DIGGING_BLOCK_BLOCK.

```
handleBlockTrigger(ctx, player, trigger, pos, blockId)
│
├─ CommandRule loop:
│    rules = RuntimeIndex.blockCommandRules(trigger, blockId)
│      → exact match only; NO wildcard support
│    for each rule: roll() → LegacyCommandRunner.run(...)
│
└─ EffectRule loop:
     rules = RuntimeIndex.blockEffectRules(trigger, blockId)
       → exact match only; NO wildcard support
     for each rule: roll() → StatusEffectInstance → player.addStatusEffect(...)
```

---

## §5 Event Hook Flows

### §5.1 UseItemHook

**Fabric event:** `UseItemCallback.EVENT`  
**Source:** `hooks/UseItemHook.java`

```
UseItemCallback fires when player right-clicks with item in hand
│
├─ world.isClient() → return PASS
│
├─ tryUseLootChest(player, world, hand, stack)
│    Condition: stack is Blocks.CHEST.asItem() + has NBT "Type" field
│    If conditions met:
│      chestTypeMapper.resolve(chestType) → loot table Identifier
│      raycast(player, distance=5) → BlockHitResult
│      target = hitResult.blockPos.offset(hitSide)
│      world.setBlockState(target, Blocks.CHEST.getDefaultState())
│      ChestBlockEntity.setLootTable(tableId, seed)
│      stack.decrement(1)  [if not creative]
│      return SUCCESS
│
└─ [if not a loot chest]
     stack.isFood() → return PASS  (let vanilla handle food)
     RuntimeContext ctx = new RuntimeContext(...)
     ruleEngine.executeForPlayer(ctx, player, RIGHT_CLICK)
     return PASS
```

### §5.2 AttackHook

**Fabric event:** `AttackEntityCallback.EVENT`  
**Source:** `hooks/AttackHook.java`

```
AttackEntityCallback fires when player left-clicks an entity
│
├─ world.isClient() → return PASS
│
├─ ruleEngine.executeForPlayer(ctx, player, HITTING_ENTITY_TO_ENTITY, entity.getBlockPos())
│    → rules fire at the attacked entity's position
│
└─ ruleEngine.executeForPlayer(ctx, player, HITTING_ENTITY_TO_YOURSELF, player.getBlockPos())
     → rules fire at the attacking player's position
     return PASS
```

### §5.3 BlockBreakHook

**Fabric event:** `PlayerBlockBreakEvents.AFTER`  
**Source:** `hooks/BlockBreakHook.java`

```
PlayerBlockBreakEvents.AFTER fires after a block is broken by the player
│
├─ world.isClient() → return
│
├─ ruleEngine.executeForPlayer(ctx, player, BREAKING_BLOCK, pos)
│    → item-keyed: rules for held item at break position
│
├─ ruleEngine.executeForPlayer(ctx, player, DIGGING_BLOCK, pos)
│    → item-keyed: secondary trigger for held item at break position
│
├─ ruleEngine.executeBlockTrigger(ctx, player, DIGGING_BLOCK_BLOCK, pos, blockId)
│    → block-keyed: rules for the broken block's type
│
└─ applyBlockDrops(world, pos, blockState)
     blockId = Registry.BLOCK.getId(state.getBlock())
     │
     ├─ for each BlockDropRemoval matching blockId:
     │    warnReporter.warnOnce("LegacyDropRemoval", ...)  [removal is placeholder]
     │
     └─ for each BlockDropRule matching blockId:
          DropRoller.rollGroup(rule.groups(), world.getRandom())
          → selects one DropGroup from the weighted list
          for each DropEntry in group.entries():
            executeDrop(world, pos, entry, loc)
              DropEntryItem   → ItemStack → ItemEntity spawned at pos+0.5
              DropEntryEntity → EntityType.create() → readNbt() → spawnEntity()
              DropEntryCommand → LegacyCommandRunner.run(cmd, ExecContext)
```

### §5.4 EntityDeathHook

**Fabric event:** `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`  
**Source:** `hooks/EntityDeathHook.java`

```
AFTER_KILLED_OTHER_ENTITY fires when any entity is killed in combat
│
└─ applyEntityDrops(world, killed.getType(), killed.getBlockPos())
     entityId = Registry.ENTITY_TYPE.getId(type)
     for each EntityDropRule matching entityId:
       DropRoller.rollGroup(rule.groups(), world.getRandom())
       for each DropEntry in group.entries():
         executeDrop(...)  [same item/entity/command dispatch as BlockBreakHook]
```

### §5.5 ThrownUseHook

**Fabric event:** `UseItemCallback.EVENT` (registered separately from UseItemHook)  
**Source:** `hooks/ThrownUseHook.java`

```
UseItemCallback fires (shared with UseItemHook)
│
├─ world.isClient() → return PASS
├─ stack.isEmpty()  → return PASS
│
├─ itemId = Registry.ITEM.getId(stack.getItem())
│  thrownRegistry.get(itemId)  → ThrownDef or null
│  null → return PASS
│
└─ spawnThrown(world, player, stack, def)
     LootThrownItemEntity constructed with:
       EntityRegistrar.THROWN_ENTITY, world, player, def,
       LegacyCommandRunner, warnReporter
     entity.setItem(stack.copy())
     entity.setVelocity(player, pitch, yaw, 0.0f, def.velocity(), def.inaccuracy())
     world.spawnEntity(entity)
     if spawned && !creative: stack.decrement(1)
     if !spawned: WARN LegacyThrown
     return SUCCESS
```

### §5.6 ChestLootHook

**Fabric event:** `LootTableEvents.MODIFY`  
**Source:** `hooks/ChestLootHook.java`

```
LootTableEvents.MODIFY fires once per loot table ID during data loading
│
└─ for each chestType in ChestLootRegistry.allLoots():
     mapper.resolve(chestType) → Identifier (target loot table)
     Does tableId match? No → skip
     │
     Yes → buildPool(chestType, rules, tableId)
       ChestAmountRule → minRolls / maxRolls  (default 3–9)
       LootPool.Builder:
         rolls = UniformLootNumberProvider(minRolls, maxRolls)
         for each ChestLootRule:
           normalizedItemId = LegacyEntityIdFixer.normalizeItemId(rule.itemId())
           mapWoolMeta(id, meta)  [":wool" + meta 0–15 → "minecraft:<color>_wool"]
           ItemEntry.builder(item).weight(rule.weight())
             .apply(SetCountLootFunction)
             .apply(SetNbtLootFunction)   [if nbtRaw or meta > 0]
       tableBuilder.pool(pool)
```

---

## §6 Lucky Drop Evaluation Pipeline

**Sources:** `lucky/drop/LuckyDropEngine.java`, `LuckyDropRoller.java`, `LuckyDropEvaluator.java`

### §6.1 Entry points

| Caller | Method | Notes |
|---|---|---|
| `LuckyBlockBreakHook` | `LuckyDropEngine.evaluate(ctx, drops)` | Pre-parsed drops from `LuckyAddonLoader` |
| `/lppdrop eval` command | `LuckyDropEngine.evaluate(ctx, drops, dryRun=false)` | |
| `/lppdrop eval_dry` command | `LuckyDropEngine.evaluate(ctx, drops, dryRun=true)` | No actions executed |
| `/lppdrop eval_counts <N>` command | `LuckyDropEngine.simulateCounts(ctx, drops, N)` | Returns `Map<key, hitCount>` |
| Block entity custom drops | `LuckyDropEngine.evaluateRaw(ctx, rawLines)` | Re-parses each call (slower) |

### §6.2 Evaluation pipeline

```
LuckyDropEngine.evaluate(ctx, drops, dryRun)
│
├─ drops == null || empty → return null
│
├─ Stage 1: Weighted Roll
│     LuckyDropRoller.roll(drops, ctx.luck(), world.getRandom())
│     │
│     │  for each LuckyDropLine drop[i]:
│     │    effectiveWeight[i] = max(0,
│     │      1.0 + drop.luckWeight * luckModifier + ctx.luck() * luckModifier)
│     │    where luckModifier = max(0, config.luckModifier)  [default 0.1]
│     │
│     │  totalWeight = sum(effectiveWeight[i])
│     │  totalWeight <= 0 → return null
│     │
│     │  target = random.nextFloat() * totalWeight
│     │  scan cumulative weights → select entry where target <= cumulative
│     │
│     └─ returns selected LuckyDropLine (or null)
│
├─ Stage 2: Chance Gate
│     LuckyDropRoller.passesChance(selected, random)
│       chance >= 1.0 → always passes
│       chance <= 0.0 → always fails
│       else → random.nextFloat() <= chance
│     fails → return null (no drop this activation)
│
├─ Stage 3: Dispatch (skipped if dryRun=true)
│     LuckyDropEvaluator.evaluate(selected, ctx)
│     (see §6.3)
│
└─ logDropSelection(ctx, selected, drops.size(), dryRun)
     Log.detail / Log.debug / Log.trace based on config.logDetailLevel
     in-game chat message if dropChatEnabled=true && player != null
```

### §6.3 Drop dispatch (`LuckyDropEvaluator.evaluate`)

```
LuckyDropEvaluator.evaluate(drop, ctx)
│
├─ drop.isGroup() → evaluateGroup(drop, ctx)
│    │  groupCount < 0: execute ALL entries sequentially
│    │  groupCount >= 0: pick groupCount entries randomly without replacement
│    └─ for each selected entry: evaluate(entry, ctx)  [recursive]
│
└─ switch (drop.type()):
     "item"          → LuckyItemDropAction.execute(drop, ctx)
                         spawns ItemEntity at ctx.pos
     "entity"        → LuckyEntityDropAction.execute(drop, ctx)
                         LegacyEntityIdFixer maps ID → modern EntityType
                         NBT loaded, posOffset applied, spawnEntity()
     "block"         → LuckyBlockDropAction.execute(drop, ctx)
                         world.setBlockState at posOffset
     "chest"         → LuckyChestDropAction.execute(drop, ctx)
                         places chest + sets loot table
     "throw"
     "throwable"     → LuckyThrowDropAction.execute(drop, ctx)
                         spawns projectile entity
     "command"       → LuckyCommandDropAction.execute(drop, ctx)
                         LegacyCommandRunner.run(drop.command(), ExecContext)
     "structure"     → LuckyStructureDropAction.execute(drop, ctx)
                         loads NBT structure from addon zip
     "fill"          → LuckyFillDropAction.execute(drop, ctx)
                         fills region with block
     "message"       → LuckyMessageDropAction.execute(drop, ctx)
                         sends chat message [if dropChatEnabled=true]
     "effect"        → LuckyEffectDropAction.execute(drop, ctx)
                         applies StatusEffectInstance to player
     "explosion"     → LuckyExplosionDropAction.execute(drop, ctx)
     "sound"         → LuckySoundDropAction.execute(drop, ctx)
     "particle"      → LuckyParticleDropAction.execute(drop, ctx)
     "difficulty"    → LuckyDifficultyDropAction.execute(drop, ctx)
     "time"          → LuckyTimeDropAction.execute(drop, ctx)
     "nothing"       → no-op
     "luckyprojectile" → LuckyProjectileDropAction.execute(drop, ctx)
     "<type contains space>"
                     → LuckyCommandDropAction.executeRaw(type, drop, ctx)
                         treats entire type field as a server command string
     "<unknown>"     → LuckyEntityDropAction.executeAsShorthand(drop, ctx, type)
                         treats type name as entity shorthand ID
                         (e.g. "falling_block", "armorstand")
```

---

## §7 LegacyCommandRunner Flow

**Source:** `command/LegacyCommandRunner.java`

```
LegacyCommandRunner.run(commandString, ExecContext)
│
├─ Template variable expansion (at call time, not parse time)
│    LuckyTemplateVars.expand(commandString, ctx)
│      #rand(min,max), #randFloat, #randList, #randBool, #posX/Y/Z, #pName, …
│    Simple arithmetic N+M / N-M evaluated after expansion
│    Unknown #token left unchanged
│
├─ Token splitting (LegacyCommandSplitter)
│    Splits on spaces respecting quoted strings
│
├─ Verb dispatch:
│    "clear"       → runClear(targets, itemId, meta, maxCount)
│    "effect" / "lppeffect"  → runEffect(targets, effectId, duration, amplifier, hideParticles)
│    "playsound"   → runPlaySound(targets, soundId, source, volume, pitch)
│    "scoreboard"  → runScoreboard(subcommand, ...)
│    "execute"     → runExecute(selectorStr, x, y, z, subCommand)
│                     → for each matched entity: run(subCommand, ExecContext at entity pos)
│    "testfor"     → runTestFor(targets)
│    "summon"      → runSummon(entityId, x, y, z, nbt)
│    "setblock"    → runSetBlock(x, y, z, blockId, nbt)
│    "gamerule"    → runGameRule(ruleName, value)
│    "kill"        → runKill(targets)
│    "enchant"     → runEnchant(targets, enchantId, level)
│    "lppcondition" → runLppCondition(condCmd, _if_true_, thenCmd [, _if_false_, elseCmd])
│                     evaluate condCmd → successCount > 0 → run thenCmd
│                                      → successCount == 0 → run elseCmd (if present)
│    "tp"/"teleport" → runTeleport(targets, x, y, z)  or  runTeleport(targets, destEntity)
│    unknown verb  → WARN LegacyCommand unknown verb + return successCount=0
│
└─ returns ExecResult { int successCount }
```

---

## §8 `/reload` Partial Re-Bootstrap

**Source:** `bootstrap/Bootstrap.java` (reload path)

`/reload` triggers a partial re-bootstrap. The table below shows exactly which phases are re-run and which are skipped, and why.

| Phase | `/reload` behavior | Reason |
|---|---|---|
| Pre-phase: ModRecipes | **Skipped** | Serializers already registered |
| 1 — Load config | **Re-run** | Config may have changed on disk |
| 2 — Pack discovery | **Skipped** | Pack list is fixed at startup; new zips not recognized |
| 3 — Pack indexing | **Skipped** | `PackIndex` is reused as-is |
| 4 — Parse all rules | **Re-run** | Rules may reference updated config |
| 5 — Register content | **Skipped** | Registries are frozen; re-writing would crash |
| 6 — Build runtime registries | **Re-run** | Registries rebuild from freshly parsed rules |
| 7 — Build RuntimeIndex | **Re-run** | Rebuilt from new CommandRule/EffectRule lists |
| 8 — Install hooks | **Skipped** | Hooks remain installed; `RuleEngine` is recreated in-place |
| 9 — Export diagnostics | **Re-run** | If `exportReports=true` |

**Hard consequence:** Packs added or removed while the game is running are not reflected until the next full restart. Dynamic items/blocks/entities from new packs cannot be registered after startup.

---

## §9 Trigger Type Reference

Complete list of all `TriggerType` enum values and the hook/method that fires each.

| TriggerType | Fired by | Origin position |
|---|---|---|
| `HELD` | `ServerTickHook` (every interval) | Player position |
| `WEARING_ARMOUR` | `ServerTickHook` | Player position |
| `IN_INVENTORY` | `ServerTickHook` | Player position |
| `BLOCKS_IN_INVENTORY` | `ServerTickHook` (`executeBlocksInInventory`) | Player position |
| `STANDING_ON_BLOCK` | `ServerTickHook` | Block below player |
| `INSIDE_BLOCK` | `ServerTickHook` | Block at player's feet |
| `RIGHT_CLICK` | `UseItemHook` | Player position |
| `HITTING_ENTITY_TO_ENTITY` | `AttackHook` | Attacked entity's position |
| `HITTING_ENTITY_TO_YOURSELF` | `AttackHook` | Attacking player's position |
| `BREAKING_BLOCK` | `BlockBreakHook` | Broken block position |
| `DIGGING_BLOCK` | `BlockBreakHook` | Broken block position |
| `DIGGING_BLOCK_BLOCK` | `BlockBreakHook` (block-keyed) | Broken block position |
| `NEAR_ENTITY` | Reserved / not yet hooked | — |

Item-keyed triggers (`HELD`, `WEARING_ARMOUR`, `IN_INVENTORY`, `RIGHT_CLICK`, `HITTING_*`, `BREAKING_BLOCK`, `DIGGING_BLOCK`) support wildcard `"*"` item ID in `RuntimeIndex`. Block-keyed triggers (`STANDING_ON_BLOCK`, `INSIDE_BLOCK`, `BLOCKS_IN_INVENTORY`, `DIGGING_BLOCK_BLOCK`) use exact match only.

---

*End of FLOW.md (English section)*

---

## 中文版本（Chinese Version）

---

# Re-LootPlusPlus — 执行流程

**版本：** Minecraft 1.18.2 · Fabric Loader 0.18.4 · Fabric API 0.77.0+1.18.2  
**模块：** `ie.orangep.reLootplusplus`  
**Mod ID：** `re-lootplusplus`

本文档追踪从 Mod 初始化到游戏事件的每一条重要代码路径，是理解数据如何在各阶段间流动以及每个触发器在运行时如何评估的唯一参考文档。

---

## §1 启动序列

**源类：** `bootstrap/Bootstrap.java` · 由 `ReLootPlusPlus#onInitialize` 调用

启动流程在 Mod 初始化时仅运行一次，分为九个顺序阶段，不得重新排序。Minecraft 注册表生命周期的限制强制执行此顺序。

```
onInitialize()
└─ Bootstrap.run()
     │
     ├─ [预阶段] ModRecipes.register()
     │     在任何附加包数据加载之前注册有形/无形配方序列化器。
     │
     ├─ 第 1 阶段  加载配置
     │     ReLootPlusPlusConfig.load()
     │       → 读取 .minecraft/config/relootplusplus.json
     │       → 应用系统属性覆盖（-Drelootplusplus.<字段>=<值>）
     │       → 应用环境变量覆盖（RELOOTPLUSPLUS_<字段>=<值>）
     │     DebugFileWriter.open()  [如果 debugFileEnabled && 日志级别 >= DETAIL]
     │     根据控制台/限制/摘要设置构造 LegacyWarnReporter
     │
     ├─ 第 2 阶段  包发现
     │     PackDiscovery.discover()  →  List<AddonPack>
     │       （完整目录扫描顺序见 §2）
     │     AddonDisableStore.isEnabled(packId) 过滤已禁用的包
     │
     ├─ 第 3 阶段  包索引
     │     PackIndex.indexAll(packs)
     │       → 读取每个包（zip 或目录）中的每个 config/**/*.txt
     │       → 为每行构建 SourceLoc（packId / innerPath / lineNumber / rawLine）
     │       → 此阶段后不可变；/reload 时原样复用
     │
     ├─ 第 4 阶段  解析所有规则
     │     每个配置域一个加载器（均使用 PackIndex，遇到错误行时输出 WARN）：
     │       CommandEffectLoader  → List<CommandRule>
     │       EffectLoader         → List<EffectRule>
     │       BlockDropsLoader     → List<BlockDropRule>, List<BlockDropRemoval>
     │       ChestContentLoader   → List<ChestAmountRule>, List<ChestLootRule>
     │       FishingLootLoader    → List<FishingRule>
     │       FurnaceRecipesLoader → List<SmeltingRule>, List<FuelRule>
     │       RecordsLoader        → List<RecordDef>
     │       RecipesLoader        → RecipeSet { shaped, shapeless }
     │       CreativeMenuLoader   → List<CreativeMenuEntry>
     │       StackSizeLoader      → List<StackSizeRule>
     │       ItemAdditionsLoader  → ItemAdditions（8 个子列表）
     │       BlockAdditionsLoader → BlockAdditions（5 个子列表）
     │       WorldGenLoader       → List<WorldGenEntry> 地表 + 地下
     │       ThrownLoader         → List<ThrownDef>
     │       EntityDropsLoader    → List<EntityDropRule>
     │     Lucky 附加包加载器（独立于 PackIndex）：
     │       LuckyAddonLoader.load(packs)         → 每包的 drops.txt/bow/sword/potion
     │       LuckyLuckCraftingLoader.load(packs)  → luck_crafting.txt
     │
     │     [dryRun=true 在此处导出诊断后退出]
     │
     ├─ 第 5 阶段  注册内容  ◄── 注册表写入仅在此处发生
     │     EntityRegistrar.THROWN_ENTITY（强制静态初始化）
     │     LuckyRegistrar.register()             lucky:lucky_block + lucky 物品
     │     AddonLuckyRegistrar.register(...)     每附加包的 lucky 方块/物品
     │     LuckyNaturalGenRegistrar.register()  [若 naturalGenEnabled=true]
     │     DynamicBlockRegistrar.registerAll(blockAdditions)
     │     DynamicItemRegistrar.registerItemAdditions(itemAdditions)
     │     DynamicItemRegistrar.registerThrownItems(thrownDefs)
     │
     ├─ 第 6 阶段  构建运行时注册表
     │     ThrownRegistry       ← thrownDefs
     │     BlockDropRegistry    ← blockDropAdds + blockDropRemovals
     │     StackSizeRegistry    ← stackSizeRules
     │     EntityDropRegistry   ← entityDropAdds
     │     ChestLootRegistry    ← chestAmounts + chestLoots
     │     RuntimeState.init(config, blockDropRegistry, stackSizeRegistry, warnReporter)
     │     ChestLootHook.install()   [立即安装 LootTableEvents.MODIFY]
     │
     ├─ 第 7 阶段  构建 RuntimeIndex
     │     RuntimeIndex runtimeIndex = new RuntimeIndex()
     │     对每条 CommandRule：parseTrigger() → runtimeIndex.addCommandRule(trigger, rule)
     │     对每条 EffectRule：parseTrigger()  → runtimeIndex.addEffectRule(trigger, rule)
     │     未知触发器字符串 → WARN LegacyTrigger + 跳过
     │
     ├─ 第 8 阶段  安装事件钩子（延迟至 SERVER_STARTED）
     │     ServerLifecycleEvents.SERVER_STARTED.register(hooks::install)
     │     SERVER_STARTED 触发时：
     │       构造 RuleEngine（RuntimeIndex + LegacyCommandRunner）
     │       RuntimeState.setRuleEngine(ruleEngine)
     │       ServerTickHook.install()
     │       UseItemHook.install()
     │       AttackHook.install()
     │       ThrownUseHook.install()
     │       BlockBreakHook.install()
     │       EntityDeathHook.install()
     │
     └─ 第 9 阶段  导出诊断
           DiagnosticExporter.export(config, warnReporter, report, packs)
             → logs/re_lootplusplus/<时间戳>/report.json
             → logs/re_lootplusplus/<时间戳>/warnings.tsv
             → logs/re_lootplusplus/<时间戳>/thrown.tsv
             → logs/re_lootplusplus/latest.txt
```

### 硬性约束

| 约束 | 原因 |
|---|---|
| 注册表写入仅在第 5 阶段 | Minecraft 在 `onInitialize` 返回后冻结注册表 |
| 包发现仅在第 2 阶段 | `/reload` 不得发现新包 |
| `PackIndex` 在第 3 阶段后不可变 | 规则加载器缓存行列表；重建会破坏 `SourceLoc` 引用 |
| 钩子延迟至 `SERVER_STARTED` | `onInitialize` 期间 `ServerWorld` 不可用 |

---

## §2 包发现流程

**源类：** `pack/PackDiscovery.java`

```
PackDiscovery.discover()
│
├─ discoverAll()
│    │
│    │  按以下固定顺序扫描目录（环境变量/系统属性目录优先检查）：
│    │
│    ├─ 1. $RELOOTPLUSPLUS_ADDONS（环境变量，若已设置）
│    ├─ 2. -Drelootplusplus.addons（系统属性，若已设置）
│    ├─ 3. <游戏目录>/lootplusplus_addons/
│    ├─ 4. <游戏目录>/addons/
│    ├─ 5. <游戏目录>/addons/lucky/
│    ├─ 6. <游戏目录>/addons/lucky_block/
│    ├─ 7. <游戏目录>/packs/
│    ├─ 8. <游戏目录>/mods/    [若 config.scanModsDir=true]
│    └─ 9. config.extraAddonDirs[]（每个条目）
│
│    对每个目录执行 scanDir(dir)：
│      • *.zip 文件      → AddonPack(stripZip(文件名), zipPath)
│      • 包含 config/ 子文件夹的子目录
│                        → AddonPack(目录名, dirPath)
│
│    重复包 ID 处理：
│      duplicateStrategy="suffix"   → 后出现的包重命名为 "<id>_2"、"<id>_3"…
│      duplicateStrategy="skip"     → 丢弃后出现的包，输出 WARN
│      duplicateStrategy="replace"  → 丢弃先出现的包
│
└─ filterEnabled(packs)
     对每个包调用 AddonDisableStore.isEnabled(packId)
     → false 条目从 config/relootplusplus_addons.json 中读取
     → 被禁用的包从最终列表中移除
```

---

## §3 服务器 Tick 流程

**源类：** `hooks/ServerTickHook.java`  
**Fabric 事件：** `ServerTickEvents.END_SERVER_TICK`

```
每个服务器 tick 触发 END_SERVER_TICK
│
├─ tickCount++ % config.tickIntervalTicks == 0 ?（截断：< 1 → 1）
│    否 → 返回（跳过本 tick）
│    是 → 继续
│
└─ 对 server.getWorlds() 中的每个 ServerWorld：
     RuntimeContext ctx = new RuntimeContext(server, world, world.getRandom(), warnReporter)
     │
     └─ 对 world.getPlayers() 中的每个 ServerPlayerEntity：
          │
          │  固定扫描顺序（默认全部启用；由 config.enabledTriggerTypes 过滤）：
          │
          ├─ 1. HELD
          │     ruleEngine.executeForPlayer(ctx, player, HELD)
          │       itemId = Registry.ITEM.getId(主手物品栈.getItem())
          │       → runRules(ctx, player, HELD, itemId, 主手物品栈, player.getBlockPos())
          │
          ├─ 2. WEARING_ARMOUR
          │     ruleEngine.executeArmour(ctx, player, WEARING_ARMOUR)
          │       对 4 个护甲槽（索引 0–3）各执行：
          │         itemId = Registry.ITEM.getId(护甲物品栈.getItem())
          │         → runRules(ctx, player, WEARING_ARMOUR, itemId, 护甲栈, player.getBlockPos())
          │
          ├─ 3. IN_INVENTORY
          │     ruleEngine.executeInventory(ctx, player, IN_INVENTORY)
          │       对 player.getInventory() 中的每个槽（0 到 size-1）：
          │         itemId = Registry.ITEM.getId(物品栈.getItem())
          │         → runRules(ctx, player, IN_INVENTORY, itemId, 物品栈, player.getBlockPos())
          │
          ├─ 4a. BLOCKS_IN_INVENTORY（in_inventory 的方块键变体）
          │     ruleEngine.executeBlocksInInventory(ctx, player)
          │       对背包中每个含 BlockItem 的槽：
          │         blockId = Registry.BLOCK.getId(blockItem.getBlock())
          │         → handleBlockTrigger(ctx, player, BLOCKS_IN_INVENTORY, player.getBlockPos(), blockId)
          │
          ├─ 4b. STANDING_ON_BLOCK
          │     ruleEngine.executeStandingOnBlock(ctx, player)
          │       blockPos = player.getBlockPos().down()
          │       blockId  = world.getBlockState(blockPos).getBlock() → 注册表查找
          │       → handleBlockTrigger(ctx, player, STANDING_ON_BLOCK, blockPos, blockId)
          │
          └─ 5. INSIDE_BLOCK
                ruleEngine.executeInsideBlock(ctx, player)
                  blockPos = player.getBlockPos()
                  blockId  = world.getBlockState(blockPos).getBlock() → 注册表查找
                  → handleBlockTrigger(ctx, player, INSIDE_BLOCK, blockPos, blockId)
```

---

## §4 RuleEngine 评估流程

**源类：** `runtime/RuleEngine.java`

### §4.1 物品键规则（`runRules`）

适用于所有物品触发器类型（HELD、WEARING_ARMOUR、IN_INVENTORY、RIGHT_CLICK、HITTING_*、BREAKING_BLOCK、DIGGING_BLOCK）。

```
runRules(ctx, player, trigger, itemId, stack, origin)
│
├─ CommandRule 循环：
│    rules = RuntimeIndex.commandRules(trigger, itemId)
│      → 合并 itemId 的直接规则 + "*" 通配符规则
│      → 直接规则在前，通配符规则追加在后
│    │
│    对每条 CommandRule：
│      ├─ matchesItem(rule.itemKey(), stack)?
│      │    • meta == 32767 → 通配符（始终通过）
│      │    • nbtRaw 非空   → NbtPredicate.matches(stack.nbt, parsedNeedle)
│      │    • 无 nbt        → 通过
│      ├─ matchesSetBonus(player, trigger, rule.setBonusItems())?
│      │    • 仅与 WEARING_ARMOUR 触发器相关
│      │    • 所有 setBonusItems 必须存在于护甲槽中
│      ├─ roll(rule.probability(), ctx)?
│      │    • probability >= 1.0 → 始终为真
│      │    • probability <= 0.0 → 始终为假
│      │    • 否则 → ctx.random().nextDouble() <= probability
│      └─ LegacyCommandRunner.run(rule.command(), ExecContext)
│
└─ EffectRule 循环：
     rules = RuntimeIndex.effectRules(trigger, itemId)
       → 与上方相同的通配符合并
     │
     对每条 EffectRule：
       ├─ matchesItem、matchesSetBonus、roll（同上）
       └─ LegacyEffectIdMapper.resolve(rule.effectId())
            → 注册表查找 → StatusEffectInstance
            → player.addStatusEffect(...)
            • particleType="none"  → showParticles=false, ambient=false
            • particleType="faded" → showParticles=false, ambient=true
            • 其他                 → showParticles=true,  ambient=false
```

### §4.2 方块键规则（`handleBlockTrigger`）

适用于 STANDING_ON_BLOCK、INSIDE_BLOCK、BLOCKS_IN_INVENTORY、DIGGING_BLOCK_BLOCK。

```
handleBlockTrigger(ctx, player, trigger, pos, blockId)
│
├─ CommandRule 循环：
│    rules = RuntimeIndex.blockCommandRules(trigger, blockId)
│      → 仅精确匹配；不支持通配符
│    对每条规则：roll() → LegacyCommandRunner.run(...)
│
└─ EffectRule 循环：
     rules = RuntimeIndex.blockEffectRules(trigger, blockId)
       → 仅精确匹配；不支持通配符
     对每条规则：roll() → StatusEffectInstance → player.addStatusEffect(...)
```

---

## §5 事件钩子流程

### §5.1 UseItemHook

**Fabric 事件：** `UseItemCallback.EVENT`  
**源类：** `hooks/UseItemHook.java`

```
玩家右键手持物品时触发 UseItemCallback
│
├─ world.isClient() → 返回 PASS
│
├─ tryUseLootChest(player, world, hand, stack)
│    条件：stack 是 Blocks.CHEST.asItem() 且 NBT 含 "Type" 字段
│    满足条件时：
│      chestTypeMapper.resolve(chestType) → 战利品表 Identifier
│      raycast(player, 距离=5) → BlockHitResult
│      target = hitResult.blockPos.offset(hitSide)
│      world.setBlockState(target, Blocks.CHEST.getDefaultState())
│      ChestBlockEntity.setLootTable(tableId, seed)
│      stack.decrement(1)  [非创造模式]
│      返回 SUCCESS
│
└─ [若非战利品箱]
     stack.isFood() → 返回 PASS（交由原版处理食物）
     RuntimeContext ctx = new RuntimeContext(...)
     ruleEngine.executeForPlayer(ctx, player, RIGHT_CLICK)
     返回 PASS
```

### §5.2 AttackHook

**Fabric 事件：** `AttackEntityCallback.EVENT`  
**源类：** `hooks/AttackHook.java`

```
玩家左键点击实体时触发 AttackEntityCallback
│
├─ world.isClient() → 返回 PASS
│
├─ ruleEngine.executeForPlayer(ctx, player, HITTING_ENTITY_TO_ENTITY, entity.getBlockPos())
│    → 规则在被攻击实体的位置触发
│
└─ ruleEngine.executeForPlayer(ctx, player, HITTING_ENTITY_TO_YOURSELF, player.getBlockPos())
     → 规则在攻击玩家的位置触发
     返回 PASS
```

### §5.3 BlockBreakHook

**Fabric 事件：** `PlayerBlockBreakEvents.AFTER`  
**源类：** `hooks/BlockBreakHook.java`

```
玩家破坏方块后触发 PlayerBlockBreakEvents.AFTER
│
├─ world.isClient() → 返回
│
├─ ruleEngine.executeForPlayer(ctx, player, BREAKING_BLOCK, pos)
│    → 物品键：手持物品在破坏位置的规则
│
├─ ruleEngine.executeForPlayer(ctx, player, DIGGING_BLOCK, pos)
│    → 物品键：手持物品在破坏位置的次触发器
│
├─ ruleEngine.executeBlockTrigger(ctx, player, DIGGING_BLOCK_BLOCK, pos, blockId)
│    → 方块键：被破坏方块类型的规则
│
└─ applyBlockDrops(world, pos, blockState)
     blockId = Registry.BLOCK.getId(state.getBlock())
     │
     ├─ 对每条匹配 blockId 的 BlockDropRemoval：
     │    warnReporter.warnOnce("LegacyDropRemoval", ...)  [移除为占位符]
     │
     └─ 对每条匹配 blockId 的 BlockDropRule：
          DropRoller.rollGroup(rule.groups(), world.getRandom())
          → 从加权列表中选择一个 DropGroup
          对 group.entries() 中的每条 DropEntry：
            executeDrop(world, pos, entry, loc)
              DropEntryItem    → ItemStack → 在 pos+0.5 处生成 ItemEntity
              DropEntryEntity  → EntityType.create() → readNbt() → spawnEntity()
              DropEntryCommand → LegacyCommandRunner.run(cmd, ExecContext)
```

### §5.4 EntityDeathHook

**Fabric 事件：** `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`  
**源类：** `hooks/EntityDeathHook.java`

```
实体在战斗中被杀死时触发 AFTER_KILLED_OTHER_ENTITY
│
└─ applyEntityDrops(world, killed.getType(), killed.getBlockPos())
     entityId = Registry.ENTITY_TYPE.getId(type)
     对每条匹配 entityId 的 EntityDropRule：
       DropRoller.rollGroup(rule.groups(), world.getRandom())
       对 group.entries() 中的每条 DropEntry：
         executeDrop(...)  [与 BlockBreakHook 相同的物品/实体/命令分发]
```

### §5.5 ThrownUseHook

**Fabric 事件：** `UseItemCallback.EVENT`（与 UseItemHook 分别注册）  
**源类：** `hooks/ThrownUseHook.java`

```
UseItemCallback 触发（与 UseItemHook 共用）
│
├─ world.isClient() → 返回 PASS
├─ stack.isEmpty()  → 返回 PASS
│
├─ itemId = Registry.ITEM.getId(stack.getItem())
│  thrownRegistry.get(itemId)  → ThrownDef 或 null
│  null → 返回 PASS
│
└─ spawnThrown(world, player, stack, def)
     构造 LootThrownItemEntity：
       EntityRegistrar.THROWN_ENTITY, world, player, def,
       LegacyCommandRunner, warnReporter
     entity.setItem(stack.copy())
     entity.setVelocity(player, pitch, yaw, 0.0f, def.velocity(), def.inaccuracy())
     world.spawnEntity(entity)
     若已生成 && 非创造模式：stack.decrement(1)
     若未生成：WARN LegacyThrown
     返回 SUCCESS
```

### §5.6 ChestLootHook

**Fabric 事件：** `LootTableEvents.MODIFY`  
**源类：** `hooks/ChestLootHook.java`

```
数据加载期间每个战利品表 ID 触发一次 LootTableEvents.MODIFY
│
└─ 对 ChestLootRegistry.allLoots() 中的每个 chestType：
     mapper.resolve(chestType) → Identifier（目标战利品表）
     tableId 匹配吗？否 → 跳过
     │
     是 → buildPool(chestType, rules, tableId)
       ChestAmountRule → minRolls / maxRolls（默认 3–9）
       LootPool.Builder：
         rolls = UniformLootNumberProvider(minRolls, maxRolls)
         对每条 ChestLootRule：
           normalizedItemId = LegacyEntityIdFixer.normalizeItemId(rule.itemId())
           mapWoolMeta(id, meta)  [":wool" + meta 0–15 → "minecraft:<颜色>_wool"]
           ItemEntry.builder(item).weight(rule.weight())
             .apply(SetCountLootFunction)
             .apply(SetNbtLootFunction)   [若有 nbtRaw 或 meta > 0]
       tableBuilder.pool(pool)
```

---

## §6 Lucky 掉落评估管线

**源类：** `lucky/drop/LuckyDropEngine.java`、`LuckyDropRoller.java`、`LuckyDropEvaluator.java`

### §6.1 入口点

| 调用方 | 方法 | 备注 |
|---|---|---|
| `LuckyBlockBreakHook` | `LuckyDropEngine.evaluate(ctx, drops)` | 来自 `LuckyAddonLoader` 的预解析掉落 |
| `/lppdrop eval` 命令 | `LuckyDropEngine.evaluate(ctx, drops, dryRun=false)` | |
| `/lppdrop eval_dry` 命令 | `LuckyDropEngine.evaluate(ctx, drops, dryRun=true)` | 不执行动作 |
| `/lppdrop eval_counts <N>` 命令 | `LuckyDropEngine.simulateCounts(ctx, drops, N)` | 返回 `Map<键, 命中次数>` |
| 方块实体自定义掉落 | `LuckyDropEngine.evaluateRaw(ctx, rawLines)` | 每次调用重新解析（较慢）|

### §6.2 评估管线

```
LuckyDropEngine.evaluate(ctx, drops, dryRun)
│
├─ drops == null 或为空 → 返回 null
│
├─ 第 1 阶段：加权抽取
│     LuckyDropRoller.roll(drops, ctx.luck(), world.getRandom())
│     │
│     │  对每条 LuckyDropLine drop[i]：
│     │    effectiveWeight[i] = max(0,
│     │      1.0 + drop.luckWeight * luckModifier + ctx.luck() * luckModifier)
│     │    其中 luckModifier = max(0, config.luckModifier)  [默认 0.1]
│     │
│     │  totalWeight = sum(effectiveWeight[i])
│     │  totalWeight <= 0 → 返回 null
│     │
│     │  target = random.nextFloat() * totalWeight
│     │  扫描累计权重 → 选出 target <= cumulative 的条目
│     │
│     └─ 返回选中的 LuckyDropLine（或 null）
│
├─ 第 2 阶段：概率门控
│     LuckyDropRoller.passesChance(selected, random)
│       chance >= 1.0 → 始终通过
│       chance <= 0.0 → 始终失败
│       否则 → random.nextFloat() <= chance
│     失败 → 返回 null（本次激活无掉落）
│
├─ 第 3 阶段：分发执行（dryRun=true 时跳过）
│     LuckyDropEvaluator.evaluate(selected, ctx)
│     （见 §6.3）
│
└─ logDropSelection(ctx, selected, drops.size(), dryRun)
     根据 config.logDetailLevel 输出 Log.detail / Log.debug / Log.trace
     若 dropChatEnabled=true && player != null，发送游戏内聊天消息
```

### §6.3 掉落分发（`LuckyDropEvaluator.evaluate`）

```
LuckyDropEvaluator.evaluate(drop, ctx)
│
├─ drop.isGroup() → evaluateGroup(drop, ctx)
│    │  groupCount < 0：顺序执行所有条目
│    │  groupCount >= 0：不放回地随机选取 groupCount 个条目
│    └─ 对每个选中的条目：evaluate(entry, ctx)  [递归]
│
└─ switch (drop.type())：
     "item"          → LuckyItemDropAction.execute(drop, ctx)
                         在 ctx.pos 处生成 ItemEntity
     "entity"        → LuckyEntityDropAction.execute(drop, ctx)
                         LegacyEntityIdFixer 映射 ID → 现代 EntityType
                         加载 NBT，应用 posOffset，spawnEntity()
     "block"         → LuckyBlockDropAction.execute(drop, ctx)
                         在 posOffset 处 world.setBlockState
     "chest"         → LuckyChestDropAction.execute(drop, ctx)
                         放置箱子并设置战利品表
     "throw"
     "throwable"     → LuckyThrowDropAction.execute(drop, ctx)
                         生成弹射物实体
     "command"       → LuckyCommandDropAction.execute(drop, ctx)
                         LegacyCommandRunner.run(drop.command(), ExecContext)
     "structure"     → LuckyStructureDropAction.execute(drop, ctx)
                         从附加包 zip 中加载 NBT 结构
     "fill"          → LuckyFillDropAction.execute(drop, ctx)
                         用方块填充区域
     "message"       → LuckyMessageDropAction.execute(drop, ctx)
                         发送聊天消息 [若 dropChatEnabled=true]
     "effect"        → LuckyEffectDropAction.execute(drop, ctx)
                         对玩家施加 StatusEffectInstance
     "explosion"     → LuckyExplosionDropAction.execute(drop, ctx)
     "sound"         → LuckySoundDropAction.execute(drop, ctx)
     "particle"      → LuckyParticleDropAction.execute(drop, ctx)
     "difficulty"    → LuckyDifficultyDropAction.execute(drop, ctx)
     "time"          → LuckyTimeDropAction.execute(drop, ctx)
     "nothing"       → 空操作
     "luckyprojectile" → LuckyProjectileDropAction.execute(drop, ctx)
     "<type 含空格>"
                     → LuckyCommandDropAction.executeRaw(type, drop, ctx)
                         将整个 type 字段作为服务器命令字符串执行
     "<未知>"        → LuckyEntityDropAction.executeAsShorthand(drop, ctx, type)
                         将类型名作为实体简写 ID 处理
                         （如 "falling_block"、"armorstand"）
```

---

## §7 LegacyCommandRunner 流程

**源类：** `command/LegacyCommandRunner.java`

```
LegacyCommandRunner.run(commandString, ExecContext)
│
├─ 模板变量展开（在调用时执行，非解析时）
│    LuckyTemplateVars.expand(commandString, ctx)
│      #rand(min,max)、#randFloat、#randList、#randBool、#posX/Y/Z、#pName…
│    展开后对整数/浮点位置的简单算术 N+M / N-M 进行求值
│    未知的 #token 保持不变
│
├─ 令牌拆分（LegacyCommandSplitter）
│    按空格拆分，遵守引号字符串
│
├─ 动词分发：
│    "clear"       → runClear(targets, itemId, meta, maxCount)
│    "effect" / "lppeffect"  → runEffect(targets, effectId, duration, amplifier, hideParticles)
│    "playsound"   → runPlaySound(targets, soundId, source, volume, pitch)
│    "scoreboard"  → runScoreboard(subcommand, ...)
│    "execute"     → runExecute(selectorStr, x, y, z, subCommand)
│                     → 对每个匹配实体：run(subCommand, ExecContext at entity pos)
│    "testfor"     → runTestFor(targets)
│    "summon"      → runSummon(entityId, x, y, z, nbt)
│    "setblock"    → runSetBlock(x, y, z, blockId, nbt)
│    "gamerule"    → runGameRule(ruleName, value)
│    "kill"        → runKill(targets)
│    "enchant"     → runEnchant(targets, enchantId, level)
│    "lppcondition" → runLppCondition(condCmd, _if_true_, thenCmd [, _if_false_, elseCmd])
│                     评估 condCmd → successCount > 0 → 执行 thenCmd
│                                  → successCount == 0 → 执行 elseCmd（若存在）
│    "tp"/"teleport" → runTeleport(targets, x, y, z)  或  runTeleport(targets, destEntity)
│    未知动词      → WARN LegacyCommand unknown verb + 返回 successCount=0
│
└─ 返回 ExecResult { int successCount }
```

---

## §8 `/reload` 部分重启

**源类：** `bootstrap/Bootstrap.java`（reload 路径）

`/reload` 触发部分重新启动。下表列出了哪些阶段重新运行、哪些被跳过及原因。

| 阶段 | `/reload` 行为 | 原因 |
|---|---|---|
| 预阶段：ModRecipes | **跳过** | 序列化器已注册 |
| 第 1 阶段 — 加载配置 | **重新运行** | 磁盘上的配置可能已更改 |
| 第 2 阶段 — 包发现 | **跳过** | 包列表在启动时固定；新 zip 不被识别 |
| 第 3 阶段 — 包索引 | **跳过** | `PackIndex` 原样复用 |
| 第 4 阶段 — 解析规则 | **重新运行** | 规则可能引用更新后的配置 |
| 第 5 阶段 — 注册内容 | **跳过** | 注册表已冻结；重写会导致崩溃 |
| 第 6 阶段 — 构建运行时注册表 | **重新运行** | 从新解析的规则重建 |
| 第 7 阶段 — 构建 RuntimeIndex | **重新运行** | 从新的 CommandRule/EffectRule 列表重建 |
| 第 8 阶段 — 安装钩子 | **跳过** | 钩子保持安装状态；`RuleEngine` 就地重建 |
| 第 9 阶段 — 导出诊断 | **重新运行** | 若 `exportReports=true` |

**硬性后果：** 游戏运行时添加或移除的包在下次完整重启前不会生效。来自新包的动态物品/方块/实体无法在启动后注册。

---

## §9 触发器类型参考

所有 `TriggerType` 枚举值及触发每个类型的钩子/方法的完整列表。

| TriggerType | 触发方 | 原点位置 |
|---|---|---|
| `HELD` | `ServerTickHook`（每个间隔）| 玩家位置 |
| `WEARING_ARMOUR` | `ServerTickHook` | 玩家位置 |
| `IN_INVENTORY` | `ServerTickHook` | 玩家位置 |
| `BLOCKS_IN_INVENTORY` | `ServerTickHook`（`executeBlocksInInventory`）| 玩家位置 |
| `STANDING_ON_BLOCK` | `ServerTickHook` | 玩家脚下方块 |
| `INSIDE_BLOCK` | `ServerTickHook` | 玩家脚下方块 |
| `RIGHT_CLICK` | `UseItemHook` | 玩家位置 |
| `HITTING_ENTITY_TO_ENTITY` | `AttackHook` | 被攻击实体的位置 |
| `HITTING_ENTITY_TO_YOURSELF` | `AttackHook` | 攻击玩家的位置 |
| `BREAKING_BLOCK` | `BlockBreakHook` | 被破坏方块位置 |
| `DIGGING_BLOCK` | `BlockBreakHook` | 被破坏方块位置 |
| `DIGGING_BLOCK_BLOCK` | `BlockBreakHook`（方块键）| 被破坏方块位置 |
| `NEAR_ENTITY` | 保留 / 尚未挂钩 | — |

物品键触发器（`HELD`、`WEARING_ARMOUR`、`IN_INVENTORY`、`RIGHT_CLICK`、`HITTING_*`、`BREAKING_BLOCK`、`DIGGING_BLOCK`）在 `RuntimeIndex` 中支持通配符物品 ID `"*"`。方块键触发器（`STANDING_ON_BLOCK`、`INSIDE_BLOCK`、`BLOCKS_IN_INVENTORY`、`DIGGING_BLOCK_BLOCK`）仅使用精确匹配。

---

*FLOW.md 结束（中文部分）*
