# Re-LootPlusPlus — Legacy Selector & Command Semantics

> **This is the Loot++ 1.8.9 legacy selector and command runner spec.**
> It defines the precise semantics (inputs → side effects → successCount) for the 1.8 command subset
> and the legacy `@p/@a/@r/@e` selector syntax with old parameters (`r=`, `rm=`, `score_*`, `c=`, etc.).
>
> **Implementation classes:**
> - `legacy/selector/LegacySelectorParser.java` — parses and evaluates legacy selectors
> - `legacy/selector/SelectorContext.java` — evaluation context (world, origin, random)
> - `command/LegacyCommandRunner.java` — interprets the 1.8 command subset
> - `command/exec/CommandChain.java` — splits on top-level `;`; warns on `&&`/`||`
> - `command/exec/ExecResult.java` — carries `successCount`
>
> All legacy selector parameters and remapped command behaviors must call
> `LegacyWarnReporter.warn()` or `warnOnce()`. Never silent.
>
> ---
>
下面给你两份**可直接开写**的规格：

1. **LegacySelectorParser 规范**（把 1.8 选择器 + `r=`/`score_*` 等老参数在 1.20.1 解释成实体集合）
2. **LegacyCommandRunner 子集语义表**（按你这两包已经用到的命令：`clear/effect/playsound/scoreboard/execute/testfor/summon/setblock/kill/enchant/gamerule`，定义“接受的输入→副作用→successCount”）

并且：所有“老语法/老名字/自动映射/降级”都规定**必须 WARN**（可 warnOnce 去重，但不能静默）。

---

## 1) LegacySelectorParser（1.8 选择器解释器）规范

### 1.1 输入与输出

* **输入**：`selectorString`（如 `@p[r=0,score_x_min=1]`），`ExecContext ctx`（包含 world、originPos、sender、server）
* **输出**：`List<Entity> targets`（稳定顺序），并提供 `int matchedCount = targets.size()`
* **失败策略**：解析失败 → `WARN` + 返回空列表（不抛异常，避免一行坏命令炸掉整包）

### 1.2 语法（EBNF）

```ebnf
selector      := at_kind [ selector_args ] ;
at_kind       := "@p" | "@a" | "@r" | "@e" ;
selector_args := "[" arg ( "," arg )* "]" ;

arg           := key "=" value ;
key           := ( letter | "_" ) ( letter | digit | "_" )* ;
value         := raw_value ;
raw_value     := (any char except ',' and ']')* ;  // 不支持引号转义；与 1.8 行为接近
```

> 说明：1.8 的选择器参数值通常不需要引号；pack 里也基本不出现带逗号的值。你做严格版就按上面切。

### 1.3 支持的参数键（建议“覆盖常见 luckyblock 包”）

> 你这两包用到：`r`, `score_<obj>`, `score_<obj>_min`。下面是建议全覆盖集合。

**坐标/范围类**

* `x`, `y`, `z`：选择器基准点（默认使用 `ctx.originPos`）
* `r`：最大半径（包含边界）
* `rm`：最小半径（包含边界）
* `dx`, `dy`, `dz`：轴对齐盒尺寸（从 `(x,y,z)` 到 `(x+dx, y+dy, z+dz)`，允许负值；用 min/max 归一）

**数量/排序类**

* `c`：数量限制

    * `c > 0`：取前 c 个
    * `c < 0`：取后 |c| 个（老包偶尔用，建议支持；不支持也要 WARN）
* `@p` 默认 `c=1`（最近玩家）
* `@r` 默认 `c=1`（随机玩家）
* `@a/@e` 默认不限制

**类型/名称/队伍**

* `type`：实体类型（支持 `type=!minecraft:cow` 形式；遇到 `!` 必须 WARN 但仍兼容）
* `name`：实体名（精确匹配 `Entity.getName().getString()`；支持 `!`，同上 WARN）
* `team`：记分板队伍名（玩家/实体均可，支持 `!`）

**玩家属性**

* `m`：游戏模式（0/1/2/3；非玩家自动过滤掉；若出现 `!` 同上 WARN）
* `l` / `lm`：经验等级上/下限（玩家）

**计分板（重点）**

* `score_<objective>`：要求分数 `<= value`（1.8 语义里相当于上界）
* `score_<objective>_min`：要求分数 `>= value`
* 若 objective 不存在：在 1.8 下通常会导致“不匹配任何实体”；你这里建议：

    * `WARN [LegacyScore] objective missing -> no match`，并让该过滤把所有候选过滤为空（最接近 pack 预期）

### 1.4 选择器求值算法（固定顺序，保证稳定性）

**步骤 0：WARN 探测（必须）**
只要 selector 使用了任一 legacy 参数或特性（比如 `r/rm/score_*`、`c<0`、`type=!` 等），都要 `WARN`（可 warnOnce）。

**步骤 1：确定候选集**

* `@a/@p/@r`：候选=当前世界所有玩家（含旁观者，后续 `m=`再过滤）
* `@e`：候选=世界所有实体（含玩家）

**步骤 2：确定基准点 origin**

* 默认 `origin = ctx.originPos`
* 若 args 包含 `x/y/z` 任意一个：缺省的那两个仍用 ctx.originPos 对应分量
* `dx/dy/dz` 盒过滤使用该 origin（x/y/z 作为盒起点）

**步骤 3：按固定顺序应用过滤器（不要换顺序）**

1. `type/name/team/m/l/lm`（这些不依赖坐标）
2. 盒过滤 `dx/dy/dz`（若提供）
3. 半径过滤 `rm/r`
4. scoreboard 过滤（`score_*_min`/`score_*`）

**步骤 4：排序与截取**

* `@p`：按距 origin 的距离升序；同距离按 `entityId` 升序（稳定）
* `@r`：从满足条件的玩家里做随机洗牌，再取前 c（使用 `ctx.random`；不要 new Random）
* `@a/@e`：按 `entityId` 升序（稳定）
* 应用 `c` 截取

### 1.5 坐标与距离定义

* 距离：用实体位置与 origin 的欧氏距离（建议平方比较）
* `r/rm` 边界包含：`rm <= dist <= r`
* `dx/dy/dz`：形成 AABB，包含边界

### 1.6 WARN 规范（强制）

当触发以下任一情况，必须 WARN（示例 Type 你可按实现定义）：

* 使用 legacy 参数：`r/rm/c/score_*` → `WARN [LootPP-Legacy] SelectorParam ...`
* 使用否定 `!` → `WARN [LootPP-Legacy] SelectorNegation ...`
* objective 不存在 → `WARN [LootPP-Legacy] ScoreObjectiveMissing ...`
* 解析失败/未知键 → `WARN [LootPP-Legacy] SelectorParse ...`

---

## 2) LegacyCommandRunner（子集语义 + successCount）

### 2.1 总体执行模型

* 输入：`commandString`（可能以 `/` 开头）、`ExecContext ctx`
* 输出：`ExecResult { int successCount }`
* 要求：

    1. **不依赖 Brigadier** 来解析 1.8 语法（避免 `/execute`、`/playsound`、旧选择器等差异）
    2. 命令执行必须是**可恢复的**：任何单条失败 → `WARN` + 返回 0，不抛异常
    3. 若命令触发 legacy 映射（旧 sound/effect/particle/实体名/方块名/metadata）→ **必须 WARN**

### 2.2 Token 解析（很关键，避免 NBT 吃空格）

* 先去掉前导 `/`，`trim`
* 取第一个空格前为 `verb`
* 对于 NBT（`{...}`）参数：使用“括号配对截取”，把剩余字符串中从第一个 `{` 开始到匹配的 `}` 作为一个整体 nbtToken（允许空格）
* 选择器 token（`@p[...]`）整体作为一个 token（中间不含空格）

> 这套规则足够覆盖你当前两包里的 `summon {...}` / `setblock ... {}` / `lppcondition ...` 场景。

---

## 2.3 命令语义表（你当前必须实现的集合）

下面每条都定义：**接受语法**、**执行语义**、**successCount**、**WARN 点**。

---

### A) `lppcondition`（内置）

**语法**

```text
lppcondition <condCommand...> _if_true_ <thenCommand...> [_if_false_ <elseCommand...>]
```

**语义**

1. 执行 `<condCommand...>` 得到 `k=successCount`
2. `k>0` 执行 then，否则执行 else（如存在）
3. 返回所执行分支的 successCount（1.8 Loot++ 更像这样；你也可以返回 cond 的 successCount，但要保持一致性，建议：**返回分支的**更符合“链式条件继续跑”的直觉）

**WARN**

* 若分隔符缺失/顺序不对 → `WARN [Legacy] lppcondition parse` 并返回 0

---

### B) `lppeffect`（内置）

**语法（建议覆盖）**

```text
lppeffect <targets> <effectId> <seconds> <amplifier> <showParticles>
lppeffect clear <targets> [effectId]
```

**语义**

* apply：对每个 target 施加效果（秒→tick）
* clear：移除目标的某个效果或全部效果

**successCount**

* apply：成功施加的目标数
* clear：成功移除（原本存在）的目标数

**WARN**

* effectId 为 legacy 数字/旧名 → `WARN [LegacyEffect]`（再映射）

---

### C) `clear`（1.8 兼容）

**你包里用法**

```text
clear @p[...] lootplusplus:astral.fairy 0 1
```

**接受语法（子集）**

```text
clear <targets> [itemId] [meta] [count]
```

* meta 可省略；count 可省略
* 若只写 `clear <targets>`：清空目标物品栏（谨慎，但很多包会用）

**语义**

* 对每个 target（玩家）移除指定物品（可含 meta；你在 1.20.1 meta 已扁平化，需要走 legacy 映射并 WARN）

**successCount（强烈建议这样定义）**

* 返回**实际移除的物品总数**（跨所有目标累加）

    * 这能完美支持 pack 用 `lppcondition clear ... _if_true_ ...` 判断“是否真的消耗掉一个道具”

**WARN**

* 使用 meta 或 meta 为 wildcard → `WARN [LegacyMeta]`
* itemId 旧名映射 → `WARN [LegacyItemId]`

---

### D) `effect`（1.8 兼容）

**你包里用法**

* `effect @p[...] instant_health 5`
* `effect @e[r=3] potioncore:perplexity 10 10 10`（看起来是 modded 扩展写法）

**接受语法（兼容两种）**

```text
effect <targets> <effectId|effectName|effectNumber> [seconds] [amplifier] [hideParticles]
effect <targets> clear [effectId]
```

* `hideParticles`：1.8 是 boolean（true/false），你包里有时写成数字（例如 `10 10 10` 那种其实更像是某些旧扩展格式），建议：

    * 若无法判定：按“seconds/amplifier”尽量 parse，剩余参数忽略并 WARN

**successCount**

* apply：成功施加的目标数
* clear：成功移除的目标数

**WARN**

* effect 为数字或旧名（`instant_health`）→ `WARN [LegacyEffect]`
* 选择器 legacy 参数（`r=`, `score_*`）由 selector 统一 WARN

---

### E) `playsound`（1.8 兼容）

**你包里用法**

* `playsound random.levelup @a[r=16] ~ ~ ~ 1.0 0.5`
* `playsound @a mob.wither.death`（在 CommandList 里）

**接受语法（兼容 1.8）**

```text
playsound <soundId> <targets> [x y z] [volume] [pitch] [minVolume]
```

**语义**

* 对每个目标玩家播放 sound
* 坐标默认 `ctx.originPos`（或目标位置，任选一个但要固定；建议：若提供 x y z 用之，否则用 ctx.originPos）

**successCount**

* 成功播放的目标玩家数

**WARN**

* soundId 为旧 id（如 `random.levelup`, `mob.wither.death`）→ `WARN [LegacySound]` 并映射

---

### F) `scoreboard`（只实现你包里用到的 players set）

**你包里用法**

* `scoreboard players set @p[r=0] astralFairyCdwn 10`

**接受语法（子集）**

```text
scoreboard players set <targets> <objective> <score>
scoreboard players add <targets> <objective> <delta>        (建议顺手做)
scoreboard players remove <targets> <objective> <delta>     (建议顺手做)
```

**语义**

* 若 objective 不存在：1.8 下通常会失败；为了 1:1，建议：

    * `WARN [LegacyScore] objective missing` + successCount=0（不自动创建）
* 对每个目标写入分数

**successCount**

* 成功更新的目标数

**WARN**

* selector legacy 参数由 selector WARN

---

### G) `execute`（1.8 兼容）

**接受语法（严格按 1.8 形态）**

```text
execute <targets> <x> <y> <z> <subCommand...>
```

**语义**

* 对每个 target：

    * 构造子上下文 `ctx2`：

        * sender = target（若需要）
        * originPos = 解析后的 x/y/z（支持 `~` 相对；相对基于 target 位置或原 ctx.originPos——1.8 是基于执行者位置，建议基于 target）
    * 执行 `<subCommand...>` 并累加结果

**successCount**

* `sum(subResult.successCount)`（跨所有 target 累加）

**WARN**

* 使用旧坐标/旧子命令仍按各自命令 WARN
* 若 subCommand 为空 → WARN + 0

---

### H) `testfor`（1.8 兼容）

**接受语法（子集）**

```text
testfor <targets>
testfor <targets> <nbtJson>   (可选，遇到就支持；宽松 NBT 并 WARN)
```

**语义**

* 仅测试选择器是否命中（以及可选 NBT contains-match）

**successCount**

* 命中实体数量（0 表示失败）

**WARN**

* 使用 NBT（尤其宽松解析/规范化）→ `WARN [LegacyNBT]`

---

### I) `summon`（1.8 兼容）

**接受语法（子集）**

```text
summon <entityId> [x y z] [nbtJson]
```

**语义**

* entityId 允许旧名（如 `Cow`），需要映射并 WARN
* 若 nbtJson 存在：宽松解析（并 WARN 若发生规范化/修复）
* 生成一个实体并应用 NBT（注意 1.20.1 限制：有些字段不可直接写，失败要 WARN）

**successCount**

* 成功生成返回 1，否则 0

**WARN**

* 旧实体名/旧 NBT → `WARN [LegacyEntityId]/[LegacyNBT]`

---

### J) `setblock`（1.8 兼容）

**你包里用法**

* `setblock ~ ~ ~ lucky:lucky_block_plural`

**接受语法（子集）**

```text
setblock <x> <y> <z> <blockId> [meta] [mode] [nbtJson]
```

* mode：`replace|destroy|keep`（若缺省 → replace）
* meta：遇到就 WARN（1.20.1 blockstate 扁平化）

**successCount**

* 成功放置（或按 mode 规则执行成功）→ 1，否则 0

**WARN**

* blockId 旧名/需要映射 → `WARN [LegacyBlockId]`
* meta 使用 → `WARN [LegacyMeta]`

---

### K) `kill`（1.8 兼容）

```text
kill <targets>
```

* successCount = 实际移除（死亡/删除）的实体数量
* WARN：无（除非选择器里有 legacy param，由 selector WARN）

---

### L) `enchant`（你 jar/pack 可能用）

```text
enchant <targets> <enchantId> [level]
```

* 只对玩家/手持物品可附魔的情形成功
* successCount = 成功附魔的目标数
* WARN：旧附魔名/数字 id → `WARN [LegacyEnchant]`

---

### M) `gamerule`（你 CommandList 里出现）

```text
gamerule <ruleName> <value>
```

* successCount = 1 成功设置，否则 0
* WARN：旧 ruleName（若遇到）→ `WARN [LegacyGamerule]`

---

## 2.4 CommandRunner 的统一失败/降级规则（建议写死）

* 未知 verb：`WARN [LegacyCommand] unknown verb ...`，success=0
* 参数不足：`WARN [LegacyCommand] bad arity ...`，success=0
* 映射失败（sound/effect/entity/block/item）：`WARN [LegacyMapping] ...`，success=0（或尽量退化到“啥也没做”）

---

## 2.5 你可以直接拿来做回归测试的两个例子（和你包一致）

### (1) `clear` 作为条件

输入：

```text
lppcondition clear @p[r=0] lootplusplus:astral.fairy 0 1 _if_true_ scoreboard players set @p[r=0] astralFairyCdwn 10
```

预期：

* `clear` 移除 1 个 fairy → successCount=1 → then 分支执行
* `WARN` 至少包含：selector `r=0`、meta `0`（即使是 0 也属于 legacy meta 使用）

### (2) `execute` 迭代 + 半径选择

输入：

```text
execute @e[r=3] ~ ~ ~ effect @e[r=3] potioncore:perplexity 10 10 true
```

预期：

* selector WARN（r=3）
* execute 对每个实体重复执行 effect（会叠加很多次，和 1.8 一样“很凶”）
* successCount = 所有子 effect 成功数的总和（会很大）

---

如果你希望我把这两块再“落到接口级别”，我可以下一条给你：

* `SelectorQuery` / `SelectorResult` / `ExecContext` / `ExecResult` 的字段定义
* 以及每条命令的 **最小实现伪代码**（尤其是 `clear/effect/execute/lppcondition` 这四个最关键的）。
