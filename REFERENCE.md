下面按你的要求做三件事：

1. **把开发需要的“其他参考/资料点”列清单**（你现在已有 structure + parser，但真正落地还缺这些）
2. **先“补一块”到设计里**：给出 1.20.1 Fabric 端的 **Hook 矩阵 + 执行顺序/确定性 + WARN 规范**（这是把 parser/structure 落到可跑的关键拼图）
3. **最后给 3 个“真实 zip 行”的解析+执行示例**（用你上传的 Astral/Plural 包里的行）

---

## 1) 开发所需的其他参考（你还会频繁查的）

### A. Fabric 1.20.1 事件与 API（落地 Hook 的依据）

* **事件系统总览/回调接口**：Fabric 文档（中文）对事件/回调的解释与注册方式。 ([Fabric Documentation][1])
* **Tick（服务器 tick）**：`ServerTickEvents` Javadoc（决定你 tick 扫描放哪一段）。 ([maven.fabricmc.net][2])
* **右键物品**：`UseItemCallback` Javadoc（对应 Loot++ 的 `right_click` / `command_right_click`）。 ([maven.fabricmc.net][3])
* **攻击实体**：`AttackEntityCallback` Javadoc（对应 `hitting_entity_to_entity` / command 版本）。 ([maven.fabricmc.net][4])
* **战利品表修改**：Fabric Wiki 的 `LootTableEvents.MODIFY` 教程（对应 Loot++ 的 chest_content / 方块掉落注入路线之一）。 ([wiki.fabricmc.net][5])
* **世界生成 Feature**：Fabric Wiki “Adding Features”（以及你要做 surface/underground 的放置特征）。 ([wiki.fabricmc.net][6])

### B. 外部 zip 作为资源包（不改 zip 的关键）

* Yarn 的 `ResourcePackProvider` 接口 Javadoc（你需要把 `assets/lootplusplus/**` 注入客户端资源包列表）。 ([maven.fabricmc.net][7])

### C. 反编译/验证 JAR 行为（你要长期做“对照测试”）

* 反编译器：CFR / Quiltflower / Fernflower（用于核对 split/limit、默认值、钳制、怪异 bug 行为）
* Bytecode 工具：ASMifier / Bytecode Viewer（遇到反编译误差时对照字节码）
* 建议做一个 **“JAR 行为 golden tests”**：把你解析器输出（AST/Rule）和 JAR（或你自己写的 1.8.9 仿真解析器）输出对比，确保 1:1。

### D. 迁移映射（legacy 必 WARN，但仍要能跑）

* 1.8 → 1.20.1 的 **ID/音效/粒子/效果**映射表：你最好做成可热更新的 json（例如 `legacy_mappings/*.json`），并在运行中“遇到未知映射仍能降级但 WARN”。

---

## 2) 补一块：1.20.1 Fabric 的 Hook 矩阵 + 执行顺序 + WARN 规范

> 这块是把你已有的 **structure + parser** 接到 “真的能跑并且尽量 1:1” 的关键拼图。

### 2.1 Hook 矩阵（触发器 → Fabric Hook → 执行上下文）

（括号内是你 runtime/context 里应该提供给命令/效果/掉落的上下文）

* `held` / `in_inventory` / `wearing_armour` / `standing_on_block` / `inside_block`
  → **ServerTickEvents.END_SERVER_TICK**：每 tick（或可配置每 N tick）扫描在线玩家。 ([maven.fabricmc.net][2])
  上下文：`(server, world, player, playerPos, random, SourceLoc, packId)`

* `right_click`
  → **UseItemCallback**：右键回调触发一次。 ([maven.fabricmc.net][3])
  上下文：`(player, hand, itemStack, world, pos=playerPos)`

* `hitting_entity_to_entity`
  → **AttackEntityCallback** +（必要时 mixin/二次确认“是否造成有效伤害”） ([maven.fabricmc.net][4])
  上下文：`(attacker, target, weaponStack, world, pos=targetPos)`

* `digging_block`
  → Fabric 的 block break 事件（若 API 不足就 mixin 到 break 流程末端）
  上下文：`(player, pos, state, toolStack, world)`

* `block_drops` / `entity_drops` / `chest_content`
  优先路线：**LootTableEvents.MODIFY** 注入 loot table（兼容性最好）。 ([wiki.fabricmc.net][5])
  备用路线：mixin 到 drop 生成点（用于复刻一些 Loot++ 的“非战利品表语义”）。

* `world_gen surface/underground`
  → Feature/PlacedFeature 注入（Fabric wiki 的 feature 教程路线）。 ([wiki.fabricmc.net][6])

### 2.2 执行顺序（必须固定，才能接近 1:1）

建议把 tick 扫描顺序写死（并保持稳定迭代顺序）：

1. `held`（主手）
2. `wearing_armour`（四件 + set bonus）
3. `in_inventory`（背包扫描）
4. `standing_on_block`
5. `inside_block`

原因：你 Astral 包里有大量 `command_in_inventory` 链式 lppcondition；不同顺序会改变“先 clear 再 effect”的可见行为。

### 2.3 确定性（Random 的选取）

Loot++ 1.8.9 时代很多地方用 `world.rand` / `Random`，你在 1.20.1 可以：

* tick 触发：使用 `world.random`（或 `player.getRandom()`）但**不要每次 new Random**
* 掉落组权重：固定用同一个 RNG（同 tick 内的多条 rule 按顺序 consume）

这样你的“抽权重”在同样的世界状态下更接近 1.8 行为。

### 2.4 “Legacy 适配必须 WARN”规范（你要求的硬约束）

做成统一格式（并支持 warnOnce 防刷屏）：

* **触发 WARN 的最小集合（你这两包已经出现）**

    * 旧选择器参数：`r=`, `score_x_min=`（Astral fairy 行里大量出现）
    * 旧音效名：`random.levelup`, `mob.wither.death`
    * 旧药水/效果名：`instant_health`, `instant_damage`（Plural thrown 行出现）
    * 目录别名：`config/enity_drops`（拼写错误容错）
    * meta/wildcard：`-1`、`32767`、以及任何带 meta 的 key

* **WARN 输出模板**

  ```
  [WARN] [LootPP-Legacy] <Type> <WhatChangedOrAssumed> @ <zip>:<innerPath>:<line>
  ```

---

## 3) 示例（真实 zip 行 → 解析结果 → 执行路径 → 你应该打出的 WARN）

下面示例都来自你上传的 zip 文件内容（我直接读出来的行）。

---

## 4) 运行配置与 parser args（新增）

运行时配置文件：

* 路径：`.minecraft/config/relootplusplus.json`
* 默认会自动生成（若不存在）

字段（示例）：

```json
{
  "dryRun": false,
  "exportReports": true,
  "exportRawLines": true,
  "exportDir": "logs/re_lpp",
  "extraAddonDirs": [],
  "duplicateStrategy": "suffix"
}
```

支持用系统属性/环境变量覆盖（等价于“parser args”）：

* `-Drelootplusplus.dryRun=true` / `RELOOTPLUSPLUS_DRY_RUN=true`
* `-Drelootplusplus.exportReports=false` / `RELOOTPLUSPLUS_EXPORT_REPORTS=false`
* `-Drelootplusplus.exportRawLines=false` / `RELOOTPLUSPLUS_EXPORT_RAW_LINES=false`
* `-Drelootplusplus.exportDir=/abs/path` / `RELOOTPLUSPLUS_EXPORT_DIR=/abs/path`
* `-Drelootplusplus.extraAddonDirs=/path1,/path2` / `RELOOTPLUSPLUS_EXTRA_ADDON_DIRS=/path1,/path2`
* `-Drelootplusplus.duplicateStrategy=suffix|ignore` / `RELOOTPLUSPLUS_DUPLICATE_STRATEGY=suffix|ignore`

说明：

* `dryRun=true` 时 **只解析、不注册、不挂钩**。
* `exportReports=true` 时会把 WARN 明细 + 总结输出到 `.minecraft/logs/re_lpp/`。
* `duplicateStrategy`：重复 packId / blockId / itemId 的处理策略。
  * `suffix`：自动追加 `_2/_3/...`（默认）
  * `ignore`：跳过重复项并 WARN

### 示例 1：Plural 投掷物 + 分组掉落（`config/item_additions/thrown.txt`）

原始行（Plural）：

```text
plural_chaos_nuke_____§5§lPlural Chaos Ball_____false_____8.0_____1.0_____0.01_____0.0_____1.0_____c-1-effect @e[r=3] potioncore:perplexity 10 10 10%%%%%c-1-effect @e[r=3] potioncore:disorganization 10 10 10
```

**解析（按你 parser 规范）**

* `ThrownDef.id = "lootplusplus:plural_chaos_nuke"`（实际注册名策略你定，但要能被 `summon LuckyProjectile {...}` 引用）
* `display = "§5§lPlural Chaos Ball"`
* `shines=false, damage=8.0, velocity=1.0, gravity=0.01, inaccuracy=0.0, dropChance=1.0`
* drops：按 `%%%%%` 切为 **2 个 DropGroup**，每组 1 个 `CommandDrop(weight=1, cmd="effect @e[r=3] potioncore:...")`

**你必须输出的 WARN（至少这些）**

* `@e[r=3]`：旧选择器参数 `r=`
* `effect` 命令语义：旧版 `effect` 参数与 1.20.1 不同（你要用兼容层）
  示例日志：

```text
[WARN] [LootPP-Legacy] SelectorParam r=3 used @ plural...zip:config/item_additions/thrown.txt:?? 
[WARN] [LootPP-Legacy] LegacyEffectCommand syntax assumed @ plural...zip:config/item_additions/thrown.txt:??
```

**执行路径（impact 时）**

1. `ThrownItemEntity.onImpact()`
2. roll 掉落组（这里只有两个组，各自独立 roll；weight=1）
3. 对每个选中的 `CommandDrop`：

    * LegacyCommandRunner 执行 `effect @e[r=3] potioncore:perplexity 10 10 10`
    * successCount = 实际被影响的实体数量（供 lppcondition 用；这里没用到也建议你保持一致）

---

### 示例 2：Astral 背包触发命令链（`config/item_effects/command_in_inventory.txt`）

原始行（Astral）：

```text
lootplusplus:astral.fairy_____-1_____{}_____1.0_____lppcondition clear @p[r=0,score_astralHealth=2,score_astralHealth_min=1,score_astralFairyCdwn=0] lootplusplus:astral.fairy 0 1 _if_true_ lppcondition effect @p[r=0,score_astralHealth=2] instant_health 5 _if_true_ lppcondition playsound random.levelup @a[r=16] ~ ~ ~ 1.0 0.5 _if_true_ scoreboard players set @p[r=0] astralFairyCdwn 10
```

**解析**

* ItemKey：

    * `id="lootplusplus:astral.fairy"`
    * `meta=-1 → wildcard(32767)`（legacy）
    * `nbt="{}" → 无 NBT 过滤`
* probability=1.0
* command string：整段保留（不 trim/不重排），交给 `CommandChain` / `lppcondition` 解释器

**你必须输出的 WARN（至少这些）**

* meta=-1 wildcard
* `@p[r=0,...score_...]`：旧选择器 + 旧 scoreboard 参数
* `instant_health`：旧效果名
* `playsound random.levelup`：旧 sound id
  示例日志（建议 warnOnce 去重，但要确实出现过）：

```text
[WARN] [LootPP-Legacy] MetaWildcard -1 treated as ANY @ Astral...zip:config/item_effects/command_in_inventory.txt:??
[WARN] [LootPP-Legacy] SelectorParam r/score_* used @ Astral...zip:config/item_effects/command_in_inventory.txt:??
[WARN] [LootPP-Legacy] EffectName instant_health mapped @ Astral...zip:config/item_effects/command_in_inventory.txt:??
[WARN] [LootPP-Legacy] SoundId random.levelup mapped @ Astral...zip:config/item_effects/command_in_inventory.txt:??
```

**执行流程（每 tick）**

1. Tick 扫描玩家背包：发现包含 `lootplusplus:astral.fairy`
2. 命令引擎执行这条规则（prob=1.0）
3. `lppcondition`：

    * 先执行：`clear @p[...] lootplusplus:astral.fairy 0 1`
    * 得到 successCount（清掉了几个物品堆/或清掉成功的目标数）
    * successCount>0 → 执行 true 分支（嵌套 lppcondition）：

        * `effect @p[...] instant_health 5`
        * `playsound random.levelup @a[r=16] ~ ~ ~ 1.0 0.5`
        * `scoreboard players set @p[r=0] astralFairyCdwn 10`

> 这就是为什么你需要“旧选择器 + scoreboard 条件 + 旧命令兼容层”，否则 Astral 这类包根本跑不出 1:1。

---

### 示例 3：world_gen surface 放置 command_trigger_block（Plural）

原始行（Plural）：

```text
lootplusplus:command_trigger_block_____0_____{CommandList:["playsound @a mob.wither.death","gamerule commandBlockOutput false","setblock ~ ~ ~ lucky:lucky_block_plural"]}_____false_____1.0_____1_____1_____1_____0_____255_____-_____-_____-_____ground-grass-sand-rock_____-_____-_____-_____-_____-_____- 
```

**解析**

* 放置方块：`lootplusplus:command_trigger_block`
* NBT：含 `CommandList`（字符串列表）
* chance=1.0 / tries=1 / group=1 / height 0..255
* 地表材质白名单：`ground-grass-sand-rock`（legacy 的 material 体系，需要你在 1.20.1 做映射并 WARN）

**执行路径**

* 世界生成阶段：Feature 放置 → 生成该方块并把 NBT 写入 BlockEntity
* 方块触发时机（你需要定义并复刻一致性）：

    * 最接近 1.8 的做法：**onPlaced / scheduled tick / 玩家邻近触发**（取你从 JAR 验到的行为；建议先实现“放置后立刻执行一次”作为 MVP）

**必须 WARN**

* `mob.wither.death`（旧 sound id）
* “material beneath whitelist” 旧体系（1.20.1 没这个概念）

---

如果你下一步要进入“能写代码的阶段”，我建议你下一条让我输出两份东西（不需要你再提供文件）：

1. **LegacySelectorParser 规范**：把 `@p[r=0,score_x=...,score_x_min=...]` 在 1.20.1 怎么解析成实体集合（并定义 successCount）
2. **LegacyCommandRunner 子集语义表**：对你包里已出现的命令（clear/effect/playsound/scoreboard/execute/testfor/summon/setblock/kill）逐条定义输入→输出→successCount

这样你就能直接写引擎并用上面这三个示例做第一批回归测试。

[1]: https://docs.fabricmc.net/zh_cn/develop/events?utm_source=chatgpt.com "事件 | Fabric 文档"
[2]: https://maven.fabricmc.net/docs/fabric-api-0.92.0%2B1.20.1/net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents.html?utm_source=chatgpt.com "ServerTickEvents (fabric-api 0.92.0+1.20.1 API)"
[3]: https://maven.fabricmc.net/docs/fabric-api-0.87.0%2B1.20.1/net/fabricmc/fabric/api/event/player/UseItemCallback.html?utm_source=chatgpt.com "UseItemCallback (fabric-api 0.87.0+1.20.1 API)"
[4]: https://maven.fabricmc.net/docs/fabric-api-0.88.1%2B1.20.1/net/fabricmc/fabric/api/event/player/AttackEntityCallback.html?utm_source=chatgpt.com "AttackEntityCallback (fabric-api 0.88.1+1.20.1 API)"
[5]: https://wiki.fabricmc.net/tutorial%3Aadding_to_loot_tables?utm_source=chatgpt.com "Adding items to existing loot tables [Fabric Wiki]"
[6]: https://wiki.fabricmc.net/tutorial%3Afeatures?utm_source=chatgpt.com "Adding Features [Fabric Wiki]"
[7]: https://maven.fabricmc.net/docs/yarn-1.20.1-rc1%2Bbuild.1/net/minecraft/resource/ResourcePackProvider.html?utm_source=chatgpt.com "ResourcePackProvider (yarn 1.20.1-rc1+build.1 API)"
