# Re-LootPlusPlus — Fabric Injection Strategy

**Version:** Minecraft 1.18.2 · Fabric Loader 0.18.4 · Fabric API 0.77.0+1.18.2  
**Module:** `ie.orangep.reLootplusplus`  
**Mod ID:** `re-lootplusplus`

---

## Implementation Classes

```
bootstrap/Bootstrap.java                      — nine-phase orchestrator; all registration in phase 5 only
hooks/HookInstaller.java                      — installs all Fabric events (no registration)
mixin/                                        — 12 thin mixins; prefer Fabric API events over mixins
resourcepack/ExternalPackProvider.java        — mounts addon zips as resource packs on client
resourcepack/LegacyPatchingResourcePack.java  — fixes 1.8 resource formats before pack consumption
```

---

## §1 Lifecycle Boundaries (Hard Constraints)

### §1.1 Server/Common Initialization — `ModInitializer#onInitialize`

All of the following phases must complete during `onInitialize`. No phase may be deferred to a later point in the Minecraft lifecycle.

| Phase | Responsibility |
|---|---|
| 1. Pack discovery | Scan addon directories and zip files; build `List<AddonPack>` |
| 2. Pack indexing | Build `PackIndex` with per-line `SourceLoc` metadata |
| 3. Config parse | Run each `*Loader` to produce rules and definitions |
| 4. Content registration | Register items, blocks, entities, block entities, item groups (all registry writes) |
| 5. RuntimeIndex build | Map `TriggerType → itemId/blockId → List<Rule>` |
| 6. Hook installation | Register Fabric events via `HookInstaller` |
| 7. Reload listener | Register a data reload listener (rules/index only — no new registry entries) |

**Hard constraint:** The Fabric Registry is frozen after `onInitialize` returns. Items, blocks, entities, and item groups cannot be registered after this point. Any attempt to write to a frozen registry produces undefined behavior. The `/reload` command must never attempt new registry entries.

`Bootstrap.java` is the sole orchestrator. It executes all seven phases in the order above and emits a phase-level diagnostic log entry at the start and end of each phase. No other class initiates registration.

### §1.2 Client Initialization — `ClientModInitializer#onInitializeClient`

The following operations are performed during client initialization:

1. Mount addon zip `assets/**` trees as `ResourcePackProfile` entries via `ExternalPackProvider` (implements `ResourcePackProvider`).
2. Register entity renderers for dynamically-registered entity types.
3. Register block entity renderers for dynamically-registered block entity types.
4. Register block color providers and item color providers (optional, only if the addon supplies color data).

Client-side registration of renderers and color providers is permissible after `onInitialize` because those registries are client-only and are not frozen at the same time as common registries.

---

## §2 Registry Standards

### §2.1 ID Normalization (Mandatory)

All registry identifiers must satisfy the following rules:

- Constructed as `new Identifier(modid, path)`.
- The `path` segment must consist exclusively of lowercase ASCII letters, decimal digits, and underscores (`[a-z0-9_]`).
- The `namespace` segment must be `re-lootplusplus` for mod-owned identifiers, or `lootplusplus` for dynamically registered addon content.

External addon names may contain dots, uppercase letters, or other characters not legal in a Fabric `Identifier` (e.g., `astral.fairy`, `LuckySword`). The normalization procedure is:

```
normalized = rawName
    .toLowerCase()
    .replace('.', '_')
    .replaceAll("[^a-z0-9_]", "_")
```

The raw name is preserved in the `AddonPack` metadata and emitted in all WARN and diagnostic output. Dynamic addon items and blocks are registered under the `lootplusplus` namespace:

```
lootplusplus:<normalized>
```

### §2.2 Duplicate ID Strategy

When the same normalized ID is produced by two distinct addon entries, behavior is controlled by the `duplicateStrategy` config field:

| Value | Behavior |
|---|---|
| `suffix` | Append `_2`, `_3`, etc., to the duplicate ID (default). Emits WARN. |
| `ignore` | Skip the duplicate entry entirely. Emits WARN. |

In both cases a WARN is emitted with `SourceLoc` of both the original and the duplicate entry.

### §2.3 Creative Tab

All dynamically registered addon items are placed in a dedicated `lootplusplus` creative tab. The creative tab itself is a registry entry (`ItemGroup`) and must be registered during phase 4 (content registration), before any items that reference it are registered. The tab registration is managed by `CreativeMenuRegistrar`.

---

## §3 Event Hook Standards

All event hooks are installed during phase 6 by `HookInstaller`. No hook is installed before phases 1–5 complete, and no hook is installed during `/reload`.

### §3.1 Tick Scan (held / wearing_armour / in_inventory / standing_on_block / inside_block)

| Attribute | Value |
|---|---|
| Fabric hook | `ServerTickEvents.END_SERVER_TICK` |
| Execution side | Logical server only |
| Interval | Controlled by `tickIntervalTicks` (default: 1, every tick) |
| Trigger gating | Controlled by `enabledTriggerTypes` (omitted types are skipped entirely) |

The scan iterates over all online players and, for each player, evaluates trigger types in the following fixed order:

1. `held` — main-hand item
2. `wearing_armour` — equipped armor pieces (head → chest → legs → feet); set-bonus check follows
3. `in_inventory` — all inventory slots
4. `standing_on_block` — block directly below player feet
5. `inside_block` — block at player head position

This order is fixed and must not change between versions. See §3.1 of REFERENCE.md for the determinism rationale.

### §3.2 Right-Click Item (`right_click`)

| Attribute | Value |
|---|---|
| Fabric hook | `UseItemCallback` |
| Execution side | Logical server |
| Rule source | `item_effects/held.txt` right-click variant |

### §3.3 Attack Entity (`hitting_entity_to_entity`)

| Attribute | Value |
|---|---|
| Fabric hook | `AttackEntityCallback` |
| Execution side | Logical server |
| Rule source | `item_effects/hitting_entity_to_entity.txt` |

### §3.4 Block Break (`block_drops` / `digging_block`)

| Attribute | Value |
|---|---|
| Fabric hook | `PlayerBlockBreakEvents.AFTER` + `BlockDropMixin` |
| Execution side | Logical server |
| Rule source | `block_drops/adding.txt` |

`BlockDropMixin` is required for fine-grained fortune/silk-touch drop interception that the Fabric API event does not expose at sufficient granularity.

### §3.5 Entity Death (`entity_drops`)

| Attribute | Value |
|---|---|
| Hook | `EntityDeathHook` (via mixin or equivalent) |
| Execution side | Logical server |
| Rule source | `entity_drops/adding.txt` |

### §3.6 Chest Loot (`chest_content`)

| Attribute | Value |
|---|---|
| Hook | `LootManagerMixin` |
| Execution side | Logical server |
| Rule source | `chest_content/chest_loot.txt` |

`LootManagerMixin` is preferred over `LootTableEvents.MODIFY` because chest loot rules require meta/NBT-level filtering of individual loot table entries, which the public event does not support at sufficient depth.

### §3.7 World Generation

| Attribute | Value |
|---|---|
| Hook | `LuckyNaturalGenFeature` registered as a Fabric `Feature` |
| Config flag | `naturalGenEnabled` (boolean) |
| Rule source | `world_gen/surface.txt`, `world_gen/underground.txt` |
| Registration | Each addon's natural gen rules become separate `PlacedFeature` instances via `LuckyNaturalGenRegistrar` |

World gen features are registered during phase 4 and installed into the Fabric biome modification API. They are not re-registered during `/reload`.

---

## §4 Reload Standards

The reload listener is registered via:

```java
ResourceManagerHelper.get(ResourceType.SERVER_DATA)
    .registerReloadListener(/* anonymous SimpleResourceReloadListener */);
```

### §4.1 Reload Listener Responsibilities

The following operations are permitted and required during a `/reload`:

1. Re-run all `*Loader` parsers against the existing `PackIndex` (addon zip files do not change at runtime).
2. Rebuild all rule collections (`ChestLootRegistry`, `BlockDropRegistry`, `EntityDropRegistry`, etc.).
3. Rebuild `RuntimeIndex` from the new rule collections.
4. Re-run `DiagnosticExporter` if `exportReports=true`.

### §4.2 Operations Prohibited During Reload

The following operations must never be performed during a `/reload`:

| Prohibited operation | Reason |
|---|---|
| New item/block/entity registration | The Fabric Registry is frozen; any write produces undefined behavior |
| New mixin installation | Mixins are applied at class-load time; they cannot be added at runtime |
| Re-scanning addon directories | Pack discovery is a bootstrap-time operation; packs are fixed for the session |

---

## §5 Resource Pack Injection

### §5.1 Provider Implementation

`ExternalPackProvider` implements Fabric's `ResourcePackProvider` interface. During the `register(Consumer<ResourcePackProfile>)` call, it creates one `ResourcePackProfile` per discovered addon pack and passes each profile to the consumer. The profiles are created with lower priority than user-selected resource packs (see §5.4).

### §5.2 Coverage

Each addon pack exposes its entire `assets/**` tree. The provider does not filter by namespace. This is required because addon packs commonly include assets in multiple namespaces simultaneously:

- `assets/lucky/**` — Lucky Block textures, models, sounds
- `assets/minecraft/**` — vanilla overrides
- `assets/<addon-namespace>/**` — addon-specific assets

Filtering by a single namespace would silently discard assets that addons require.

### §5.3 Legacy Format Patching

`LegacyPatchingResourcePack` wraps each addon `ResourcePack` and intercepts resource reads. It delegates to `LegacyResourcePackPatcher` to apply the following fixes before returning data to the resource manager:

| Fix | 1.8 format | 1.18.2 format | WARN type |
|---|---|---|---|
| Blockstate variant key | `variants.normal` | `variants[""]` (empty string key) | `LegacyBlockstate` |
| Model path prefix | bare name (e.g., `stone`) | `block/stone` or `item/stone` | `LegacyTexture` |
| Texture directory | `textures/blocks/` | `textures/block/` | `LegacyTexture` |
| Texture directory | `textures/items/` | `textures/item/` | `LegacyTexture` |
| Common texture renames | 1.8 wool/log/terracotta names | 1.18.2 canonical names | `LegacyTexture` |
| Language file format | `.lang` (key=value) | `.json` (JSON object) | `LegacyTexture` |

Every applied fix emits a WARN via `LegacyWarnReporter`. No fix is applied silently.

For language files, the conversion procedure additionally strips any leading BOM (`\uFEFF`) and attempts UTF-8 strict decoding first, falling back to ISO-8859-1/CP1252 if UTF-8 decoding fails.

### §5.4 Priority

Addon packs are injected with lower priority than user-selected resource packs. This allows players to override addon assets with their own resource pack selections without requiring modifications to the addon zip files themselves.

---

## §6 Mixin Policy

Mixins are added only when the Fabric API is insufficient for the required behavior. All mixins must be thin: they pass data to `RuleEngine` or `RuntimeState` and must not embed game logic directly. Prefer `@Inject` over `@Redirect`; prefer `@Inject(at = @At("RETURN"))` over head injection unless a head injection is strictly necessary.

### §6.1 Mixin Inventory (12 mixins)

| Mixin | Side | Purpose |
|---|---|---|
| `ArmorFeatureRendererMixin` | Client | Renders dynamic armor textures sourced from addon resource packs |
| `BlockDropMixin` | Server | Fine-grained block drop interception with fortune/silk-touch awareness |
| `EntityRenderDispatcherMixin` | Client | Suppresses missing entity renderer errors when `skipMissingEntityRenderers=true` |
| `GameMenuScreenMixin` | Client | Injects "✦ Loot++" diagnostic button into the pause menu |
| `ItemStackSizeMixin` | Both | Applies `StackSizeRegistry` maximum-stack-size overrides |
| `LootManagerMixin` | Server | Injects `ChestLootRegistry` rules into loot table evaluation |
| `MinecraftClientMixin` | Client | Hooks resource-pack reload to re-initialize addon texture atlases |
| `PackScreenMixin` | Client | Displays addon packs in the resource pack selection screen |
| `PlayerEntityEatMixin` | Both | Applies addon-defined food effects on item consumption |
| `PlayerManagerMixin` | Server | Server-side player join hook for initial state setup |
| `RecipeManagerMixin` | Both | Injects dynamically-defined crafting recipes |
| `ResourcePackProfile{Mixin,Accessor,EntryMixin}` | Client | Injects addon packs into the ordered `ResourcePackProfile` list |

### §6.2 LuckyBlock Mixin Layer

The `mixin/Lucky*` classes form a dedicated compatibility shim between Re-LootPlusPlus and the LuckyBlock Fabric mod (compile-only dependency). These mixins are necessarily thicker than others because there is no Fabric API equivalent for LuckyBlock internals.

| Mixin | Purpose |
|---|---|
| `LuckyParserMixin` | Tolerates bare `@chance` attribute (no `=` value); normalizes to `chance=1` + WARN |
| `LuckyFabricGameApiMixin` | Normalizes legacy entity IDs in NBT before `EntityType.loadEntityWithPassengers` |
| `LuckyLoaderMixin` | Patches addon crafting recipe parse errors that would otherwise abort the entire addon load |
| `LuckyDropActionsMixin` | Intercepts Lucky drop actions for sanitization |
| `LuckyDropEvaluatorMixin` | Intercepts drop evaluation for compat fixes |
| `LuckyDropSanitizerMixin` | Intercepts drop sanitization pass |
| `LuckySingleDropMixin` | Intercepts single drop execution |
| `LuckyWeightedDropMixin` | Intercepts weighted drop selection |
| `LuckyAddonCraftingMixin` | Handles item ID fallback for addon-registered items in crafting |
| `LuckyAddonCraftingSafeMixin` | Safe variant of the crafting fallback for error isolation |
| `LuckyAddonIdFallbackMixin` | General item ID fallback for unknown addon IDs |
| `LuckyPluginInitMixin` | Hook into LuckyBlock plugin initialization sequence |

All findings from LuckyBlock mixin layer must still be routed through `LegacyWarnReporter`.

---

## §7 Unified Implementation Constraints (Summary)

The following constraints apply to all implementation code across all packages:

1. **WARN on all legacy compat.** Every adaptation of a 1.8 value (selector parameters, legacy command syntax, legacy sound/effect IDs, meta wildcards, legacy chest types, old entity names, old block/item IDs) must emit a WARN via `LegacyWarnReporter.warn()` or `warnOnce()`. No silent fixes.

2. **`SourceLoc` on every WARN.** Every `warn()` call must be accompanied by a `SourceLoc` that identifies the pack, inner path, and line number of the source line. Format: `packId:innerPath:lineNumber`.

3. **Per-line error isolation.** Parse failures are per-line. A bad line emits WARN and is skipped; it must not abort parsing of subsequent lines, the current file, or other files. Only unreadable zip/file I/O is a hard error, and even in that case the remaining packs must continue loading.

4. **`successCount` semantics are fixed.** See ADAPTION.md for the canonical table. Changing successCount definitions breaks `lppcondition` chains in existing addon packs.

5. **Prefer Fabric Events over mixins.** Add a mixin only when the Fabric API provides no equivalent event at the required granularity. Document the reason in the mixin class Javadoc.

6. **Registry writes in phase 4 only.** No code path outside phase 4 may write to a Minecraft or Fabric registry. This constraint is enforced by assertion in `Bootstrap.java`.

7. **`world.random` for all randomness.** Rule evaluation must use `world.random` (or `player.getRandom()`). Creating `new Random()` inside rule evaluation is prohibited; it breaks weighted-roll determinism.

---

## §8 AddonDisableStore and `/reload` Lifecycle

### §8.1 AddonDisableStore Persistence

**Source:** `config/AddonDisableStore.java`

Pack enable/disable state is stored in a **dedicated** file at `.minecraft/config/relootplusplus_addons.json`, separate from the main `relootplusplus.json`. This design allows the in-game debug UI to toggle individual packs without rewriting or conflating the main configuration.

The file is a plain JSON object mapping pack IDs (strings) to booleans:
```json
{
  "lucky_fairy_addon": false,
  "lucky_water_addon": true
}
```

A missing key means the pack is **enabled** by default; only explicitly disabled packs have an entry.

**One-time migration:** When `relootplusplus_addons.json` does not yet exist, `AddonDisableStore` reads the `disabledAddonPacks` array from the main config, writes those IDs as `false` entries into the new file, then clears `disabledAddonPacks` in the main config and saves it. After migration the two files are fully independent.

### §8.2 `/reload` Interaction with PackIndex

`/reload` triggers a partial re-bootstrap. The phases affected are:

| Phase | On `/reload` |
|---|---|
| 1 — Load config | Re-read from disk |
| 2 — Pack discovery | **Skipped** — pack list is fixed at startup |
| 3 — Pack indexing | **Skipped** — `PackIndex` is reused as-is |
| 4 — Parse rules | **Re-run** — all `*Loader` + `LuckyAddonLoader` |
| 5 — Register content | **Skipped** — registries are immutable after startup |
| 6 — World gen | **Skipped** |
| 7 — RuntimeIndex | **Rebuilt** from freshly parsed rules |
| 8 — Install hooks | **Skipped** — hooks remain installed |
| 9 — Export diagnostics | **Re-run** if `exportReports=true` |

**Key constraint:** Because pack discovery (phase 2) and registry writes (phase 5) are skipped, `/reload` cannot:
- Recognize newly added addon zips dropped into `lootplusplus_addons/` while the game is running.
- Register new dynamic items, blocks, or entities that were not present at startup.

New packs and new items always require a full restart.

---

*End of INJECTION.md (English section)*

---

# Re-LootPlusPlus — Fabric 注入策略

**版本：** Minecraft 1.18.2 · Fabric Loader 0.18.4 · Fabric API 0.77.0+1.18.2  
**模块：** `ie.orangep.reLootplusplus`  
**Mod ID：** `re-lootplusplus`

---

## 实现类索引

```
bootstrap/Bootstrap.java                      — 九阶段编排器；所有注册表写入仅发生于第 5 阶段
hooks/HookInstaller.java                      — 安装全部 Fabric 事件钩子（不执行注册）
mixin/                                        — 12 个精简混入；优先使用 Fabric API 事件而非混入
resourcepack/ExternalPackProvider.java        — 在客户端将插件包挂载为资源包
resourcepack/LegacyPatchingResourcePack.java  — 在资源包被消费前修复 1.8 格式
```

---

## §1 生命周期边界（硬性约束）

### §1.1 服务端/通用初始化 — `ModInitializer#onInitialize`

以下所有阶段必须在 `onInitialize` 期间完成。任何阶段均不得延迟至 Minecraft 生命周期的后续阶段执行。

| 阶段 | 职责 |
|---|---|
| 1. 包发现 | 扫描插件目录和 zip 文件；构建 `List<AddonPack>` |
| 2. 包索引 | 构建含逐行 `SourceLoc` 元数据的 `PackIndex` |
| 3. 配置解析 | 运行各 `*Loader` 以生成规则和定义 |
| 4. 内容注册 | 注册物品、方块、实体、方块实体、物品组（全部注册表写入） |
| 5. RuntimeIndex 构建 | 建立 `TriggerType → itemId/blockId → List<Rule>` 映射 |
| 6. 钩子安装 | 通过 `HookInstaller` 注册 Fabric 事件 |
| 7. 重载监听器 | 注册数据重载监听器（仅重建规则与索引，不写入注册表） |

**硬性约束：** Fabric 注册表在 `onInitialize` 返回后即被冻结。物品、方块、实体及物品组在此之后不得再被注册。任何向已冻结注册表的写入行为将产生未定义结果。`/reload` 命令绝对不得尝试新增注册表条目。

`Bootstrap.java` 是唯一的编排器，按上述顺序执行全部七个阶段，并在每个阶段的开始与结束时输出阶段级诊断日志条目。任何其他类均不得发起注册表写入操作。

### §1.2 客户端初始化 — `ClientModInitializer#onInitializeClient`

客户端初始化期间执行以下操作：

1. 通过 `ExternalPackProvider`（实现 `ResourcePackProvider`）将插件包的 `assets/**` 树挂载为 `ResourcePackProfile` 条目。
2. 为动态注册的实体类型注册实体渲染器。
3. 为动态注册的方块实体类型注册方块实体渲染器。
4. 注册方块颜色提供者和物品颜色提供者（可选，仅当插件包提供颜色数据时）。

渲染器与颜色提供者的客户端注册可在 `onInitialize` 之后执行，因为这些注册表仅存在于客户端，其冻结时间与通用注册表不同。

---

## §2 注册表标准

### §2.1 ID 规范化（强制要求）

所有注册表标识符必须满足以下规则：

- 以 `new Identifier(modid, path)` 形式构造。
- `path` 段必须仅包含小写 ASCII 字母、十进制数字及下划线（`[a-z0-9_]`）。
- `namespace` 段对于 mod 自有标识符使用 `re-lootplusplus`，对于动态注册的插件包内容使用 `lootplusplus`。

外部插件包名称可能包含点号、大写字母或其他在 Fabric `Identifier` 中不合法的字符（例如 `astral.fairy`、`LuckySword`）。规范化流程如下：

```
normalized = rawName
    .toLowerCase()
    .replace('.', '_')
    .replaceAll("[^a-z0-9_]", "_")
```

原始名称保留于 `AddonPack` 元数据中，并在所有 WARN 及诊断输出中使用。动态插件包物品和方块注册于 `lootplusplus` 命名空间下：

```
lootplusplus:<规范化名称>
```

### §2.2 重复 ID 策略

当两个不同插件包条目产生相同的规范化 ID 时，行为由 `duplicateStrategy` 配置字段控制：

| 值 | 行为 |
|---|---|
| `suffix` | 向重复 ID 追加 `_2`、`_3` 等后缀（默认值）。同时发出 WARN。 |
| `ignore` | 完全跳过重复条目。同时发出 WARN。 |

两种情况下均发出 WARN，包含原始条目和重复条目的 `SourceLoc`。

### §2.3 创意模式标签页

所有动态注册的插件包物品均放置于专用的 `lootplusplus` 创意模式标签页中。该标签页本身是一个注册表条目（`ItemGroup`），必须在第 4 阶段（内容注册）期间进行注册，且须早于任何引用该标签页的物品。标签页注册由 `CreativeMenuRegistrar` 管理。

---

## §3 事件钩子标准

所有事件钩子均由 `HookInstaller` 在第 6 阶段安装。钩子不得在第 1 至 5 阶段完成之前安装，且不得在 `/reload` 期间安装。

### §3.1 Tick 扫描（held / wearing_armour / in_inventory / standing_on_block / inside_block）

| 属性 | 值 |
|---|---|
| Fabric 钩子 | `ServerTickEvents.END_SERVER_TICK` |
| 执行侧 | 仅逻辑服务端 |
| 扫描间隔 | 由 `tickIntervalTicks` 控制（默认值：1，每 tick 扫描） |
| 触发器门控 | 由 `enabledTriggerTypes` 控制（未列出的触发器类型将被完全跳过） |

扫描遍历所有在线玩家，并对每位玩家按以下固定顺序评估触发器类型：

1. `held` — 主手物品
2. `wearing_armour` — 已装备的护甲（头盔 → 胸甲 → 护腿 → 靴子）；随后执行套装加成检查
3. `in_inventory` — 所有背包格位
4. `standing_on_block` — 玩家脚部正下方的方块
5. `inside_block` — 玩家头部位置的方块

此顺序固定不变，不得在版本间更改。确定性原理详见 REFERENCE.md §2。

### §3.2 右键使用物品（`right_click`）

| 属性 | 值 |
|---|---|
| Fabric 钩子 | `UseItemCallback` |
| 执行侧 | 逻辑服务端 |
| 规则来源 | `item_effects/held.txt` 右键变体 |

### §3.3 攻击实体（`hitting_entity_to_entity`）

| 属性 | 值 |
|---|---|
| Fabric 钩子 | `AttackEntityCallback` |
| 执行侧 | 逻辑服务端 |
| 规则来源 | `item_effects/hitting_entity_to_entity.txt` |

### §3.4 破坏方块（`block_drops` / `digging_block`）

| 属性 | 值 |
|---|---|
| Fabric 钩子 | `PlayerBlockBreakEvents.AFTER` + `BlockDropMixin` |
| 执行侧 | 逻辑服务端 |
| 规则来源 | `block_drops/adding.txt` |

`BlockDropMixin` 用于实现精细的时运/精准采集掉落物拦截，Fabric API 事件在该粒度上支持不足。

### §3.5 实体死亡（`entity_drops`）

| 属性 | 值 |
|---|---|
| 钩子 | `EntityDeathHook`（通过混入或等效方式） |
| 执行侧 | 逻辑服务端 |
| 规则来源 | `entity_drops/adding.txt` |

### §3.6 箱子战利品（`chest_content`）

| 属性 | 值 |
|---|---|
| 钩子 | `LootManagerMixin` |
| 执行侧 | 逻辑服务端 |
| 规则来源 | `chest_content/chest_loot.txt` |

`LootManagerMixin` 优先于 `LootTableEvents.MODIFY`，因为箱子战利品规则需要对单个战利品表条目进行元数据/NBT 级别的过滤，而公开事件在该深度上支持不足。

### §3.7 世界生成

| 属性 | 值 |
|---|---|
| 钩子 | `LuckyNaturalGenFeature`，注册为 Fabric `Feature` |
| 配置开关 | `naturalGenEnabled`（布尔值） |
| 规则来源 | `world_gen/surface.txt`、`world_gen/underground.txt` |
| 注册方式 | 每个插件包的自然生成规则通过 `LuckyNaturalGenRegistrar` 注册为独立的 `PlacedFeature` 实例 |

世界生成特性在第 4 阶段注册，并通过 Fabric 生物群系修改 API 安装。`/reload` 期间不得重新注册世界生成特性。

---

## §4 重载标准

重载监听器通过以下方式注册：

```java
ResourceManagerHelper.get(ResourceType.SERVER_DATA)
    .registerReloadListener(/* 匿名 SimpleResourceReloadListener */);
```

### §4.1 重载监听器职责

`/reload` 期间允许且必须执行以下操作：

1. 对现有 `PackIndex` 重新运行全部 `*Loader` 解析器（插件包 zip 文件在运行时不会改变）。
2. 重建所有规则集合（`ChestLootRegistry`、`BlockDropRegistry`、`EntityDropRegistry` 等）。
3. 从新规则集合重建 `RuntimeIndex`。
4. 若 `exportReports=true`，重新运行 `DiagnosticExporter`。

### §4.2 重载期间禁止的操作

以下操作在 `/reload` 期间绝对不得执行：

| 禁止操作 | 原因 |
|---|---|
| 新增物品/方块/实体注册 | Fabric 注册表已冻结；任何写入将产生未定义行为 |
| 新增混入安装 | 混入在类加载时应用；不能在运行时新增 |
| 重新扫描插件包目录 | 包发现是引导时操作；包在会话期间固定不变 |

---

## §5 资源包注入

### §5.1 提供者实现

`ExternalPackProvider` 实现 Fabric 的 `ResourcePackProvider` 接口。在 `register(Consumer<ResourcePackProfile>)` 调用期间，为每个已发现的插件包创建一个 `ResourcePackProfile` 并传递给消费者。资源包配置文件以低于用户选择资源包的优先级创建（参见 §5.4）。

### §5.2 覆盖范围

每个插件包暴露其完整的 `assets/**` 树。提供者不按命名空间过滤。这是必要的，因为插件包通常同时包含多个命名空间的资源：

- `assets/lucky/**` — Lucky Block 纹理、模型、音效
- `assets/minecraft/**` — 原版覆盖资源
- `assets/<插件包命名空间>/**` — 插件包专用资源

按单一命名空间过滤会静默丢弃插件包所需的资源。

### §5.3 遗留格式修复

`LegacyPatchingResourcePack` 包装每个插件包的 `ResourcePack` 并拦截资源读取操作。在将数据返回给资源管理器之前，它委托 `LegacyResourcePackPatcher` 应用以下修复：

| 修复内容 | 1.8 格式 | 1.18.2 格式 | WARN 类型 |
|---|---|---|---|
| 方块状态变体键 | `variants.normal` | `variants[""]`（空字符串键） | `LegacyBlockstate` |
| 模型路径前缀 | 裸名称（如 `stone`） | `block/stone` 或 `item/stone` | `LegacyTexture` |
| 纹理目录 | `textures/blocks/` | `textures/block/` | `LegacyTexture` |
| 纹理目录 | `textures/items/` | `textures/item/` | `LegacyTexture` |
| 常见纹理重命名 | 1.8 羊毛/原木/陶瓦名称 | 1.18.2 规范名称 | `LegacyTexture` |
| 语言文件格式 | `.lang`（键=值） | `.json`（JSON 对象） | `LegacyTexture` |

每次应用修复均通过 `LegacyWarnReporter` 发出 WARN。不存在静默修复。

对于语言文件，转换流程还须额外剥离任何前导 BOM（`\uFEFF`），并优先尝试 UTF-8 严格解码，解码失败时回退至 ISO-8859-1/CP1252。

### §5.4 优先级

插件包以低于用户选择资源包的优先级注入。这允许玩家以自己的资源包选择覆盖插件包资源，而无需修改插件包 zip 文件本身。

---

## §6 混入策略

仅当 Fabric API 不足以实现所需行为时，才可添加混入。所有混入必须保持精简：混入将数据传递给 `RuleEngine` 或 `RuntimeState`，不得直接嵌入游戏逻辑。优先使用 `@Inject` 而非 `@Redirect`；优先使用 `@Inject(at = @At("RETURN"))` 而非头部注入，除非头部注入为严格必要。

### §6.1 混入清单（12 个混入）

| 混入 | 侧 | 用途 |
|---|---|---|
| `ArmorFeatureRendererMixin` | 客户端 | 渲染来自插件包资源包的动态护甲纹理 |
| `BlockDropMixin` | 服务端 | 支持时运/精准采集感知的精细方块掉落物拦截 |
| `EntityRenderDispatcherMixin` | 客户端 | 当 `skipMissingEntityRenderers=true` 时抑制缺失实体渲染器错误 |
| `GameMenuScreenMixin` | 客户端 | 在暂停菜单中注入"✦ Loot++"诊断按钮 |
| `ItemStackSizeMixin` | 双端 | 应用 `StackSizeRegistry` 最大堆叠数量覆盖 |
| `LootManagerMixin` | 服务端 | 将 `ChestLootRegistry` 规则注入战利品表评估流程 |
| `MinecraftClientMixin` | 客户端 | 钩入资源包重载以重新初始化插件包纹理图集 |
| `PackScreenMixin` | 客户端 | 在资源包选择界面显示插件包 |
| `PlayerEntityEatMixin` | 双端 | 在物品消耗时应用插件包定义的食物效果 |
| `PlayerManagerMixin` | 服务端 | 玩家加入时的服务端侧初始状态设置钩子 |
| `RecipeManagerMixin` | 双端 | 注入动态定义的合成配方 |
| `ResourcePackProfile{Mixin,Accessor,EntryMixin}` | 客户端 | 将插件包注入有序的 `ResourcePackProfile` 列表 |

### §6.2 LuckyBlock 混入层

`mixin/Lucky*` 类构成 Re-LootPlusPlus 与 LuckyBlock Fabric Mod（仅编译期依赖）之间的专用兼容垫片层。由于不存在对应 LuckyBlock 内部机制的 Fabric API，这些混入必然比其他混入更厚重。

| 混入 | 用途 |
|---|---|
| `LuckyParserMixin` | 容忍裸 `@chance` 属性（无 `=` 值）；规范化为 `chance=1` + WARN |
| `LuckyFabricGameApiMixin` | 在 `EntityType.loadEntityWithPassengers` 之前规范化 NBT 中的遗留实体 ID |
| `LuckyLoaderMixin` | 修复插件包合成配方解析错误，防止其中止整个插件包加载 |
| `LuckyDropActionsMixin` | 拦截 Lucky 掉落动作以进行清理 |
| `LuckyDropEvaluatorMixin` | 拦截掉落评估以进行兼容性修复 |
| `LuckyDropSanitizerMixin` | 拦截掉落清理流程 |
| `LuckySingleDropMixin` | 拦截单次掉落执行 |
| `LuckyWeightedDropMixin` | 拦截加权掉落选择 |
| `LuckyAddonCraftingMixin` | 处理合成配方中插件包注册物品的 ID 回退 |
| `LuckyAddonCraftingSafeMixin` | 合成回退的安全变体，用于错误隔离 |
| `LuckyAddonIdFallbackMixin` | 未知插件包 ID 的通用物品 ID 回退 |
| `LuckyPluginInitMixin` | 钩入 LuckyBlock 插件初始化序列 |

LuckyBlock 混入层的所有发现均须通过 `LegacyWarnReporter` 上报。

---

## §7 统一实现约束（摘要）

以下约束适用于所有包中的全部实现代码：

1. **所有遗留兼容必须发出 WARN。** 每次对 1.8 值的适配（选择器参数、遗留命令语法、遗留音效/效果 ID、元数据通配符、遗留箱子类型、旧实体名称、旧方块/物品 ID）均须通过 `LegacyWarnReporter.warn()` 或 `warnOnce()` 发出 WARN。不存在静默修复。

2. **每条 WARN 必须附带 `SourceLoc`。** 每次 `warn()` 调用均须附带标识来源包、内部路径及行号的 `SourceLoc`。格式：`packId:innerPath:lineNumber`。

3. **逐行错误隔离。** 解析失败为逐行处理。一行出错将发出 WARN 并跳过该行；不得中止后续行、当前文件或其他文件的解析。仅 zip/文件 I/O 不可读属于硬错误，即使在此情况下，其余包仍须继续加载。

4. **`successCount` 语义固定。** 规范表见 ADAPTION.md。更改 successCount 定义将破坏现有插件包中的 `lppcondition` 条件链。

5. **优先使用 Fabric 事件而非混入。** 仅当 Fabric API 在所需粒度上无对应事件时，才添加混入。原因须在混入类的 Javadoc 中说明。

6. **注册表写入仅在第 4 阶段进行。** 第 4 阶段以外的任何代码路径均不得向 Minecraft 或 Fabric 注册表写入。`Bootstrap.java` 通过断言强制执行此约束。

7. **所有随机性使用 `world.random`。** 规则评估须使用 `world.random`（或 `player.getRandom()`）。在规则评估内部创建 `new Random()` 是被禁止的；这会破坏加权抽取的确定性。

---

## §8 AddonDisableStore 与 `/reload` 生命周期

### §8.1 AddonDisableStore 持久化

**源类：** `config/AddonDisableStore.java`

包的启用/禁用状态存储在独立的文件 `.minecraft/config/relootplusplus_addons.json` 中，与主配置文件 `relootplusplus.json` 分开。这种设计允许游戏内调试 UI 切换单个包的状态，而不会重写或混淆主配置。

该文件是将包 ID（字符串）映射到布尔值的纯 JSON 对象：
```json
{
  "lucky_fairy_addon": false,
  "lucky_water_addon": true
}
```

键缺失表示包**默认启用**；只有明确禁用的包才有对应条目。

**一次性迁移：** 当 `relootplusplus_addons.json` 尚不存在时，`AddonDisableStore` 从主配置中读取 `disabledAddonPacks` 数组，将这些 ID 以 `false` 条目写入新文件，然后清除主配置中的 `disabledAddonPacks` 并保存。迁移完成后，两个文件完全独立。

### §8.2 `/reload` 与 PackIndex 的交互

`/reload` 触发部分重新启动。受影响的阶段如下：

| 阶段 | `/reload` 行为 |
|---|---|
| 1 — 加载配置 | 从磁盘重新读取 |
| 2 — 包发现 | **跳过** — 包列表在启动时固定 |
| 3 — 包索引 | **跳过** — `PackIndex` 原样复用 |
| 4 — 解析规则 | **重新运行** — 所有 `*Loader` + `LuckyAddonLoader` |
| 5 — 注册内容 | **跳过** — 启动后注册表不可变 |
| 6 — 世界生成 | **跳过** |
| 7 — RuntimeIndex | 从新解析的规则**重新构建** |
| 8 — 安装钩子 | **跳过** — 钩子保持安装状态 |
| 9 — 导出诊断 | 若 `exportReports=true` 则**重新运行** |

**关键约束：** 由于包发现（第 2 阶段）和注册表写入（第 5 阶段）被跳过，`/reload` **无法**：
- 识别游戏运行时新放入 `lootplusplus_addons/` 的附加包 zip。
- 注册启动时不存在的新动态物品、方块或实体。

新的包和新的物品始终需要完整重启。

---

*INJECTION.md 结束（中文部分）*
