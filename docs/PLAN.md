> **Language / 语言:** [English](#english-version) · [中文](#中文版本chinese-version)

---

## English Version

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

---

## 中文版本（Chinese Version）

# Re-LootPlusPlus — 实现说明

> **状态：实现已完成。**
> 本文件最初为分阶段构建计划，现作为架构参考与关键决策记录保存。

---

## 已构建的内容

Re-LootPlusPlus 是一个 Minecraft **1.18.2 Fabric** 模组，具备以下功能：

1. **原生重实现 Loot++ 1.8.9 行为** — 扫描附加包 zip/文件夹，解析 `config/**/*.txt`，将规则接入 Fabric 事件。无需修改附加包 zip。
2. **原生重实现 Lucky Block** — 在 `lucky:` 命名空间下注册 `lucky:lucky_block`、`lucky:lucky_sword`、`lucky:lucky_bow`、`lucky:lucky_potion`。从 Lucky 附加包 zip 中解析 `drops.txt`/`bow_drops.txt`。运行时无需依赖 Lucky Block Fabric jar。
3. **提供完整游戏内 UI** — 通过注入暂停菜单的 `✦ Loot++` 按钮访问。

---

## 架构摘要

完整包树结构见 `STRUCTURE.md`。关键架构要点：

- **`Bootstrap.java`** 为唯一的流程编排器。九个顺序阶段：加载配置 → 发现附加包 → 建立索引 → 解析规则 → 注册内容 → 世界生成 → 构建 RuntimeIndex → 安装钩子 → 导出诊断。
- **注册锁定**：物品/方块/实体注册仅在第 5 阶段发生。`/reload` 仅重建 `RuntimeIndex` 与规则。
- **`LegacyWarnReporter`**：所有 1.8→1.18.2 适配必须调用 `warn()` 或 `warnOnce()`。不得静默处理。
- **`DebugFileWriter`**：当 `debugFileEnabled=true` 且 `logDetailLevel≥detail` 时，所有 debug/trace 行均写入 `<exportDir>/debug-<timestamp>.log`，不受控制台过滤级别影响。

---

## 配置

运行时配置位于 `.minecraft/config/relootplusplus.json`，共 25 个字段，分为四类：

- **核心**：`dryRun`、`exportReports`、`exportRawLines`、`exportDir`、`extraAddonDirs`、`duplicateStrategy`、`potioncoreNamespace`、`skipMissingEntityRenderers`、`injectResourcePacks`、`disabledAddonPacks`
- **日志**：`logWarnings`、`logLegacyWarnings`、`logDebug`、`logDetailLevel`、`logDetailFilters`、`legacyWarnConsoleLimitPerType`、`legacyWarnConsoleSummary`、`debugFileEnabled`、`debugFileMaxLines`
- **掉落引擎**：`luckModifier`、`defaultLuck`、`commandDropEnabled`、`dropChatEnabled`
- **Tick / 世界**：`tickIntervalTicks`、`enabledTriggerTypes`、`structureMaxDimension`、`scanModsDir`、`naturalGenEnabled`、`legacySanitizeEnabled`

---

## 行为约束（固定）

### 掉落组权重语义
在 `%%%%%` 分隔的掉落组中，仅第一条目的权重参与加权随机抽取。若该组被选中，**所有**条目均执行。与 Loot++ 1.8.9 完全一致。

### successCount 定义
| 命令 | successCount |
|---|---|
| `clear` | 实际移除的物品数量 |
| `effect`、`playsound`、`scoreboard`、`kill`、`testfor` | 成功作用的目标数量 |
| `execute` | 所有目标的子命令 successCount 之和 |
| `summon`、`setblock`、`gamerule` | 成功时为 1，失败时为 0 |
| `lppcondition` | 所选分支的 successCount |

### Tick 扫描顺序（确定性）
`held` → `wearing_armour` → `in_inventory` → `standing_on_block` → `inside_block`

---

## 已知缺口 / 延期项目

- `fishing_amounts` 语法：部分实现；未针对 JAR 字节码完整验证边缘情况。
- `entity_drops` 移除规范：基本支持；复杂选择器交互可能与 1.8.9 有差异。
- 物品扩展：弓/枪/多功能工具——注册正常；完整的抛射物/弹药行为仅部分实现。
- 方块扩展：除 generic/plants/crops/cakes 以外的附加原始类型（按钮、压力板、台阶、楼梯、玻璃板、墙）尚未实现。
- `.luckystruct` 结构格式：读取支持存在，但放置保真度可能与原版存在差异。

---

## 规范文档

其他 Markdown 文件描述了从 JAR 字节码分析中提取的 Loot++ 1.8.9 行为规范，定义了本模组的实现依据：

- `PARSER.md` — 配置文件语法（每种 `config/*.txt` 格式的 BNF/EBNF）
- `ADAPTION.md` — 遗留选择器解析器与命令运行器语义
- `INJECTION.md` — Fabric 注入策略与生命周期约束
- `REFERENCE.md` — 钩子矩阵、执行顺序与解析/执行示例
- `ADDITION.md` — 附加格式规范（命令链、记录格式等）
