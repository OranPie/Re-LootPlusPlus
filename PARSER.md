# Re-LootPlusPlus — Config Parser Spec

> **This is the Loot++ 1.8.9 behavioral spec** extracted from JAR bytecode analysis.
> It defines the grammar and split/default/clamp rules that this mod implements against.
> All formats described here are implemented in `config/loader/` and `config/parse/`.
>
> **Implementation classes:**
> - `config/parse/Splitter.java` — Java `String.split(regex, 0)` semantics
> - `config/parse/LineReader.java` — comment/blank-line filtering with SourceLoc
> - `config/parse/NumberParser.java` — int/float/bool with WARN on parse failure
> - `config/loader/*Loader.java` — one loader per config domain
>
> **Deferred / partial:** `fishing_amounts` grammar has edge cases not fully verified.
>
> ---
>
下面是我**直接从你给的 `Loot++-1.7.1_for_1.8.9.jar` 的字节码行为**里整理出来的「严格 parser 语法」（也就是你 1.20.1 Fabric 端要 1:1 复现的输入语法/分隔规则/默认值/边界处理）。我会尽量用 **EBNF + “JAR 实际 split/limit/默认值/钳制规则”**描述，避免“凭印象猜”。

> ✅ **重要：你要求 legacy 适配必须 WARN**
> 下面凡是涉及 1.8.9 旧概念（metadata=-1/any、数字 potion id、Block/Item 的旧名、NBT JSON、any/all 哨兵等），你在 1.20.1 解析时都应当 **`LOGGER.warn("[Legacy] ...")`** 显式输出（即使继续兼容执行）。

---

# 0) 全局分隔/注释/空行规则（JAR 真实行为）

## 0.1 读文件

* 所有 extra `.txt` 文件都是**逐行读取**，原样放进 list（不会预先 trim、不会预先过滤）。

## 0.2 注释与空行（绝大多数 loader 共通）

* **空行**：`line.equals("")` → 忽略
* **注释行**：仅当 `line.length()>0 && line.charAt(0)=='#'` 才视为注释
  ⚠️ **注意：前面有空格的 `  #comment` 不算注释**（JAR 不 trim 再判断）
* **兼容扩展**（非 1.8 JAR 原生）：很多 addon 用 `//` 注释，建议你在 1.20.1 端额外忽略 `//` 行。

注释行通常会被 loader **直接跳过**，或者走到 `notifyWrongNumberOfParts(comment=true)` 从而**不输出错误**（效果等模块就是这么实现的）。

## 0.3 split 的严格性

* JAR 使用的是 **Java `String.split(regex)`**

    * `line.split("_____")`（5 个下划线）
    * `segment.split("%%%%%")`（5 个百分号）
    * `segment.split("-")` 或 `segment.split("-", limit)`
* **关键点**：`String.split(regex)` 默认 `limit=0` → **会丢弃末尾的空字段**
  例如：`a_____b_____` split 后只得到 `["a","b"]`（末尾空字段被丢弃）
* 因此你 1:1 复现时应当使用同等语义的 split（Fabric 端建议自己写 split，模拟 Java 行为）。

---

# 1) DropInfo（掉落条目）严格语法（JAR：`LootPPHelper.getDropInfo`）

掉落条目出现在 `block_drops/adding.txt` 与 `entity_drops/adding.txt` 的 “drop group” 内。

## 1.1 drop group 外层语法

```ebnf
drop_group := drop_entry ("%%%%%" drop_entry)* ;
drop_entry := type_char "-" payload ;
type_char := "i" | "e" | "c" ;
```

> JAR 取 `drop_entry` 的方式是：只找**第一个 `-`**，`type_char = s.charAt(0)`，`payload = s.substring(firstDash+1)`
> ✅ 所以 payload 里面允许再出现 `-`（不会被外层切碎）。

---

## 1.2 `i-`（Item 掉落）payload 语法（JAR：`split("-", 6)`）

```ebnf
item_payload :=
  item_id "-" min_count
  [ "-" max_count
    [ "-" weight
      [ "-" metadata
        [ "-" nbt_json ]
      ]
    ]
  ] ;
```

* JAR 使用：`payload.split("-", 6)`（**最多 6 段**，最后一段可包含额外 `-`）
* 默认值（当字段缺失时）：

    * `max_count = min_count`
    * `weight = 1`
    * `metadata = 0`
    * `nbt = null`
* 数值钳制（JAR 实际）：

    * `min_count < 0 → 0`
    * `max_count < min_count → max_count = min_count`
    * `weight <= 0 → 1`
    * `metadata < 0 → 0`（⚠️ **这里不会实现 “-1 any”**，负值直接被钳到 0）
* NBT：

    * 若提供 `nbt_json` → `JsonToNBT.parse(nbt_json)`（**不自动 trim**？在多数路径会 trim；掉落这里实际是直接 parse）
    * 失败会 `notifyNBT(...)`，并创建空 tag（行为偏容错）
* item_id：走 `Item.getByNameOrId(string)`（1.8.9 语义）

✅ **Legacy WARN 建议**

* 若 `item_id` 含旧命名/旧域、或 metadata 概念映射到 1.20.1 的 components → `WARN`

---

## 1.3 `e-`（Entity 掉落）payload 语法（JAR：`split("-", 3)`）

```ebnf
entity_payload := entity_id [ "-" weight ] [ "-" nbt_json ] ;
```

* JAR：`payload.split("-", 3)`
* 默认：

    * `weight = 1`
    * `nbt = "{}"`（最终会变 compound）
* NBT：parse 失败会 warn 并变成空 compound
* **强制覆盖**：JAR 会对 NBT `tag.setString("id", entity_id)`（无论 NBT 内写了什么 id）

✅ **Legacy WARN 建议**

* 1.20.1 entity id 迁移（如旧 mob 名称）→ WARN

---

## 1.4 `c-`（Command 掉落）payload 语法（JAR：`split("-", 2)`）

```ebnf
command_payload := weight "-" command_string ;
```

* JAR：`payload.split("-", 2)`
  ✅ command_string 中允许再出现 `-`
* 默认/钳制：

    * `weight <= 0 → 1`

✅ **Legacy WARN 建议**

* 1.8 命令 → 1.20 命令差异非常大（如 `/summon` NBT、`/execute` 结构），必须 WARN 并提供兼容层。

---

# 2) `config/block_drops/*.txt`

文件夹：`config/block_drops/`

* `removing.txt`
* `adding.txt`

---

## 2.1 removing.txt（移除方块掉落）严格语法（JAR：`split("_____")`）

```ebnf
line :=
  block_id "_____" block_meta "_____" item_id
  [ "_____" item_meta ]
  [ "_____" nbt_json ] ;
```

字段规则（JAR 实际）：

* `block_meta`：

    * int；若 `<0` → **32767**（wildcard ANY）
* `item_id`：

    * 若 `equalsIgnoreCase("any") || equalsIgnoreCase("all")`
      → 特殊 dummy item（用来匹配“所有 item”）
* `item_meta`：

    * 缺省 `-1`
    * `<0` → **32767**（wildcard ANY）
* `nbt_json`：

    * 缺省 `"{}"`
    * 若为 `""` 或 `"{}"` → 视为无 NBT 过滤
    * 否则 `trim()` 后 JsonToNBT.parse

✅ **Legacy WARN 必须点**

* `block_meta`/`item_meta` wildcard（32767）与 1.20 blockstate/item components 映射 → WARN

---

## 2.2 adding.txt（添加方块掉落）严格语法（JAR：先 split("_____") 再 split("-")）

行总体：

```ebnf
line := header "_____" drop_group ("_____" drop_group)* ;
```

header（JAR：`header.split("-")`，要求至少 5 段）：

```ebnf
header :=
  block_id "-" rarity "-" only_player_mined "-" drop_with_silk "-" affected_by_fortune
  [ "-" block_meta ] ;
```

字段规则（JAR 实际）：

* rarity：`Float.valueOf`（⚠️ JAR 里有 clamp 调用但**结果被 pop 掉**，等价于“不 clamp”，建议你也保持不 clamp 才 1:1）
* only_player_mined / drop_with_silk / affected_by_fortune：

    * `Boolean.valueOf(string)`
    * 只有 `"true"`（忽略大小写）为 true，其它都 false（例如 `"1"` 也是 false）
* block_meta：

    * 缺省 `-1`
    * `<0` → **32767**（wildcard ANY）
* drop_group：

    * 若 group 字符串 `equals("")` → 跳过（允许出现连续 `_____`）
    * group 内用 `%%%%%` 切多个 drop_entry（见第 1 节）

✅ **Legacy WARN 必须点**

* block id + meta → 1.20 blockstate 映射（特别是大量 luckyblock pack 用 meta）→ WARN

---

# 3) `config/entity_drops/*.txt`（结构与 block_drops 类似）

文件夹：`config/entity_drops/`

* `removing.txt`
* `adding.txt`

> removing 语法（从 JAR 字符串常量与结构推断）与 block removing 类似：
> `entityName_____entityNBT_____itemName_____meta(optional)_____NBT(optional)`
> 由于我这条回复里优先把你最需要的 block_drops / effects / chest / fishing 详细写完，entity_drops 的 removing/add 的完整 bytecode 展开我可以下一条补齐（但 adding 内的 drop_group 语法与第 1 节完全一致）。

---

# 4) `config/item_effects/*.txt`（JAR：`ConfigLoaderEffects` 私有解析函数）

目录：`config/item_effects/`
Keys（JAR 构造器里注册的 extras 名称）：

* `held`
* `in_inventory`
* `hitting_entity`
* `hitting_entity_to_entity`
* `wearing_armour`
* `blocks_in_inventory`
* `standing_on_block`
* `breaking_block`
* `near_entity`
* `near_entity_to_entity`
* `command_held`
* `command_in_inventory`
* `command_hitting_entity`
* `command_wearing_armour`
* `command_blocks_in_inventory`
* `command_standing_on_block`
* `command_breaking_block`
* `command_near_entity`

---

## 4.1 物品 Potion 效果（held / in_inventory / …）严格语法

JAR：`loadPotionEffect(line, allowSetBonus, map, ...)`

```ebnf
line :=
  item_name "_____" item_meta "_____" item_nbt "_____" potion_id "_____" duration "_____"
  amplifier "_____" probability "_____" particle_type
  [ "_____" extra_armour1 "_____" extra_armour2 "_____" extra_armour3 ... ] ;
```

关键行为（JAR 实际）：

* 最少 **8 段**，否则 `notifyWrongNumberOfParts`
* 注释/空行：

    * `""` 直接 return
    * `#...` 会被当 comment=true，后续错误输出会被 suppress（等价忽略）
* item_meta：

    * int；`<0 → 32767`（ANY）
* item_name：

    * `"any"` → dummy item（匹配任意物品）
* item_nbt：

    * `""` 或 `"{}"` → 不要求 NBT
    * 否则 `trim()` 后 JsonToNBT.parse
* potion_id：

    * 先按资源名找（1.8 的 `Potion.getPotionFromResourceLocation`）
    * 找不到再尝试按数字 id（`Potion.potionTypes[id]`）
* duration / amplifier / probability：

    * 数字解析失败 → notifyNumber，并继续使用默认值（duration=10, amplifier=0, prob=1.0）

### set bonus（只在 `wearing_armour` 那组启用）

* JAR 只读取 `parts[8..10]` 三个额外物品名（最多 3 个）
* 若某个额外 armour item 不存在 → notifyNonexistant，但不会终止整行

✅ **Legacy WARN 必须点**

* potion 数字 id → 1.20 effect id → WARN
* item_meta wildcard → components → WARN

---

## 4.2 物品 Command 效果严格语法（JAR：`loadCommandEffect`）

```ebnf
line :=
  item_name "_____" item_meta "_____" item_nbt "_____" probability "_____" command ;
```

* 最少 **5 段**
* item_meta `<0 → 32767`
* item_nbt 规则同上
* probability float 解析失败 → notifyNumber，prob 默认 1.0

⚠️ **JAR 的一个“很关键的怪异点/bug”**（你要 1:1 必须复现）：

* `wearing_armour` 的 command set-bonus：
  JAR 居然也从 **索引 8..10** 读取额外 armour，而不是索引 5..7。
  也就是说除非你在语法上人为塞出 3 个占位字段，否则 command set-bonus 基本不可用。

✅ 你做 port 时可以：

* **严格复现**（保持这个索引 8..10）
* 但同时在 1.20.1 端做“兼容层”：若用户写成更合理的“直接跟在 command 后面”，你可以兼容，但必须 `WARN`（符合你要求）。

---

## 4.3 Block Potion/Command Effects（blocks_in_inventory / standing_on_block / …）

### Block Potion（JAR：`loadPotionEffectBlock`）

```ebnf
line :=
  block_name "_____" block_meta "_____" potion_id "_____" duration "_____"
  amplifier "_____" probability "_____" particle_type ;
```

* 最少 7 段
* block_meta `<0 → 32767`
* block_name `"any"` → 特殊 block `blockCommandBlockTrigger`（匹配任意方块）
* block_name 若解析到 air：

    * 仅当输入字符串真的是 `"air"` 或 `"minecraft:air"` 才允许
    * 否则当做 nonexistant → notifyNonexistant

### Block Command（JAR：`loadCommandEffectBlock`）

```ebnf
line := block_name "_____" block_meta "_____" probability "_____" command ;
```

* 最少 4 段
* 其余规则同上

---

# 5) `config/chest_content/*.txt`（JAR：`ConfigLoaderChestLoot`）

目录：`config/chest_content/`
extras keys：

* `chest_loot`（JAR 内部对应 “custom chest type additions”）
* `chest_amounts`（JAR 内部对应 “min_max_amounts”）

---

## 5.1 chest_amounts.txt（JAR：`split("-")` 且必须 3 段）

```ebnf
line := chest_type "-" min_items "-" max_items ;
```

* `min_items <0 → 0`
* `max_items < min_items → max_items = min_items`

✅ **Legacy WARN**

* chest_type（1.8 Forge ChestGenHooks 类型名）到 1.20 loot table 的映射 → WARN（非常关键）

---

## 5.2 chest_loot.txt（JAR：`split("-", 7)` 至少 5 段）

```ebnf
line :=
  chest_type "-" item_name "-" min "-" max "-" weight
  [ "-" metadata ]
  [ "-" nbt_json ] ;
```

* split：`split("-", 7)`（NBT 允许包含 `-`）
* 最少 5 段
* metadata 缺省 `0`；`<0 → 0`
* nbt 缺省 `"{}"`；`""` 或 `"{}"` → 无 NBT；否则 `trim()` 后 JsonToNBT
* 数值钳制：

    * min<0→0
    * max<min→max=min
    * weight<0→0（注意 chest 用的是 `if(weight<0) weight=0`，不像 drop weight<=0→1）
* 若添加的 chest_type 在系统里 min/max 都是 0，JAR 会默认 setMin=3 setMax=9（给新箱子一个默认容量范围）

✅ **Legacy WARN**

* 1.8 chest_type → 1.20 loot_table id → WARN
* item_name 旧名 → WARN

---

# 6) `config/fishing_loot/*.txt`（JAR：`ConfigLoaderFishingLoot.loadNewFishingLoot`）

目录：`config/fishing_loot/`
extras keys：

* `fishing_loot`
* `fishing_amounts`（这部分我来不及把 bytecode 全段贴完，但 loot 条目语法已完整确定）

---

## 6.1 fishing_loot.txt（JAR：`split("-", 7)` 至少 5 段）

```ebnf
line :=
  item_name "-" stack_size "-" enchant_percent "-" enchanted "-" rarity
  [ "-" damage ]
  [ "-" nbt_json ] ;
```

* split：`split("-", 7)`（NBT 允许 `-`）
* 空行或 `#...` → 直接返回 null（忽略）
* 数值规则：

    * stack_size <1 → 1
    * enchant_percent <0 → 0
    * rarity <1 → 1
    * damage 缺省 0；damage<0 → 0
* enchanted：Boolean.valueOf（仅 "true" 为 true）
* nbt：`""` 或 `"{}"` → none，否则 trim+JsonToNBT
  NBT parse 失败 → notifyNBT 并返回 null（忽略该行）

✅ **Legacy WARN**

* fishing loot 体系 1.8 → 1.20 的 loot table / fishing loot pool → WARN

---

## 6.2 fishing_amounts.txt

这份文件在 JAR 内对应 `fishing_amounts` extras key。
我这条消息里**还没来得及把它的 `loadFishingLoot()` 中的解析分支完整展开**（因此不敢 100% 写死格式）。
但按 Loot++ 的结构，它非常可能与 chest_amounts 一样是：

```ebnf
line := loot_category "-" min "-" max ;
```

其中 category 大概率是 `junk/treasure/fish` 或类似命名，并分别控制三类渔获的抽取数量。

> 如果你希望我下一条把 fishing_amounts 的 bytecode 精确拆出来（保证 1:1），我可以继续沿用 jar 反编译逻辑把对应分支完整列出（你无需再提供文件）。

---

# 7) `config/world_gen/*.txt`（JAR：`ConfigLoaderWorldGen`）

目录：`config/world_gen/`
extras keys：

* `surface`
* `underground`

---

## 7.1 surface.txt（JAR：`split("_____")` 严格要求 **正好 20 段**）

```ebnf
line :=
  block_name "_____" block_meta "_____" block_nbt "_____" bonemeal
  "_____" chance_per_chunk "_____" tries_per_chunk "_____" group_size
  "_____" tries_per_group "_____" height_min "_____" height_max
  "_____" beneath_block_blacklist "_____" beneath_block_whitelist
  "_____" beneath_material_blacklist "_____" beneath_material_whitelist
  "_____" biome_blacklist "_____" biome_whitelist
  "_____" biome_type_blacklist "_____" biome_type_whitelist
  "_____" dimension_blacklist "_____" dimension_whitelist ;
```

* 段数必须 **==20**，多/少都算 wrong parts（JAR 用 `if arraylength != 20`）
* 其中大量 list 字段用 `"-"` 分割：

    * `field.split("-")`
    * 单独一个 `"-"` 在 Java split 下会得到空数组 → 表示“无列表条件”
* block_nbt：

    * `"{}"` → none
    * 否则 trim+JsonToNBT
* block_meta：按 JAR 逻辑是 int（是否支持 -1/32767 wildcard 取决于实现分支；你 port 时建议保留 wildcard 并 WARN）

✅ **Legacy WARN**

* dimension id / biome type 体系 1.8 → 1.20 完全不同 → WARN 必须有

---

## 7.2 underground.txt（JAR 常量显示为 19 段格式）

```ebnf
line :=
  block_name "_____" block_meta "_____" block_nbt
  "_____" chance_per_chunk "_____" tries_per_chunk
  "_____" vein_len_min "-" [vein_len_max]
  "_____" vein_thick_min "-" vein_thick_max
  "_____" height_min "_____" height_max
  "_____" block_blacklist "_____" block_whitelist
  "_____" beneath_material_blacklist "_____" beneath_material_whitelist
  "_____" biome_blacklist "_____" biome_whitelist
  "_____" biome_type_blacklist "_____" biome_type_whitelist
  "_____" dimension_blacklist "_____" dimension_whitelist ;
```

* `vein_len_max` 可选（JAR 字符串常量明确写了 optional）
* 其它 list 字段与 surface 相同用 `-` 分割

---

# 8) items/blocks additions（只给“严格格式”速查表）

你要移植大量 luckyblocks pack，很多 zip 里会带 `item_additions` / `block_additions`。
这两类我已经从 jar 中**完整提取到 file keys**，但在这一条里我先给“格式速查”，不把每一种的所有钳制细节展开（否则太长）。如果你需要，我下一条可以逐段把 bytecode 钳制/默认值也逐个列出来。

## 8.1 item_additions 下的 txt 文件名（JAR keys）

* `generic_items`
* `foods`
* `thrown`
* `bows`
* `guns`
* `multitools`
* `materials`
* `swords`
* `pickaxes`
* `axes`
* `shovels`
* `hoes`
* `helmets`
* `chestplates`
* `leggings`
* `boots`

每一行基本都是 `_____` 分隔，格式由 JAR comment 常量定义，例如：

* generic_items：

    * `<Item name>_____<Item display name>_____<Shines (true/false, optional)>`

## 8.2 block_additions 下的 txt 文件名（JAR keys）

* `blocks`
* `blocks_with_states`
* `glowing_blocks`
* `ores`
* `bonemeal_flowers`
* `flowers`
* `plants`
* `slabs`
* `stairs`
* `trapdoors`
* `pressure_plates`
* `buttons`
* `doors`
* `fences`
* `fence_gates`
* `walls`
* `panes`
* `colored_blocks`

---

# ✅ 你实现 1.20.1 Fabric parser 的建议（保证 1:1）

1. **按“文件类型”选择 split 策略**

    * `_____` 分段（effects / drops / worldgen / items / blocks）
    * `-` 分段（chest_loot / chest_amounts / fishing_loot）
2. **严格模拟 Java split 行为**（尤其是“末尾空字段丢弃”）
3. **严格按 JAR 的 limit 值**（尤其是 `split("-",2/3/6/7)` 用于保护 command/NBT 中的 `-`）
4. **所有 legacy 概念一律 WARN**

    * meta=-1 / 32767 wildcard
    * 数字 potion id
    * any/all 哨兵
    * 1.8 chestType / fishing / biome / dimension 表达
    * 旧命令体系
5. **保留 JAR 的“怪异点”**

    * command wearing armour set-bonus 读取索引 8..10（你可以兼容更合理写法，但要 WARN）

---

## 还欠的一点点（我明确说明）

* `fishing_amounts.txt` 的 **精确格式与类别名称**：我这条回复里没来得及把 `loadFishingLoot()` 对应分支完整拆出 bytecode，所以不敢 100% 写死（避免误导你 1:1）。

    * 你如果要我下一条补齐：我会把该方法对应分支的 split/字段/默认值完整列出，保证严格一致。

如果你愿意，我下一条可以按你 zip 里实际出现的文件（比如 plural_lucky_block 那包）做一个 **“逐文件→逐行→按上述语法解释它会怎么被 JAR 解析”** 的示例，这样你写 Fabric port 的回归测试会非常稳。
