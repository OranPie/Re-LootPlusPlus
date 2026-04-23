# Re-LootPlusPlus — Hook Matrix & Execution Examples

**Version:** Minecraft 1.18.2 · Fabric Loader 0.18.4 · Fabric API 0.77.0+1.18.2  
**Module:** `ie.orangep.reLootplusplus`  
**Mod ID:** `re-lootplusplus`

---

## Implementation Classes

```
hooks/HookInstaller.java       — installs all hooks
hooks/ServerTickHook.java      — held / wearing_armour / in_inventory / standing_on_block / inside_block
hooks/UseItemHook.java         — right_click trigger
hooks/AttackHook.java          — hitting_entity_to_entity trigger
hooks/ThrownUseHook.java       — thrown-item impact
hooks/BlockBreakHook.java      — block_drops trigger
hooks/EntityDeathHook.java     — entity_drops trigger
hooks/ChestLootHook.java       — chest_content injection via LootManagerMixin
runtime/RuleEngine.java        — trigger lookup and rule execution
runtime/RuntimeContext.java    — execution context
```

---

## §1 Hook Matrix

The following table is the authoritative mapping of every trigger type to its Fabric hook, source config file, and the data available in the execution context.

| Trigger | Config File | Fabric Hook | Execution Context |
|---|---|---|---|
| `held` | `item_effects/held.txt` | `ServerTickEvents.END_SERVER_TICK` | server, world, player, playerPos, random, SourceLoc |
| `in_inventory` | `item_effects/in_inventory.txt` | `ServerTickEvents.END_SERVER_TICK` | same |
| `wearing_armour` | `item_effects/wearing_armour.txt` | `ServerTickEvents.END_SERVER_TICK` | same |
| `standing_on_block` | `item_effects/standing_on_block.txt` | `ServerTickEvents.END_SERVER_TICK` | same + blockPos |
| `inside_block` | `item_effects/inside_block.txt` | `ServerTickEvents.END_SERVER_TICK` | same + blockPos |
| `right_click` | `item_effects/held.txt` (right-click variant) | `UseItemCallback` | player, hand, itemStack, world |
| `hitting_entity_to_entity` | `item_effects/hitting_entity_to_entity.txt` | `AttackEntityCallback` | attacker, target, weaponStack, world |
| `block_drops` | `block_drops/adding.txt` | `PlayerBlockBreakEvents.AFTER` + `BlockDropMixin` | player, pos, state, toolStack, world |
| `entity_drops` | `entity_drops/adding.txt` | `EntityDeathHook` | entity, attacker, world |
| `chest_content` | `chest_content/chest_loot.txt` | `LootManagerMixin` (loot table injection) | loot context, tableId |
| `world_gen surface` | `world_gen/surface.txt` | `LuckyNaturalGenFeature` | world, chunkPos, random |
| `world_gen underground` | `world_gen/underground.txt` | `LuckyNaturalGenFeature` | world, chunkPos, random |
| Lucky block break | `drops.txt` / `bow_drops.txt` | `LuckyBlockBreakHook` | world, pos, player, luck |

---

## §2 Tick Scan Execution Order

### §2.1 Fixed Scan Order

The server tick hook scans all online players on every tick (subject to `tickIntervalTicks`). For each player, trigger types are evaluated in the following fixed order:

| Step | Trigger | Source |
|---|---|---|
| 1 | `held` | Main-hand item |
| 2 | `wearing_armour` | Equipped armor (head → chest → legs → feet); set-bonus check follows |
| 3 | `in_inventory` | All inventory slots (hotbar 0–8, then main inventory 9–35, then offhand) |
| 4 | `standing_on_block` | Block directly below player feet (`playerPos.down()`) |
| 5 | `inside_block` | Block at player head position (`playerPos.up()`) |

This order is fixed and must not change between versions.

### §2.2 Determinism Rationale

The Astral Fairy addon pack uses `command_in_inventory` chains that combine a `clear` command with an `effect` command. The `clear` command's `successCount` is defined as the actual number of items removed. The `lppcondition` operator branches on `successCount > 0`.

If `in_inventory` (step 3) were evaluated after `held` (step 1), and both rules matched the same item stack, the `held` rule would observe the item before it was cleared. Reversing the order would cause the `held` rule to observe an already-cleared slot. Because both orderings produce different observable behavior, the order must be specified formally and held constant.

The canonical order above matches the behavior of Loot++ 1.8.9.

### §2.3 Tick Interval Control

The `tickIntervalTicks` config field (default: 1) controls scan frequency. When set to a value greater than 1, the tick hook skips evaluation on every tick except every N-th tick. This applies uniformly to all trigger types; per-trigger-type interval control is not supported.

### §2.4 Trigger Type Gating

The `enabledTriggerTypes` config field contains the list of trigger types that are active. Any trigger type absent from this list is skipped entirely during the tick scan. This allows server operators to disable expensive scans (e.g., `in_inventory` on servers with large player counts) without disabling the mod.

---

## §3 RuntimeContext

`RuntimeContext` carries all data required during rule evaluation. It is constructed once per trigger firing and passed to `RuleEngine.evaluate()`.

```java
public class RuntimeContext {
    public final MinecraftServer server;
    public final ServerWorld world;
    public final ServerPlayerEntity player;   // null for non-player triggers
    public final BlockPos origin;             // player feet pos, or block pos for block triggers
    public final Random random;               // world.random — do NOT use new Random()
    public final LegacyWarnReporter warnReporter;
    public final SourceLoc sourceLoc;         // source location of the firing rule
}
```

### §3.1 Random Determinism

Rule evaluation must always use `world.random` (obtained from `context.random`) or `player.getRandom()`. Creating `new Random()` inside any rule evaluation, drop evaluation, or command execution code path is prohibited. The weighted-roll behavior of Lucky Block addon packs depends on world-state-seeded randomness to approach the behavior of Loot++ 1.8.9.

### §3.2 Null Player Handling

For triggers that do not originate from a player action (e.g., world gen features), `context.player` is `null`. All code paths that access `context.player` must null-check before use. `context.origin` is always non-null.

---

## §4 WARN Output Format

All legacy adaptation warnings must follow the format below exactly. No other format is acceptable.

```
[WARN] [LootPP-Legacy] <Type> <detail> @ <packId>:<innerPath>:<lineNumber>
```

### §4.1 WARN Type Reference

| Type | Trigger condition |
|---|---|
| `MetaWildcard` | `meta=-1` or `meta=32767` used as ANY-meta wildcard |
| `SelectorParam` | Legacy selector parameters: `r=`, `rm=`, `score_*`, `c=`, `l=`, `lm=`, `m=` |
| `SelectorNegation` | `!` negation operator used in a target selector |
| `ScoreObjectiveMissing` | A scoreboard objective referenced in a selector or command does not exist at rule parse time |
| `EffectName` | Numeric potion effect ID (e.g., `1`) or legacy effect name (e.g., `instant_health` without namespace) |
| `SoundId` | Legacy sound ID (e.g., `random.levelup`, `mob.wither.death`) |
| `EntityId` | Legacy entity name (e.g., `PigZombie`, `EntityHorse`, `MushroomCow`) |
| `BlockId` | Legacy numeric block ID or unnamespaced block name |
| `ItemId` | Legacy numeric item ID or unnamespaced item name |
| `LegacyNBT` | NBT normalization or structural repair applied to a tag |
| `LegacyChestType` | 1.8 `ChestGenHooks` chest type string mapped to a 1.18.2 loot table identifier |
| `LuckyAttrBareChance` | Bare `@chance` attribute with no `=` value, normalized to `chance=1` |
| `LegacyBlockstate` | 1.8 blockstate variant key fixed (e.g., `variants.normal` → `variants[""]`) |
| `LegacyTexture` | 1.8 texture path, directory name, or filename fixed |

### §4.2 Deduplication and Console Cap

`LegacyWarnReporter.warnOnce(type, detail, sourceLoc)` deduplicates warnings by `(type, detail)` key. A warning with a given `(type, detail)` pair is emitted to the console at most once, regardless of how many source lines trigger it.

The `legacyWarnConsoleLimitPerType` config field caps console output per WARN type. When this value is `n > 0`, at most `n` distinct warnings of each type are printed to the console. The value `0` or any negative value disables the cap (unlimited console output).

All warnings — including those suppressed from the console — are always written to the export file at `<exportDir>/Latest/<timestamp>/warnings.tsv`.

When `legacyWarnConsoleSummary=true`, a single summary line is printed at the end of bootstrap for each WARN type that had suppressed entries:

```
[INFO] [LootPP-Legacy] <Type>: N warnings suppressed (see warnings.tsv)
```

---

## §5 Execution Examples

All examples are drawn from real Lucky Block addon packs (Astral and Plural). Each example shows the raw config line, the parse result, the required WARNs, and the full execution path.

### §5.1 Plural — Chaos Ball Thrown Item

**Source file:** `config/item_additions/thrown.txt`

**Raw line:**

```
plural_chaos_nuke_____§5§lPlural Chaos Ball_____false_____8.0_____1.0_____0.01_____0.0_____1.0_____c-1-effect @e[r=3] potioncore:perplexity 10 10 10%%%%%c-1-effect @e[r=3] potioncore:disorganization 10 10 10
```

**Field parse (separator: `_____`):**

| Field index | Value | Parsed as |
|---|---|---|
| 0 | `plural_chaos_nuke` | raw item name → normalized ID `lootplusplus:plural_chaos_nuke` |
| 1 | `§5§lPlural Chaos Ball` | display name (formatting codes preserved) |
| 2 | `false` | shines = false |
| 3 | `8.0` | damage = 8.0 |
| 4 | `1.0` | velocity = 1.0 |
| 5 | `0.01` | gravity = 0.01 |
| 6 | `0.0` | inaccuracy = 0.0 |
| 7 | `1.0` | dropChance = 1.0 |
| 8 | (drop groups — see below) | two `%%%%%`-separated CommandDrop groups |

**Drop group parse (separator: `%%%%%`):**

The drops field contains two entries separated by `%%%%%`:

- Group 1 (weight = 1): `c-1-effect @e[r=3] potioncore:perplexity 10 10 10`
  - `c-1-` prefix strips the weight field; command = `effect @e[r=3] potioncore:perplexity 10 10 10`
- Group 2 (weight = 1): `c-1-effect @e[r=3] potioncore:disorganization 10 10 10`
  - command = `effect @e[r=3] potioncore:disorganization 10 10 10`

Only the first entry's weight in each `%%%%%`-separated group participates in the weighted roll. Both groups are independent and each rolls independently.

**Required WARNs:**

```
[WARN] [LootPP-Legacy] SelectorParam r=3 used @ plural...zip:config/item_additions/thrown.txt:N
[WARN] [LootPP-Legacy] LegacyEffectCommand potioncore:perplexity remapped @ plural...zip:config/item_additions/thrown.txt:N
[WARN] [LootPP-Legacy] LegacyEffectCommand potioncore:disorganization remapped @ plural...zip:config/item_additions/thrown.txt:N
```

**Execution path (on impact):**

1. `LootThrownItemEntity.onImpact()` fires when the thrown item collides with a block or entity.
2. `RuleEngine` retrieves the `ThrownDef` for `lootplusplus:plural_chaos_nuke`.
3. Each `DropGroup` is evaluated independently via the weighted roll. Weight = 1 means each group is selected with certainty (total weight = 1, group weight = 1).
4. For each selected group, `LegacyCommandRunner.execute()` processes the `effect` command:
   - The legacy selector `@e[r=3]` is translated to 1.18.2 selector syntax `@e[distance=..3]` [WARN: SelectorParam].
   - `potioncore:perplexity` and `potioncore:disorganization` are remapped via `potioncoreNamespace` config field [WARN].
5. `successCount` = number of entities to which the effect was successfully applied.
6. The `successCount` value is available to any chained `lppcondition` command following this command.

---

### §5.2 Astral — Fairy Item Inventory Command

**Source file:** `config/item_effects/command_in_inventory.txt`

**Raw line:**

```
lootplusplus:astral.fairy_____-1_____{}_____1.0_____lppcondition clear @p[r=0,score_astralHealth=2,score_astralHealth_min=1,score_astralFairyCdwn=0] lootplusplus:astral.fairy 0 1 _if_true_ lppcondition effect @p[r=0,score_astralHealth=2] instant_health 5 _if_true_ lppcondition playsound random.levelup @a[r=16] ~ ~ ~ 1.0 0.5 _if_true_ scoreboard players set @p[r=0] astralFairyCdwn 10
```

**Field parse (separator: `_____`):**

| Field index | Value | Parsed as |
|---|---|---|
| 0 | `lootplusplus:astral.fairy` | item ID (already namespaced) |
| 1 | `-1` | meta = -1 → wildcard (32767) [WARN: MetaWildcard] |
| 2 | `{}` | NBT = empty object → no NBT filter applied |
| 3 | `1.0` | probability = 1.0 (fires every matching tick) |
| 4 | (full command string) | passed to `CommandChain` / `lppcondition` interpreter |

**Required WARNs:**

```
[WARN] [LootPP-Legacy] MetaWildcard -1 treated as ANY @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam r=0 used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam score_astralHealth used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam score_astralHealth_min used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam score_astralFairyCdwn used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] EffectName instant_health remapped @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SoundId random.levelup remapped @ Astral...zip:config/item_effects/command_in_inventory.txt:N
```

**Execution path (each server tick, `in_inventory` scan):**

1. The server tick hook fires (step 3 of tick scan order: `in_inventory`).
2. Player inventory contains an item matching `lootplusplus:astral.fairy` with meta wildcard; probability roll passes (1.0).
3. The `CommandRule` fires. The command string is passed to `CommandChain`.
4. **`lppcondition` level 1:**
   - Sub-command: `clear @p[r=0,score_astralHealth=2,score_astralHealth_min=1,score_astralFairyCdwn=0] lootplusplus:astral.fairy 0 1`
   - The `clear` command removes at most 1 item matching the selector constraints.
   - `successCount` = actual number of items removed (definition is fixed; any other value breaks this chain).
   - If `successCount > 0`: execute `_if_true_` branch. If `successCount == 0`: stop.
5. **`lppcondition` level 2:**
   - Sub-command: `effect @p[r=0,score_astralHealth=2] instant_health 5`
   - `successCount` = number of players to which the effect was applied.
   - If `successCount > 0`: execute `_if_true_` branch.
6. **`lppcondition` level 3:**
   - Sub-command: `playsound random.levelup @a[r=16] ~ ~ ~ 1.0 0.5`
   - `successCount` = number of players to which the sound was played.
   - If `successCount > 0`: execute `_if_true_` branch.
7. **Final command:**
   - `scoreboard players set @p[r=0] astralFairyCdwn 10`
   - `successCount` = 1 on success, 0 on failure (scoreboard set is a single-entity operation).

**`successCount` constraint:** This example is the primary motivation for the definition of `clear successCount` as "actual items removed." If `successCount` were defined as 1 (boolean success), the chain could not distinguish between "item was present and consumed" and "item was present but count was already 0." The conditional chain would fire the `effect` and `playsound` steps even when no item was consumed, breaking the cooldown mechanism.

---

### §5.3 Plural — World Gen Surface Block Placement

**Source file:** `config/world_gen/surface.txt`

**Raw line (truncated for readability):**

```
lootplusplus:command_trigger_block_____0_____{CommandList:["playsound @a mob.wither.death","gamerule commandBlockOutput false","setblock ~ ~ ~ lucky:lucky_block_plural"]}_____false_____1.0_____1_____1_____1_____0_____255_____-_____-_____-_____ground-grass-sand-rock_____-_____-_____-_____-_____-_____-
```

**Field parse (separator: `_____`):**

| Field index | Value | Parsed as |
|---|---|---|
| 0 | `lootplusplus:command_trigger_block` | block ID |
| 1 | `0` | meta = 0 |
| 2 | `{CommandList:[...]}` | NBT with `CommandList` string array |
| 3 | `false` | bonemeal = false |
| 4 | `1.0` | chancePerChunk = 1.0 |
| 5 | `1` | triesPerChunk = 1 |
| 6 | `1` | groupSize = 1 |
| 7 | `1` | triesPerGroup = 1 |
| 8 | `0` | heightMin = 0 |
| 9 | `255` | heightMax = 255 |
| 10–12 | `-` | dimension/biome/structure whitelist → empty (single `-` → empty array) |
| 13 | `ground-grass-sand-rock` | beneathMaterialWhitelist = ["ground","grass","sand","rock"] [WARN: LegacyMaterial] |
| 14–20 | `-` | remaining list fields → empty arrays |

**Required WARNs:**

```
[WARN] [LootPP-Legacy] SoundId mob.wither.death remapped @ plural...zip:config/world_gen/surface.txt:N
[WARN] [LootPP-Legacy] LegacyMaterial ground/grass/sand/rock in beneathMaterialWhitelist not supported; field skipped @ plural...zip:config/world_gen/surface.txt:N
```

**Execution path:**

1. During world generation, `LuckyNaturalGenFeature.generate()` is invoked for each registered `PlacedFeature`.
2. The feature checks dimension, biome, and height constraints (heightMin=0, heightMax=255 is effectively unconstrained).
3. The `beneathMaterialWhitelist` field (`ground`, `grass`, `sand`, `rock`) uses 1.8 material names that do not map directly to 1.18.2 block tags. This field is skipped with a WARN [WARN: LegacyMaterial].
4. `chancePerChunk=1.0` and `triesPerChunk=1` cause one placement attempt per chunk.
5. The block `lootplusplus:command_trigger_block` is placed at the valid position with the NBT tag containing `CommandList`.
6. The block entity stores the `CommandList` array. On block activation (redstone signal or player interaction), the commands execute in order:
   - `playsound @a mob.wither.death` — plays sound to all players (after remapping `mob.wither.death` → 1.18.2 sound ID)
   - `gamerule commandBlockOutput false` — disables command block output (successCount = 1)
   - `setblock ~ ~ ~ lucky:lucky_block_plural` — replaces the trigger block with a Lucky Block (successCount = 1)

---

## §6 DropGroup Weight Semantics

The `%%%%%` separator divides entries within a drop group. Weight semantics follow Loot++ 1.8.9 exactly:

```
Rule:
  DropGroup-1 (weight=W1): EntryA %%%%% EntryB %%%%% EntryC
  DropGroup-2 (weight=W2): EntryD
  DropGroup-3 (weight=W3): EntryE %%%%% EntryF
```

**Roll procedure:**

1. A weighted random selection is performed over all `DropGroup` entries using their weights `W1`, `W2`, `W3`.
2. Only the weight of the **first entry** (`EntryA`, `EntryD`, `EntryE`) in each group participates in the roll.
3. When a group is selected, **all entries** in that group execute: if `DropGroup-1` is selected, `EntryA`, `EntryB`, and `EntryC` all execute.
4. Subsequent entries' weight fields within the same `%%%%%`-group are ignored for the purposes of the roll.

This behavior is a 1:1 match of Loot++ 1.8.9 semantics and must not be changed. Changing it would alter drop distribution across all addon packs that use multi-entry drop groups.

---

## §7 successCount Definitions (Canonical)

The `successCount` returned by each command type is defined below. These definitions are fixed. Any implementation that deviates from this table will produce incorrect behavior in `lppcondition` chains.

| Command | successCount definition |
|---|---|
| `clear` | Actual number of items removed from the target's inventory |
| `effect` | Number of targets to which the effect was successfully applied |
| `playsound` | Number of targets to which the sound was successfully played |
| `scoreboard` | Number of targets successfully acted on |
| `kill` | Number of targets successfully killed |
| `testfor` | Number of targets matched by the selector |
| `execute` | Sum of sub-command `successCount` values across all targets |
| `summon` | 1 on successful entity creation, 0 on failure |
| `setblock` | 1 on successful block placement, 0 on failure |
| `gamerule` | 1 on success, 0 on failure |
| `lppcondition` | `successCount` of the executed branch (if-true or if-false) |

**`lppcondition` evaluation rule:**  
`lppcondition <condition-cmd> _if_true_ <then-cmd>` evaluates `<condition-cmd>`. If `successCount > 0`, it evaluates `<then-cmd>` and returns its `successCount`. If `successCount == 0`, it stops and returns 0. The optional `_if_false_ <else-cmd>` branch executes when `successCount == 0` and returns the else-cmd's `successCount`.

---

*End of REFERENCE.md (English section)*

---

# Re-LootPlusPlus — 钩子矩阵与执行示例

**版本：** Minecraft 1.18.2 · Fabric Loader 0.18.4 · Fabric API 0.77.0+1.18.2  
**模块：** `ie.orangep.reLootplusplus`  
**Mod ID：** `re-lootplusplus`

---

## 实现类索引

```
hooks/HookInstaller.java       — 安装所有钩子
hooks/ServerTickHook.java      — held / wearing_armour / in_inventory / standing_on_block / inside_block
hooks/UseItemHook.java         — right_click 触发器
hooks/AttackHook.java          — hitting_entity_to_entity 触发器
hooks/ThrownUseHook.java       — 投掷物撞击
hooks/BlockBreakHook.java      — block_drops 触发器
hooks/EntityDeathHook.java     — entity_drops 触发器
hooks/ChestLootHook.java       — 通过 LootManagerMixin 注入 chest_content
runtime/RuleEngine.java        — 触发器查找与规则执行
runtime/RuntimeContext.java    — 执行上下文
```

---

## §1 钩子矩阵

以下表格是每种触发器类型到其 Fabric 钩子、来源配置文件及执行上下文可用数据的权威映射。

| 触发器 | 配置文件 | Fabric 钩子 | 执行上下文 |
|---|---|---|---|
| `held` | `item_effects/held.txt` | `ServerTickEvents.END_SERVER_TICK` | server, world, player, playerPos, random, SourceLoc |
| `in_inventory` | `item_effects/in_inventory.txt` | `ServerTickEvents.END_SERVER_TICK` | 同上 |
| `wearing_armour` | `item_effects/wearing_armour.txt` | `ServerTickEvents.END_SERVER_TICK` | 同上 |
| `standing_on_block` | `item_effects/standing_on_block.txt` | `ServerTickEvents.END_SERVER_TICK` | 同上 + blockPos |
| `inside_block` | `item_effects/inside_block.txt` | `ServerTickEvents.END_SERVER_TICK` | 同上 + blockPos |
| `right_click` | `item_effects/held.txt`（右键变体） | `UseItemCallback` | player, hand, itemStack, world |
| `hitting_entity_to_entity` | `item_effects/hitting_entity_to_entity.txt` | `AttackEntityCallback` | attacker, target, weaponStack, world |
| `block_drops` | `block_drops/adding.txt` | `PlayerBlockBreakEvents.AFTER` + `BlockDropMixin` | player, pos, state, toolStack, world |
| `entity_drops` | `entity_drops/adding.txt` | `EntityDeathHook` | entity, attacker, world |
| `chest_content` | `chest_content/chest_loot.txt` | `LootManagerMixin`（战利品表注入） | loot context, tableId |
| `world_gen surface` | `world_gen/surface.txt` | `LuckyNaturalGenFeature` | world, chunkPos, random |
| `world_gen underground` | `world_gen/underground.txt` | `LuckyNaturalGenFeature` | world, chunkPos, random |
| Lucky 方块破坏 | `drops.txt` / `bow_drops.txt` | `LuckyBlockBreakHook` | world, pos, player, luck |

---

## §2 Tick 扫描执行顺序

### §2.1 固定扫描顺序

服务端 Tick 钩子在每个 tick 对所有在线玩家进行扫描（受 `tickIntervalTicks` 约束）。对于每位玩家，触发器类型按以下固定顺序评估：

| 步骤 | 触发器 | 来源 |
|---|---|---|
| 1 | `held` | 主手物品 |
| 2 | `wearing_armour` | 已装备护甲（头盔 → 胸甲 → 护腿 → 靴子）；随后执行套装加成检查 |
| 3 | `in_inventory` | 所有背包格位（快捷栏 0–8，主背包 9–35，副手） |
| 4 | `standing_on_block` | 玩家脚部正下方的方块（`playerPos.down()`） |
| 5 | `inside_block` | 玩家头部位置的方块（`playerPos.up()`） |

此顺序固定不变，不得在版本间更改。

### §2.2 确定性原理

Astral Fairy 插件包使用 `command_in_inventory` 链，将 `clear` 命令与 `effect` 命令组合使用。`clear` 命令的 `successCount` 定义为实际移除的物品数量。`lppcondition` 运算符基于 `successCount > 0` 进行分支。

若 `in_inventory`（第 3 步）在 `held`（第 1 步）之后评估，且两条规则均匹配同一物品堆叠，则 `held` 规则将在物品被清除之前观察到该物品。颠倒顺序将导致 `held` 规则观察到已被清除的格位。由于两种排序产生不同的可观察行为，必须对顺序进行正式规定并保持恒定。

上述规范顺序与 Loot++ 1.8.9 的行为一致。

### §2.3 Tick 间隔控制

`tickIntervalTicks` 配置字段（默认值：1）控制扫描频率。当设置为大于 1 的值时，Tick 钩子仅在每 N 个 tick 时执行一次评估。此控制对所有触发器类型统一生效；不支持按触发器类型设置不同间隔。

### §2.4 触发器类型门控

`enabledTriggerTypes` 配置字段包含活跃的触发器类型列表。不在此列表中的触发器类型在 Tick 扫描中将被完全跳过。这允许服务器管理员在不禁用整个 mod 的情况下，禁用代价较高的扫描操作（例如，在玩家数量较多的服务器上禁用 `in_inventory`）。

---

## §3 RuntimeContext（运行时上下文）

`RuntimeContext` 承载规则评估期间所需的所有数据。它在每次触发器触发时构造一次，并传递给 `RuleEngine.evaluate()`。

```java
public class RuntimeContext {
    public final MinecraftServer server;
    public final ServerWorld world;
    public final ServerPlayerEntity player;   // 对于非玩家触发器为 null
    public final BlockPos origin;             // 玩家脚部位置，或方块触发器的方块位置
    public final Random random;               // world.random — 禁止使用 new Random()
    public final LegacyWarnReporter warnReporter;
    public final SourceLoc sourceLoc;         // 触发规则的来源位置
}
```

### §3.1 随机性确定性

规则评估必须始终使用 `world.random`（通过 `context.random` 获取）或 `player.getRandom()`。在任何规则评估、掉落评估或命令执行代码路径中创建 `new Random()` 是被禁止的。Lucky Block 插件包的加权抽取行为依赖于基于世界状态种子的随机性，以接近 Loot++ 1.8.9 的行为。

### §3.2 空玩家处理

对于不源自玩家操作的触发器（例如世界生成特性），`context.player` 为 `null`。所有访问 `context.player` 的代码路径在使用前必须进行空值检查。`context.origin` 始终非空。

---

## §4 WARN 输出格式

所有遗留适配警告必须严格遵循以下格式，不接受其他格式。

```
[WARN] [LootPP-Legacy] <类型> <详情> @ <packId>:<innerPath>:<lineNumber>
```

### §4.1 WARN 类型参考

| 类型 | 触发条件 |
|---|---|
| `MetaWildcard` | 使用 `meta=-1` 或 `meta=32767` 作为任意元数据通配符 |
| `SelectorParam` | 遗留选择器参数：`r=`、`rm=`、`score_*`、`c=`、`l=`、`lm=`、`m=` |
| `SelectorNegation` | 目标选择器中使用了 `!` 否定运算符 |
| `ScoreObjectiveMissing` | 选择器或命令中引用的记分板目标在规则解析时不存在 |
| `EffectName` | 数字型药水效果 ID（如 `1`）或无命名空间的遗留效果名（如 `instant_health`） |
| `SoundId` | 遗留音效 ID（如 `random.levelup`、`mob.wither.death`） |
| `EntityId` | 遗留实体名称（如 `PigZombie`、`EntityHorse`、`MushroomCow`） |
| `BlockId` | 遗留数字型方块 ID 或无命名空间的方块名称 |
| `ItemId` | 遗留数字型物品 ID 或无命名空间的物品名称 |
| `LegacyNBT` | 对 NBT 标签应用了规范化或结构修复 |
| `LegacyChestType` | 1.8 `ChestGenHooks` 箱子类型字符串映射到 1.18.2 战利品表标识符 |
| `LuckyAttrBareChance` | 裸 `@chance` 属性（无 `=` 值），规范化为 `chance=1` |
| `LegacyBlockstate` | 1.8 方块状态变体键已修复（如 `variants.normal` → `variants[""]`） |
| `LegacyTexture` | 1.8 纹理路径、目录名或文件名已修复 |

### §4.2 去重与控制台输出上限

`LegacyWarnReporter.warnOnce(type, detail, sourceLoc)` 按 `(type, detail)` 键对警告进行去重。具有相同 `(type, detail)` 组合的警告无论有多少来源行触发，均最多向控制台输出一次。

`legacyWarnConsoleLimitPerType` 配置字段限制每个 WARN 类型的控制台输出数量。当此值为 `n > 0` 时，每种类型最多向控制台打印 `n` 条不同的警告。值为 `0` 或负数时禁用上限（无限制控制台输出）。

所有警告——包括被控制台上限抑制的警告——始终写入位于 `<exportDir>/Latest/<timestamp>/warnings.tsv` 的导出文件。

当 `legacyWarnConsoleSummary=true` 时，在引导结束时，对每种存在被抑制条目的 WARN 类型打印一条摘要行：

```
[INFO] [LootPP-Legacy] <类型>: N 条警告已被抑制（详见 warnings.tsv）
```

---

## §5 执行示例

所有示例均来自真实的 Lucky Block 插件包（Astral 和 Plural）。每个示例展示原始配置行、解析结果、必需的 WARN 以及完整的执行路径。

### §5.1 Plural — 混沌球投掷物

**来源文件：** `config/item_additions/thrown.txt`

**原始行：**

```
plural_chaos_nuke_____§5§lPlural Chaos Ball_____false_____8.0_____1.0_____0.01_____0.0_____1.0_____c-1-effect @e[r=3] potioncore:perplexity 10 10 10%%%%%c-1-effect @e[r=3] potioncore:disorganization 10 10 10
```

**字段解析（分隔符：`_____`）：**

| 字段索引 | 值 | 解析为 |
|---|---|---|
| 0 | `plural_chaos_nuke` | 原始物品名 → 规范化 ID `lootplusplus:plural_chaos_nuke` |
| 1 | `§5§lPlural Chaos Ball` | 显示名称（格式代码保留） |
| 2 | `false` | shines = false |
| 3 | `8.0` | damage = 8.0 |
| 4 | `1.0` | velocity = 1.0 |
| 5 | `0.01` | gravity = 0.01 |
| 6 | `0.0` | inaccuracy = 0.0 |
| 7 | `1.0` | dropChance = 1.0 |
| 8 | （掉落组，见下） | 两个 `%%%%%` 分隔的 CommandDrop 组 |

**掉落组解析（分隔符：`%%%%%`）：**

掉落字段包含两个由 `%%%%%` 分隔的条目：

- 组 1（权重 = 1）：`c-1-effect @e[r=3] potioncore:perplexity 10 10 10`
  - `c-1-` 前缀剥离权重字段；命令 = `effect @e[r=3] potioncore:perplexity 10 10 10`
- 组 2（权重 = 1）：`c-1-effect @e[r=3] potioncore:disorganization 10 10 10`
  - 命令 = `effect @e[r=3] potioncore:disorganization 10 10 10`

每个 `%%%%%` 分隔组中仅**第一个条目**的权重参与加权抽取。两个组相互独立，各自独立抽取。

**必需 WARN：**

```
[WARN] [LootPP-Legacy] SelectorParam r=3 used @ plural...zip:config/item_additions/thrown.txt:N
[WARN] [LootPP-Legacy] LegacyEffectCommand potioncore:perplexity remapped @ plural...zip:config/item_additions/thrown.txt:N
[WARN] [LootPP-Legacy] LegacyEffectCommand potioncore:disorganization remapped @ plural...zip:config/item_additions/thrown.txt:N
```

**执行路径（投掷物撞击时）：**

1. `LootThrownItemEntity.onImpact()` 在投掷物与方块或实体碰撞时触发。
2. `RuleEngine` 检索 `lootplusplus:plural_chaos_nuke` 的 `ThrownDef`。
3. 每个 `DropGroup` 通过加权抽取独立评估。权重 = 1 意味着每个组确定性被选中（总权重 = 1，组权重 = 1）。
4. 对于每个被选中的组，`LegacyCommandRunner.execute()` 处理 `effect` 命令：
   - 遗留选择器 `@e[r=3]` 转换为 1.18.2 选择器语法 `@e[distance=..3]` [WARN: SelectorParam]。
   - `potioncore:perplexity` 和 `potioncore:disorganization` 通过 `potioncoreNamespace` 配置字段重映射 [WARN]。
5. `successCount` = 成功应用效果的实体数量。
6. 该 `successCount` 值可供此命令之后链接的任何 `lppcondition` 命令使用。

---

### §5.2 Astral — 仙子物品背包命令

**来源文件：** `config/item_effects/command_in_inventory.txt`

**原始行：**

```
lootplusplus:astral.fairy_____-1_____{}_____1.0_____lppcondition clear @p[r=0,score_astralHealth=2,score_astralHealth_min=1,score_astralFairyCdwn=0] lootplusplus:astral.fairy 0 1 _if_true_ lppcondition effect @p[r=0,score_astralHealth=2] instant_health 5 _if_true_ lppcondition playsound random.levelup @a[r=16] ~ ~ ~ 1.0 0.5 _if_true_ scoreboard players set @p[r=0] astralFairyCdwn 10
```

**字段解析（分隔符：`_____`）：**

| 字段索引 | 值 | 解析为 |
|---|---|---|
| 0 | `lootplusplus:astral.fairy` | 物品 ID（已含命名空间） |
| 1 | `-1` | meta = -1 → 通配符（32767）[WARN: MetaWildcard] |
| 2 | `{}` | NBT = 空对象 → 不应用 NBT 过滤 |
| 3 | `1.0` | probability = 1.0（每个匹配的 tick 均触发） |
| 4 | （完整命令字符串） | 传递给 `CommandChain` / `lppcondition` 解释器 |

**必需 WARN：**

```
[WARN] [LootPP-Legacy] MetaWildcard -1 treated as ANY @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam r=0 used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam score_astralHealth used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam score_astralHealth_min used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SelectorParam score_astralFairyCdwn used @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] EffectName instant_health remapped @ Astral...zip:config/item_effects/command_in_inventory.txt:N
[WARN] [LootPP-Legacy] SoundId random.levelup remapped @ Astral...zip:config/item_effects/command_in_inventory.txt:N
```

**执行路径（每个服务端 tick，`in_inventory` 扫描）：**

1. 服务端 Tick 钩子触发（Tick 扫描顺序第 3 步：`in_inventory`）。
2. 玩家背包中含有匹配 `lootplusplus:astral.fairy`（元数据通配符）的物品；概率抽取通过（1.0）。
3. `CommandRule` 触发。命令字符串传递给 `CommandChain`。
4. **`lppcondition` 第 1 层：**
   - 子命令：`clear @p[r=0,score_astralHealth=2,score_astralHealth_min=1,score_astralFairyCdwn=0] lootplusplus:astral.fairy 0 1`
   - `clear` 命令根据选择器约束最多移除 1 个匹配物品。
   - `successCount` = 实际移除的物品数量（定义固定；任何其他值将破坏此条件链）。
   - 若 `successCount > 0`：执行 `_if_true_` 分支。若 `successCount == 0`：停止。
5. **`lppcondition` 第 2 层：**
   - 子命令：`effect @p[r=0,score_astralHealth=2] instant_health 5`
   - `successCount` = 成功应用效果的玩家数量。
   - 若 `successCount > 0`：执行 `_if_true_` 分支。
6. **`lppcondition` 第 3 层：**
   - 子命令：`playsound random.levelup @a[r=16] ~ ~ ~ 1.0 0.5`
   - `successCount` = 成功播放音效的玩家数量。
   - 若 `successCount > 0`：执行 `_if_true_` 分支。
7. **最终命令：**
   - `scoreboard players set @p[r=0] astralFairyCdwn 10`
   - `successCount` = 成功时为 1，失败时为 0（记分板设置为单目标操作）。

**`successCount` 约束说明：** 此示例是将 `clear successCount` 定义为"实际移除的物品数量"的主要依据。若将 `successCount` 定义为 1（布尔成功），则条件链将无法区分"物品存在且已消耗"与"物品存在但数量已为 0"两种情况。条件链将在未消耗任何物品时仍触发 `effect` 和 `playsound` 步骤，从而破坏冷却机制。

---

### §5.3 Plural — 世界生成地表方块放置

**来源文件：** `config/world_gen/surface.txt`

**原始行（为可读性截断）：**

```
lootplusplus:command_trigger_block_____0_____{CommandList:["playsound @a mob.wither.death","gamerule commandBlockOutput false","setblock ~ ~ ~ lucky:lucky_block_plural"]}_____false_____1.0_____1_____1_____1_____0_____255_____-_____-_____-_____ground-grass-sand-rock_____-_____-_____-_____-_____-_____-
```

**字段解析（分隔符：`_____`）：**

| 字段索引 | 值 | 解析为 |
|---|---|---|
| 0 | `lootplusplus:command_trigger_block` | 方块 ID |
| 1 | `0` | meta = 0 |
| 2 | `{CommandList:[...]}` | 含 `CommandList` 字符串数组的 NBT |
| 3 | `false` | bonemeal = false |
| 4 | `1.0` | chancePerChunk = 1.0 |
| 5 | `1` | triesPerChunk = 1 |
| 6 | `1` | groupSize = 1 |
| 7 | `1` | triesPerGroup = 1 |
| 8 | `0` | heightMin = 0 |
| 9 | `255` | heightMax = 255 |
| 10–12 | `-` | 维度/生物群系/结构白名单 → 空（单个 `-` → Java split 产生空数组） |
| 13 | `ground-grass-sand-rock` | beneathMaterialWhitelist = ["ground","grass","sand","rock"] [WARN: LegacyMaterial] |
| 14–20 | `-` | 其余列表字段 → 空数组 |

**必需 WARN：**

```
[WARN] [LootPP-Legacy] SoundId mob.wither.death remapped @ plural...zip:config/world_gen/surface.txt:N
[WARN] [LootPP-Legacy] LegacyMaterial ground/grass/sand/rock in beneathMaterialWhitelist not supported; field skipped @ plural...zip:config/world_gen/surface.txt:N
```

**执行路径：**

1. 在世界生成期间，每个已注册的 `PlacedFeature` 调用 `LuckyNaturalGenFeature.generate()`。
2. 特性检查维度、生物群系和高度约束（heightMin=0，heightMax=255 实际上无约束）。
3. `beneathMaterialWhitelist` 字段（`ground`、`grass`、`sand`、`rock`）使用 1.8 材质名称，无法直接映射到 1.18.2 方块标签。此字段被跳过并发出 WARN [WARN: LegacyMaterial]。
4. `chancePerChunk=1.0` 和 `triesPerChunk=1` 使每个区块执行一次放置尝试。
5. 方块 `lootplusplus:command_trigger_block` 以含 `CommandList` 的 NBT 标签放置于有效位置。
6. 方块实体存储 `CommandList` 数组。在方块激活（红石信号或玩家交互）时，命令按顺序执行：
   - `playsound @a mob.wither.death` — 向所有玩家播放音效（将 `mob.wither.death` 重映射为 1.18.2 音效 ID 后执行）
   - `gamerule commandBlockOutput false` — 禁用命令方块输出（successCount = 1）
   - `setblock ~ ~ ~ lucky:lucky_block_plural` — 将触发方块替换为 Lucky 方块（successCount = 1）

---

## §6 掉落组权重语义

`%%%%%` 分隔符划分掉落组内的条目。权重语义严格遵循 Loot++ 1.8.9：

```
规则：
  DropGroup-1（权重=W1）：条目A %%%%% 条目B %%%%% 条目C
  DropGroup-2（权重=W2）：条目D
  DropGroup-3（权重=W3）：条目E %%%%% 条目F
```

**抽取流程：**

1. 使用权重 `W1`、`W2`、`W3` 对所有 `DropGroup` 条目进行加权随机选择。
2. 每个组中**仅第一个条目**（`条目A`、`条目D`、`条目E`）的权重参与抽取。
3. 当某个组被选中时，该组中的**所有条目**均执行：若 `DropGroup-1` 被选中，则 `条目A`、`条目B` 和 `条目C` 全部执行。
4. 同一 `%%%%%` 组内后续条目的权重字段不参与抽取计算，被忽略。

此行为与 Loot++ 1.8.9 语义完全一致，不得更改。更改此行为将影响所有使用多条目掉落组的插件包的掉落分布。

---

## §7 successCount 定义（权威表）

以下是每种命令类型返回的 `successCount` 的固定定义。这些定义不可更改。任何偏离此表的实现将在 `lppcondition` 条件链中产生错误行为。

| 命令 | successCount 定义 |
|---|---|
| `clear` | 从目标背包中实际移除的物品数量 |
| `effect` | 成功应用效果的目标数量 |
| `playsound` | 成功播放音效的目标数量 |
| `scoreboard` | 成功执行操作的目标数量 |
| `kill` | 成功击杀的目标数量 |
| `testfor` | 选择器匹配的目标数量 |
| `execute` | 所有目标上子命令 `successCount` 值的总和 |
| `summon` | 实体创建成功时为 1，失败时为 0 |
| `setblock` | 方块放置成功时为 1，失败时为 0 |
| `gamerule` | 成功时为 1，失败时为 0 |
| `lppcondition` | 所执行分支（if-true 或 if-false）的 `successCount` |

**`lppcondition` 评估规则：**  
`lppcondition <条件命令> _if_true_ <then 命令>` 评估 `<条件命令>`。若 `successCount > 0`，则评估 `<then 命令>` 并返回其 `successCount`。若 `successCount == 0`，则停止并返回 0。可选的 `_if_false_ <else 命令>` 分支在 `successCount == 0` 时执行，并返回 else 命令的 `successCount`。

---

*REFERENCE.md 结束（中文部分）*
