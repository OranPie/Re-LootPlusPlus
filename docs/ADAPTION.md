# Re-LootPlusPlus — Legacy Selector & Command Semantics

> **Language / 语言:** [English](#english-version) · [中文](#中文版本chinese-version)

> **Implementation classes:**
> - `legacy/selector/LegacySelectorParser.java` — parses and evaluates legacy selectors
> - `legacy/selector/SelectorContext.java` — evaluation context (world, origin, random)
> - `command/LegacyCommandRunner.java` — interprets the 1.8 command subset
> - `command/exec/CommandChain.java` — splits on top-level `;`; warns on `&&`/`||`
> - `command/exec/ExecResult.java` — carries `successCount`

> All legacy selector parameters and remapped command behaviors must call
> `LegacyWarnReporter.warn()` or `warnOnce()`. Silent adaptation is prohibited.
>
> WARN format:
> ```
> [WARN] [LootPP-Legacy] <Type> <detail> @ <packId>:<innerPath>:<lineNumber>
> ```

---

## English Version

### §1 LegacySelectorParser

#### §1.1 Interface

| Item | Description |
|---|---|
| Input | `selectorString` (e.g. `@p[r=0,score_x_min=1]`), `SelectorContext ctx` |
| `SelectorContext` fields | `world`, `originPos`, `sender`, `server`, `random` |
| Output | `List<Entity> targets` (stable order) |
| Failure policy | Parse failure → WARN + return empty list; no exception propagated |

#### §1.2 Syntax (EBNF)

```ebnf
selector      := at_kind [ selector_args ] ;
at_kind       := "@p" | "@a" | "@r" | "@e" ;
selector_args := "[" arg ( "," arg )* "]" ;
arg           := key "=" value ;
key           := ( letter | "_" ) ( letter | digit | "_" )* ;
value         := ( any char except "," and "]" )* ;
```

Values do not support quoted strings or escape sequences. This matches 1.8.9 behavior exactly.

#### §1.3 Supported Parameter Keys

**Coordinate / range:**

| Key | Type | Default | Semantics |
|---|---|---|---|
| `x`, `y`, `z` | float | `ctx.originPos` component | Base point for distance/AABB computation |
| `r` | float | — | Maximum radius (inclusive) |
| `rm` | float | — | Minimum radius (inclusive) |
| `dx`, `dy`, `dz` | float | — | AABB dimensions from `(x,y,z)`; negative values allowed; normalized to min/max |

**Count / ordering:**

| Key | Semantics |
|---|---|
| `c` | Result count limit. `c > 0` → take first `c` results; `c < 0` → take last `|c|` results; WARN if `c < 0` |
| `@p` default | `c = 1` (nearest player) |
| `@r` default | `c = 1` (one random player) |
| `@a` / `@e` default | No limit |

**Type / name / team:**

| Key | Semantics |
|---|---|
| `type` | Entity type filter; supports `type=!value` negation; WARN on negation |
| `name` | Entity display name exact match; supports `!` negation; WARN on negation |
| `team` | Scoreboard team name; supports `!` negation; WARN on negation |

**Player attributes:**

| Key | Semantics |
|---|---|
| `m` | Game mode: `0` = survival, `1` = creative, `2` = adventure, `3` = spectator; non-player entities filtered out |
| `l` | Experience level upper bound (inclusive) |
| `lm` | Experience level lower bound (inclusive) |

**Scoreboard (critical for Astral / Plural packs):**

| Key | Semantics |
|---|---|
| `score_<objective>` | Require entity score on `<objective>` ≤ value |
| `score_<objective>_min` | Require entity score on `<objective>` ≥ value |

If the named objective does not exist: emit `WARN [LootPP-Legacy] ScoreObjectiveMissing` and filter result to empty (no match for any entity).

#### §1.4 Evaluation Algorithm (fixed order)

1. **Determine candidate set:**
   - `@a`, `@p`, `@r` → all players currently in the world
   - `@e` → all entities currently in the world

2. **Determine base point:**
   - Default = `ctx.originPos`
   - Override with any provided `x`/`y`/`z` arguments; missing axes retain the corresponding `ctx.originPos` component

3. **Apply filters in fixed order:**
   a. Non-spatial filters: `type`, `name`, `team`, `m`, `l`, `lm`
   b. AABB filter (`dx`/`dy`/`dz`), if any dimension argument is provided
   c. Radius filter (`rm`/`r`), if either is provided
   d. Scoreboard filters (`score_*` / `score_*_min`)

4. **Sort and limit:**
   - `@p`: ascending Euclidean distance from base point; tie-break by entity ID (stable)
   - `@r`: shuffle filtered candidates using `ctx.random`; take first `c`
   - `@a` / `@e`: ascending entity ID (stable)
   - Apply `c` limit after sort

#### §1.5 Distance Definition

- Distance is Euclidean between entity position and base point.
- For performance, implementations may compare squared distances.
- `rm` and `r` bounds are **inclusive**: `rm ≤ dist ≤ r`.
- AABB defined by `dx`/`dy`/`dz` includes boundary.

#### §1.6 WARN Requirements (mandatory)

Every use of a legacy selector parameter must emit a WARN. The following types are defined:

| Condition | WARN type |
|---|---|
| Any legacy parameter used (`r`, `rm`, `c`, `score_*`, etc.) | `SelectorParam` |
| Negation `!` used on any key | `SelectorNegation` |
| Named scoreboard objective does not exist | `ScoreObjectiveMissing` |
| Parse failure or unknown parameter key | `SelectorParse` |

WARN format:
```
[WARN] [LootPP-Legacy] SelectorParam r=0 @ packId:inner/path.txt:42
```

---

### §2 LegacyCommandRunner — Command Semantics

#### §2.1 Execution Model

| Item | Description |
|---|---|
| Input | `commandString` (may have leading `/`), `ExecContext ctx` |
| Output | `ExecResult { int successCount }` |
| Brigadier | Must **not** be used to parse 1.8 syntax |
| Single failure | WARN + return `successCount = 0`; no exception thrown |
| Legacy mapping | Any remapping → mandatory WARN; no silent adaptation |

#### §2.2 Token Parsing Rules

1. Strip leading `/`, then `trim()`.
2. First whitespace-delimited token = verb (command name).
3. NBT tokens `{...}`: consume from first `{` to matching `}` as a single token; may contain internal spaces.
4. Selector tokens `@p[...]`, `@a[...]`, etc.: the entire selector string is one token; no spaces inside.

#### §2.3 Command Semantics

---

##### `lppcondition` (built-in)

**Syntax:**
```
lppcondition <condCmd> _if_true_ <thenCmd> [_if_false_ <elseCmd>]
```

**Semantics:**
1. Execute `condCmd` → obtain `k = successCount`.
2. If `k > 0`: execute `thenCmd`; otherwise execute `elseCmd` (if present).
3. Return the executed branch's `successCount`.

**Failure:** Malformed separators → `WARN [LootPP-Legacy] lppcondition parse` + return `successCount = 0`.

---

##### `lppeffect` (built-in)

**Syntax:**
```
lppeffect <targets> <effectId> <seconds> <amplifier> <showParticles>
lppeffect clear <targets> [effectId]
```

| Subcommand | `successCount` definition |
|---|---|
| apply | Number of targets successfully affected |
| `clear` | Number of targets from which the effect was removed |

WARN: numeric or legacy effect name → `WARN [LootPP-Legacy] LegacyEffect`.

---

##### `clear` (1.8 compatible)

**Syntax:**
```
clear <targets> [itemId] [meta] [count]
```

Removes specified items from target player inventories.

| Field | Notes |
|---|---|
| `meta` | WARN: `WARN [LootPP-Legacy] LegacyMeta` |
| old `itemId` | WARN: `WARN [LootPP-Legacy] LegacyItemId` |

`successCount`: total number of item instances actually removed, summed across all targets.

This definition enables `lppcondition clear ... _if_true_ ...` to correctly test item consumption: if exactly one item is removed, `successCount = 1 > 0`, so the then-branch executes.

---

##### `effect` (1.8 compatible)

**Syntax:**
```
effect <targets> <effectId|name|number> [seconds] [amplifier] [hideParticles]
effect <targets> clear [effectId]
```

| Subcommand | `successCount` definition |
|---|---|
| apply | Number of targets affected |
| `clear` | Number of targets from which the effect was removed |

WARN: numeric or legacy effect name → `WARN [LootPP-Legacy] LegacyEffect`.

---

##### `playsound` (1.8 compatible)

**Syntax:**
```
playsound <soundId> <targets> [x y z] [volume] [pitch] [minVolume]
```

Plays the specified sound for each target player.

- Coordinates: use provided `x`/`y`/`z` if present; otherwise `ctx.originPos`.
- `successCount`: number of target players for whom the sound was played.
- WARN: legacy sound ID → `WARN [LootPP-Legacy] LegacySound` + remap.

---

##### `scoreboard players` (subset)

**Syntax:**
```
scoreboard players set    <targets> <objective> <score>
scoreboard players add    <targets> <objective> <delta>
scoreboard players remove <targets> <objective> <delta>
```

- If the named objective does not exist: WARN + `successCount = 0`; do **not** auto-create the objective.
- `successCount`: number of targets successfully updated.

---

##### `execute` (1.8 compatible)

**Syntax:**
```
execute <targets> <x> <y> <z> <subCommand>
```

For each target entity: construct a sub-context with that entity as sender and the resolved `(x, y, z)` as origin, then execute `subCommand`.

- `successCount`: sum of all sub-command `successCount` values across all targets.
- Empty `subCommand` → `WARN [LootPP-Legacy] LegacyCommand bad arity` + `successCount = 0`.

---

##### `testfor` (1.8 compatible)

**Syntax:**
```
testfor <targets> [nbtJson]
```

Tests whether entities matching the selector exist; optionally applies an NBT contains-match.

- `successCount`: number of matched entities.
- WARN: NBT argument present → `WARN [LootPP-Legacy] LegacyNBT`.

---

##### `summon` (1.8 compatible)

**Syntax:**
```
summon <entityId> [x y z] [nbtJson]
```

| Field | Notes |
|---|---|
| legacy `entityId` | Remap + `WARN [LootPP-Legacy] LegacyEntityId` |
| legacy NBT | `WARN [LootPP-Legacy] LegacyNBT` |

`successCount`: `1` on success, `0` on failure.

---

##### `setblock` (1.8 compatible)

**Syntax:**
```
setblock <x> <y> <z> <blockId> [meta] [mode] [nbtJson]
```

| Field | Default | Notes |
|---|---|---|
| `mode` | `replace` | Allowed values: `replace`, `destroy`, `keep` |
| legacy `blockId` | — | `WARN [LootPP-Legacy] LegacyBlockId` |
| `meta` | — | `WARN [LootPP-Legacy] LegacyMeta` |

`successCount`: `1` on success, `0` on failure.

---

##### `kill`

**Syntax:**
```
kill <targets>
```

`successCount`: number of entities removed.

---

##### `enchant`

**Syntax:**
```
enchant <targets> <enchantId> [level]
```

`successCount`: number of targets successfully enchanted.
WARN: numeric or legacy enchantment ID → `WARN [LootPP-Legacy] LegacyEnchant`.

---

##### `gamerule`

**Syntax:**
```
gamerule <ruleName> <value>
```

`successCount`: `1` on success, `0` on failure.

---

##### `tp` / `teleport`

**Syntax (entity → coordinates):**
```
tp <targets> <x> <y> <z>
teleport <targets> <x> <y> <z>
```

**Syntax (entity → entity):**
```
tp <targets> <destEntity>
teleport <targets> <destEntity>
```

Both `tp` and `teleport` are accepted as verb aliases and behave identically.

`<targets>` is parsed by `LegacySelectorParser` (see §1). `<destEntity>` is a selector resolving to at most one entity; if it resolves to multiple, the **first** result is used. Coordinates `<x> <y> <z>` accept absolute integers/floats or `~`-relative offsets (relative to the **executor's** current position, not the target's).

`successCount`: number of targets successfully teleported.

**WARNs:**
- `r=`, `rm=` in any selector parameter → `WARN [LootPP-Legacy] SelectorParam`
- Entity-to-entity form with a selector that resolves to zero entities → WARN + `successCount = 0`

---

#### §2.4 `successCount` Definitions Summary

| Command | `successCount` |
|---|---|
| `clear` | Actual number of item instances removed (summed across all targets) |
| `effect`, `lppeffect` (apply) | Number of targets successfully affected |
| `effect`, `lppeffect` (clear) | Number of targets from which effect was removed |
| `playsound` | Number of target players sound was played for |
| `scoreboard players` | Number of targets successfully updated |
| `execute` | Sum of sub-command `successCount` values across all targets |
| `testfor` | Number of matched entities |
| `summon`, `setblock`, `gamerule` | `1` on success, `0` on failure |
| `kill` | Number of entities removed |
| `enchant` | Number of targets successfully enchanted |
| `tp`, `teleport` | Number of targets successfully teleported |
| `lppcondition` | `successCount` of the executed branch |

#### §2.5 Uniform Failure / Fallback Rules

| Condition | Behavior |
|---|---|
| Unknown verb | `WARN [LootPP-Legacy] LegacyCommand unknown verb` + `successCount = 0` |
| Insufficient arguments | `WARN [LootPP-Legacy] LegacyCommand bad arity` + `successCount = 0` |
| Mapping failure | `WARN [LootPP-Legacy] LegacyMapping` + `successCount = 0` |

No exception may propagate from `LegacyCommandRunner`. All failures are expressed through `successCount = 0` and a corresponding WARN.

#### §2.6 Regression Examples

**Example 1 — `clear` as condition:**

```
lppcondition clear @p[r=0] lootplusplus:astral.fairy 0 1
_if_true_ scoreboard players set @p[r=0] astralFairyCdwn 10
```

Expected behavior:
- WARN for `r=0` selector parameter (type `SelectorParam`)
- WARN for meta `0` (type `LegacyMeta`)
- `clear` removes 1 item → `successCount = 1`
- `1 > 0` → then-branch executes: `scoreboard players set @p[r=0] astralFairyCdwn 10`

---

**Example 2 — `execute` with radius selector:**

```
execute @e[r=3] ~ ~ ~ effect @e[r=3] potioncore:perplexity 10 10 true
```

Expected behavior:
- WARN for `r=3` in outer selector (type `SelectorParam`)
- `execute` iterates each entity within radius 3
- For each such entity: inner `effect` applies to entities within radius 3 relative to that entity
- `successCount` = sum of all inner `effect` `successCount` values across all outer targets

---

## 中文版本（Chinese Version）

### §1 LegacySelectorParser

#### §1.1 接口定义

| 项目 | 说明 |
|---|---|
| 输入 | `selectorString`（例如 `@p[r=0,score_x_min=1]`），`SelectorContext ctx` |
| `SelectorContext` 字段 | `world`、`originPos`、`sender`、`server`、`random` |
| 输出 | `List<Entity> targets`（稳定顺序）|
| 失败策略 | 解析失败 → WARN + 返回空列表；不抛出异常 |

#### §1.2 语法（EBNF）

```ebnf
selector      := at_kind [ selector_args ] ;
at_kind       := "@p" | "@a" | "@r" | "@e" ;
selector_args := "[" arg ( "," arg )* "]" ;
arg           := key "=" value ;
key           := ( letter | "_" ) ( letter | digit | "_" )* ;
value         := ( 除 "," 和 "]" 以外的任意字符 )* ;
```

值不支持带引号的字符串或转义序列。此行为与 1.8.9 完全一致。

#### §1.3 支持的参数键

**坐标 / 范围：**

| 键 | 类型 | 默认值 | 语义 |
|---|---|---|---|
| `x`、`y`、`z` | float | `ctx.originPos` 对应分量 | 距离 / AABB 计算的基准点 |
| `r` | float | — | 最大半径（包含边界）|
| `rm` | float | — | 最小半径（包含边界）|
| `dx`、`dy`、`dz` | float | — | 从 `(x,y,z)` 出发的 AABB 尺寸；允许负值；自动规范化为 min/max |

**数量 / 排序：**

| 键 | 语义 |
|---|---|
| `c` | 结果数量限制。`c > 0` → 取前 `c` 个；`c < 0` → 取后 `|c|` 个；`c < 0` 时须发出 WARN |
| `@p` 默认值 | `c = 1`（最近的玩家）|
| `@r` 默认值 | `c = 1`（随机一个玩家）|
| `@a` / `@e` 默认值 | 无限制 |

**类型 / 名称 / 队伍：**

| 键 | 语义 |
|---|---|
| `type` | 实体类型过滤；支持 `type=!value` 取反；取反时须发出 WARN |
| `name` | 实体显示名称精确匹配；支持 `!` 取反；取反时须发出 WARN |
| `team` | 计分板队伍名称；支持 `!` 取反；取反时须发出 WARN |

**玩家属性：**

| 键 | 语义 |
|---|---|
| `m` | 游戏模式：`0` = 生存，`1` = 创造，`2` = 冒险，`3` = 旁观者；非玩家实体被过滤 |
| `l` | 经验等级上限（包含）|
| `lm` | 经验等级下限（包含）|

**计分板（对 Astral / Plural 插件包至关重要）：**

| 键 | 语义 |
|---|---|
| `score_<objective>` | 要求实体在 `<objective>` 上的分数 ≤ 值 |
| `score_<objective>_min` | 要求实体在 `<objective>` 上的分数 ≥ 值 |

若命名的计分项不存在：发出 `WARN [LootPP-Legacy] ScoreObjectiveMissing`，并将结果过滤为空（对任何实体均不匹配）。

#### §1.4 评估算法（固定顺序）

1. **确定候选集：**
   - `@a`、`@p`、`@r` → 当前世界中所有玩家
   - `@e` → 当前世界中所有实体

2. **确定基准点：**
   - 默认 = `ctx.originPos`
   - 若提供了 `x`/`y`/`z` 参数则覆盖相应分量；缺失的轴保留 `ctx.originPos` 对应分量

3. **按固定顺序依次应用过滤器：**
   a. 非空间过滤器：`type`、`name`、`team`、`m`、`l`、`lm`
   b. AABB 过滤器（`dx`/`dy`/`dz`），若提供了任意维度参数
   c. 半径过滤器（`rm`/`r`），若提供了其中任意一个
   d. 计分板过滤器（`score_*` / `score_*_min`）

4. **排序与限制：**
   - `@p`：按距基准点的欧氏距离升序排列；距离相同时以实体 ID 为次要键（稳定）
   - `@r`：使用 `ctx.random` 对过滤后的候选集洗牌；取前 `c` 个
   - `@a` / `@e`：按实体 ID 升序排列（稳定）
   - 排序后应用 `c` 限制

#### §1.5 距离定义

- 距离为实体位置与基准点之间的欧氏距离。
- 实现可比较距离的平方以提升性能。
- `rm` 和 `r` 的边界均**包含**：`rm ≤ dist ≤ r`。
- `dx`/`dy`/`dz` 定义的 AABB 包含边界。

#### §1.6 WARN 要求（强制性）

任何遗留选择器参数的使用均须发出 WARN。以下类型已定义：

| 触发条件 | WARN 类型 |
|---|---|
| 任何遗留参数被使用（`r`、`rm`、`c`、`score_*` 等）| `SelectorParam` |
| 任何键上使用了取反 `!` | `SelectorNegation` |
| 命名的计分项不存在 | `ScoreObjectiveMissing` |
| 解析失败或未知参数键 | `SelectorParse` |

WARN 格式：
```
[WARN] [LootPP-Legacy] SelectorParam r=0 @ packId:inner/path.txt:42
```

---

### §2 LegacyCommandRunner — 命令语义

#### §2.1 执行模型

| 项目 | 说明 |
|---|---|
| 输入 | `commandString`（可有前导 `/`），`ExecContext ctx` |
| 输出 | `ExecResult { int successCount }` |
| Brigadier | **不得**用于解析 1.8 语法 |
| 单次失败 | WARN + 返回 `successCount = 0`；不抛出异常 |
| 遗留映射 | 任何重映射 → 强制 WARN；禁止静默适配 |

#### §2.2 令牌解析规则

1. 剥去前导 `/`，然后 `trim()`。
2. 第一个以空格分隔的令牌 = 动词（命令名）。
3. NBT 令牌 `{...}`：从第一个 `{` 到匹配的 `}` 作为一个整体令牌消耗；内部可含空格。
4. 选择器令牌 `@p[...]`、`@a[...]` 等：整个选择器字符串为一个令牌；内部不含空格。

#### §2.3 命令语义详述

---

##### `lppcondition`（内置命令）

**语法：**
```
lppcondition <condCmd> _if_true_ <thenCmd> [_if_false_ <elseCmd>]
```

**语义：**
1. 执行 `condCmd` → 获得 `k = successCount`。
2. 若 `k > 0`：执行 `thenCmd`；否则执行 `elseCmd`（若存在）。
3. 返回所执行分支的 `successCount`。

**失败：** 分隔符格式错误 → `WARN [LootPP-Legacy] lppcondition parse` + 返回 `successCount = 0`。

---

##### `lppeffect`（内置命令）

**语法：**
```
lppeffect <targets> <effectId> <seconds> <amplifier> <showParticles>
lppeffect clear <targets> [effectId]
```

| 子命令 | `successCount` 定义 |
|---|---|
| 施加效果 | 成功受到效果的目标数量 |
| `clear` | 效果被移除的目标数量 |

WARN：数字或遗留效果名称 → `WARN [LootPP-Legacy] LegacyEffect`。

---

##### `clear`（兼容 1.8）

**语法：**
```
clear <targets> [itemId] [meta] [count]
```

从目标玩家背包中移除指定物品。

| 字段 | 说明 |
|---|---|
| `meta` | WARN：`WARN [LootPP-Legacy] LegacyMeta` |
| 旧 `itemId` | WARN：`WARN [LootPP-Legacy] LegacyItemId` |

`successCount`：实际移除的物品实例总数（对所有目标求和）。

此定义使 `lppcondition clear ... _if_true_ ...` 能够正确检测物品消耗：若恰好移除一个物品，则 `successCount = 1 > 0`，从而执行 then 分支。

---

##### `effect`（兼容 1.8）

**语法：**
```
effect <targets> <effectId|name|number> [seconds] [amplifier] [hideParticles]
effect <targets> clear [effectId]
```

| 子命令 | `successCount` 定义 |
|---|---|
| 施加效果 | 受到效果的目标数量 |
| `clear` | 效果被移除的目标数量 |

WARN：数字或遗留效果名称 → `WARN [LootPP-Legacy] LegacyEffect`。

---

##### `playsound`（兼容 1.8）

**语法：**
```
playsound <soundId> <targets> [x y z] [volume] [pitch] [minVolume]
```

为每个目标玩家播放指定音效。

- 坐标：若提供了 `x`/`y`/`z` 则使用之；否则使用 `ctx.originPos`。
- `successCount`：为其播放了音效的目标玩家数量。
- WARN：遗留音效 ID → `WARN [LootPP-Legacy] LegacySound` + 重映射。

---

##### `scoreboard players`（子集）

**语法：**
```
scoreboard players set    <targets> <objective> <score>
scoreboard players add    <targets> <objective> <delta>
scoreboard players remove <targets> <objective> <delta>
```

- 若命名计分项不存在：WARN + `successCount = 0`；**不得**自动创建计分项。
- `successCount`：成功更新的目标数量。

---

##### `execute`（兼容 1.8）

**语法：**
```
execute <targets> <x> <y> <z> <subCommand>
```

对每个目标实体：以该实体为 sender、以解析后的 `(x, y, z)` 为原点构建子上下文，然后执行 `subCommand`。

- `successCount`：所有目标各自子命令 `successCount` 的总和。
- 空 `subCommand` → `WARN [LootPP-Legacy] LegacyCommand bad arity` + `successCount = 0`。

---

##### `testfor`（兼容 1.8）

**语法：**
```
testfor <targets> [nbtJson]
```

检测与选择器匹配的实体是否存在；可选地进行 NBT 包含匹配。

- `successCount`：匹配到的实体数量。
- WARN：提供了 NBT 参数 → `WARN [LootPP-Legacy] LegacyNBT`。

---

##### `summon`（兼容 1.8）

**语法：**
```
summon <entityId> [x y z] [nbtJson]
```

| 字段 | 说明 |
|---|---|
| 遗留 `entityId` | 重映射 + `WARN [LootPP-Legacy] LegacyEntityId` |
| 遗留 NBT | `WARN [LootPP-Legacy] LegacyNBT` |

`successCount`：成功时为 `1`，失败时为 `0`。

---

##### `setblock`（兼容 1.8）

**语法：**
```
setblock <x> <y> <z> <blockId> [meta] [mode] [nbtJson]
```

| 字段 | 默认值 | 说明 |
|---|---|---|
| `mode` | `replace` | 允许值：`replace`、`destroy`、`keep` |
| 遗留 `blockId` | — | `WARN [LootPP-Legacy] LegacyBlockId` |
| `meta` | — | `WARN [LootPP-Legacy] LegacyMeta` |

`successCount`：成功时为 `1`，失败时为 `0`。

---

##### `kill`

**语法：**
```
kill <targets>
```

`successCount`：被移除的实体数量。

---

##### `enchant`

**语法：**
```
enchant <targets> <enchantId> [level]
```

`successCount`：成功附魔的目标数量。
WARN：数字或遗留附魔 ID → `WARN [LootPP-Legacy] LegacyEnchant`。

---

##### `gamerule`

**语法：**
```
gamerule <ruleName> <value>
```

`successCount`：成功时为 `1`，失败时为 `0`。

---

##### `tp` / `teleport`

**语法（实体 → 坐标）：**
```
tp <targets> <x> <y> <z>
teleport <targets> <x> <y> <z>
```

**语法（实体 → 实体）：**
```
tp <targets> <destEntity>
teleport <targets> <destEntity>
```

`tp` 和 `teleport` 均被接受为动词别名，行为完全相同。

`<targets>` 由 `LegacySelectorParser`（见 §1）解析。`<destEntity>` 是最多解析为一个实体的选择器；若解析为多个实体，则使用**第一个**结果。坐标 `<x> <y> <z>` 接受绝对整数/浮点数或 `~` 相对偏移量（相对于**执行者**的当前位置，而非目标位置）。

`successCount`：成功传送的目标数量。

**WARN：**
- 任意选择器参数中的 `r=`、`rm=` → `WARN [LootPP-Legacy] SelectorParam`
- 实体到实体形式中目标选择器解析为零个实体 → WARN + `successCount = 0`

---

#### §2.4 `successCount` 定义汇总

| 命令 | `successCount` |
|---|---|
| `clear` | 实际移除的物品实例总数（对所有目标求和）|
| `effect`、`lppeffect`（施加效果）| 成功受到效果的目标数量 |
| `effect`、`lppeffect`（`clear`）| 效果被移除的目标数量 |
| `playsound` | 为其播放了音效的目标玩家数量 |
| `scoreboard players` | 成功更新的目标数量 |
| `execute` | 所有目标子命令 `successCount` 之和 |
| `testfor` | 匹配到的实体数量 |
| `summon`、`setblock`、`gamerule` | 成功为 `1`，失败为 `0` |
| `kill` | 被移除的实体数量 |
| `enchant` | 成功附魔的目标数量 |
| `tp`、`teleport` | 成功传送的目标数量 |
| `lppcondition` | 所执行分支的 `successCount` |

#### §2.5 统一失败 / 回退规则

| 条件 | 行为 |
|---|---|
| 未知动词 | `WARN [LootPP-Legacy] LegacyCommand unknown verb` + `successCount = 0` |
| 参数不足 | `WARN [LootPP-Legacy] LegacyCommand bad arity` + `successCount = 0` |
| 映射失败 | `WARN [LootPP-Legacy] LegacyMapping` + `successCount = 0` |

`LegacyCommandRunner` 不得向外传播任何异常。所有失败均通过 `successCount = 0` 与对应 WARN 表达。

#### §2.6 回归示例

**示例 1 — `clear` 作为条件：**

```
lppcondition clear @p[r=0] lootplusplus:astral.fairy 0 1
_if_true_ scoreboard players set @p[r=0] astralFairyCdwn 10
```

预期行为：
- 对 `r=0` 选择器参数发出 WARN（类型 `SelectorParam`）
- 对 meta `0` 发出 WARN（类型 `LegacyMeta`）
- `clear` 移除 1 个物品 → `successCount = 1`
- `1 > 0` → 执行 then 分支：`scoreboard players set @p[r=0] astralFairyCdwn 10`

---

**示例 2 — 带半径选择器的 `execute`：**

```
execute @e[r=3] ~ ~ ~ effect @e[r=3] potioncore:perplexity 10 10 true
```

预期行为：
- 对外层选择器中的 `r=3` 发出 WARN（类型 `SelectorParam`）
- `execute` 遍历半径 3 内的每个实体
- 对每个此类实体：内层 `effect` 对以该实体为原点、半径 3 内的所有实体施加效果
- `successCount` = 所有外层目标各自内层 `effect` `successCount` 之总和
