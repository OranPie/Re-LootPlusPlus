# Re-LootPlusPlus — Additional Format Specs

> **This document covers additional Loot++ 1.8.9 format specifications** not covered in PARSER.md:
> command chain splitting rules, record format, Lucky Block drop format extensions,
> lppcondition semantics, and item/block addition type coverage.
>
> **Implementation classes:**
> - `command/exec/CommandChain.java` — top-level `;` split; warns on `&&`/`||`
> - `config/loader/RecordsLoader.java` — records.txt format
> - `lucky/drop/LuckyDropParser.java` — Lucky drop `key=value` attr format
> - `lucky/attr/LuckyAttrParser.java` — bare `@chance` normalization + WARN
>
> **Item addition types implemented:** generic, food, sword, axe, shovel, pickaxe, hoe, bow, armor
>
> **Block addition types implemented:** generic, plant, crop, cake
>
> **Deferred:** bows/guns/multitools full behavior; buttons/pressure_plates/slabs/stairs/panes/walls.
>
> ---
>
可以开始实现了。你现在手上已经够做出 **MVP（能跑你这两包 + 大量 1.8 luckyblocks addon）**：结构、parser 语法、selector、command 子集语义都有了。

不过在你“只看我输出、不看 jar”的前提下，我建议你**再补 6 个点**（都是“实现时必踩坑/不补会偏离 1:1”的），我把它们写成**必须项**和**可后置项**，你可以直接照做，不需要再反编译。

---

## 必须补的 4 项（否则很容易跑不出 1:1）

### 1) `command_*` 的“多命令串”分割规则

很多 pack 会在一条 `command_*.txt` 里写多个命令，用：

* `;`
* `&&`
* 或直接 `lppcondition ... _if_true_ lppcondition ...`（你 Astral 就是这样）

**建议实现：**

* `CommandChain.parse()`：仅支持 `;` 作为“顶层分隔符”

    * “顶层”=不在 `{...}` NBT 里、不在 `[...]` selector args 里
* 没写 `;` 就当单命令
* 分割时保留原样，不 trim 中间 token（只 trim 首尾）

**WARN：**

* 若检测到 `&&` 或 `||`：`WARN [LegacyCommandChain] unsupported separator, treating as literal`（别擅自当逻辑运算）

### 2) `%%%%%` DropGroup 的权重语义（这一点必须写死）

你要严格按 Loot++ 的语义来：

* 一个 dropGroup 内可以有多个 dropEntry（用 `%%%%%` 分隔）
* **只有 group 内第一个 entry 的 weight 参与抽取**
* 抽中该 group → group 内 **所有 entry 依次执行**

这点对一些“抽到就同时 summon+playsound+give”的 luckyblock 组很关键。

### 3) successCount 的定义要统一（否则 lppcondition 会乱）

你已经同意用 “clear 成功移除数量”作为条件，这很好。为了不出“包作者写的 if 逻辑失效”，建议你全局统一：

* 对“面向目标”的命令（effect/playsound/scoreboard/kill/testfor）：
  `successCount = 成功作用到的目标数量`
* 对“clear”：
  `successCount = 实际移除的 item 总数`
* 对“execute”：
  `successCount = Σ 子命令 successCount`（对每个 target 累加）
* 对“summon/setblock/gamerule”：
  成功=1，失败=0

**WARN：**

* 当命令语义需要降级（例如 parse 不出某参数而忽略）必须 WARN，successCount 仍按“实际成功”算。

### 4) `wearing_armour` 的 set-bonus（含那个奇怪索引）怎么处理

你之前也看到：JAR 的 command wearing armour set-bonus 索引很奇怪。你不看 jar 的话，我给你一个“兼容+可复刻”的做法：

* **严格模式（默认）**：
  只认“JAR 位置”的三个额外物品字段（即语法上必须能提供到那三个字段才能触发 set-bonus）
  这样你最接近 1:1。
* **兼容模式（可选 config 开关）**：
  如果命令行里没有占位字段但末尾出现 3 个 itemId，看起来像 set-bonus，就按合理写法解析，**但必须 WARN**：
  `WARN [LegacyArmourSetBonus] non-jar layout accepted`

这样你既能 1:1，又能更好兼容社区写错但“大家都这样用”的包。

---

## 可后置的 2 项（先不做也能开跑，但迟早要补）

### 5) 1.8 “chest_type” 到 1.20.1 loot table 的映射策略

如果你想让老包的 `chest_content` 也 1:1 生效，你需要一个映射表：

* 1.8 里的 ChestGenHooks type（或 Loot++ 自定义 type 名）
  → 1.20.1 的 loot table id（如 `minecraft:chests/simple_dungeon` 等）

**建议：**

* 先做一个 `legacy_chest_types.json`（用户可扩展）
* 未映射到就 WARN 并跳过（不要瞎注入）

### 6) “外部 zip 资源包注入”的客户端边界

你已经有结构里 resourcepack 模块了。实作时注意：

* 只把 `assets/lootplusplus/**` 暴露为资源包内容（其他路径不暴露）
* 资源包优先级要高于 vanilla，但低于玩家手动选择的资源包（可按你 provider 插入位置控制）

---

## 你现在可以按什么顺序实现（最不容易返工）

1. **PackDiscovery + PackIndex + 读取 SourceLoc**（服务端能读 zip 内 config）
2. **严格 parser**（按你前面要求的 split/limit/默认值/钳制）
3. **LegacyWarnReporter + warnOnce**（从第一天就接上，避免后面补日志太痛）
4. **LegacySelectorParser**（先把 r/score_* 做出来）
5. **LegacyCommandRunner**（先实现 clear/effect/playsound/scoreboard/lppcondition/execute/testfor）
6. **Tick 扫描 + Use/Attack hook**（让 Astral fairy 这种开始工作）
7. **ThrownItemEntity + drops group**（让 plural chaos ball 这种开始工作）
8. 再补：world_gen + command_trigger_block + loot table 注入（视你优先级）

---

## 一句话结论

**是的，你可以开写了。**
唯一我强烈建议你在开写前把上面“必须补的 4 项”写进你的 `SPEC.md`（或者直接写成单元测试），这样你后面不会因为 lppcondition/权重/命令链细节偏了而大返工。

如果你愿意，我也可以顺手给你一份“最小回归测试清单”（用你两份 zip 的 5～10 行作为 golden cases），你照着写 JUnit/GameTest 就能快速验证每次改动不破坏 1:1。

---

# Addition 2（新发现/必须补支持）

## 2) 新配置目录/文件类型（来自 addon 实例）

### 2.1 `config/furnace_recipes/`

* `add_smelting_recipes.txt`

```
<input item id>_____<metadata (-1 for any)>_____<output item id>_____<metadata>_____<NBT Tag ({} for blank)>_____<amount>_____<xp given (optional)>
```

* `add_furnace_fuels.txt`

```
<fuel item id>_____<metadata (-1 for any)>_____<burn time>
```

### 2.2 `config/fishing_loot/` 拆分为 3 个文件（分隔符是 `-`）

* `fish_additions.txt`
* `junk_additions.txt`
* `treasure_additions.txt`

格式：

```
<name>-<amount>-<damage percent>-<enchanted>-<weight>-<metadata (optional)>-<NBT Tag (optional)>
```

（JAR 还有“默认 entry tweak”行，但当前 addon 基本不写。）

### 2.3 `config/records/records.txt`（`-` 分隔）

```
<record name>-<description>
```

### 2.4 `config/stack_size/stack_sizes.txt`

```
<Item/Block name>_____<Stack size>
```

示例：

```
ender_pearl_____64
```

---

## 3) `block_additions` 扩展类型（新增 + 格式严格）

### 3.1 `generic.txt`（已被实际使用，必须实现）

14 字段：

```
<Block name>_____<Block display name>_____<Block material>_____<Falls (true/false)>_____<Beacon Base (true/false)>_____<Hardness>_____<Explosion resistance>_____<Harvesting item type>_____<Harvest level (-1 for any)>_____<Light emitted>_____<Slipperiness>_____<Fire Spread Speed>_____<Flammability>_____<Opacity>
```

示例：

```
ender.ender_crystal_block_____§2§lEnder Crystal Block_____iron_____false_____false_____5.0_____5.0_____pickaxe_____2_____0_____0.6_____0_____0_____-1
```

### 3.2 `plants.txt`（已被实际使用）

10 字段（crossed texture plant）：

```
<Block name>_____<Block display name>_____<Block material>_____<Hardness>_____<Explosion resistance>_____<Harvesting item type>_____<Harvest level (-1 for any)>_____<Light emitted>_____<Fire Spread Speed>_____<Flammability>
```

示例：

```
water.coral_plant_____Coral Plant_____gourd_____0.0_____0.0_____none_____-1_____0.7_____0_____0
```

### 3.3 其它 `block_additions/*.txt`（即使当前为空也要支持）

* `crops.txt`（10 字段）

```
<Block name>_____<Block display name>_____<Seed item name>_____<Seed item metadata>_____<Light emitted>_____<Fire Spread Speed>_____<Flammability>_____<Can bonemeal?>_____<Nether plant?>_____<Right click to harvest?>
```

* `cakes.txt`（13 字段，末尾 potion effects 可选）

```
<Block name>_____<Block display name>_____<Hardness>_____<Explosion resistance>_____<Light emitted>_____<Slipperiness>_____<Fire Spread Speed>_____<Flammability>_____<Number of bites>_____<Hunger restored>_____<Saturation restored>_____<Always edible?>_____<Potion effects given (optional)>
```

* 其它可能出现：`buttons.txt` `pressure_plates.txt` `doors.txt` `slabs.txt` `stairs.txt` `panes.txt` `walls.txt` `fences.txt` `fence_gates.txt` `ladders.txt` `logs_pillars.txt` `multiple_metadata.txt` `crafting_tables.txt` `furnaces.txt` …

---

## 4) 新增 item_effect 触发器（语义）

### 4.1 `hitting_entity_to_yourself`

* `config/item_effects/hitting_entity_to_yourself.txt`
* `config/item_effects/command_hitting_entity_to_yourself.txt`

格式与其它 item_effect 相同，**区别仅在目标：作用在攻击者自己**。

### 4.2 `digging_block_*`

* `config/item_effects/command_digging_block.txt`：**玩家用物品破坏方块时**运行 command（按 item）
* `config/item_effects/digging_block_block.txt`：**破坏特定方块时**触发（按 block）

block-based 格式：

```
<Block name>_____<Block meta (-1)>_____<Effect id>_____<Duration>_____<Strength>_____<Prob>_____<Particles>
```

command block-based 格式：

```
<Block name>_____<Block meta (-1)>_____<Command probability>_____<Command>
```

---

## 5) 两个实际“坑点”（必须补）

### 5.1 编码回退

* 先 **UTF-8 strict** 解码
* 失败则回退 `ISO-8859-1/CP1252`
* 首行需 strip BOM（`\\uFEFF`）

### 5.2 Recipes：OreDict token 可带引号

* `add_shapeless.txt` / `add_shaped.txt` 中的输入 token 如果包在 `"` 里
  → 视作 OreDict key（去引号）
* 必须 WARN（legacy/oredict）

---

## 6) 资源包注入边界修正

* **不要只过滤 `assets/lootplusplus/**`**  
* addon zip 还包含 `assets/lucky/**`（Lucky Block 命名空间）  
→ 应挂载 **整个 zip 的 `assets/**`**

---

# Addition 3（LuckyBlock Legacy 兼容注入）

## 1) LuckyBlock 属性解析容错（`@chance` 裸键）

旧包里常见写法：

```
ID=lootplusplus:xxx@chance@luck=1
```

新 LuckyBlock 把 `@` 解析成字典，裸 `chance` 没 `=` 会报错。

**兼容规则（必须 WARN）：**

* 发现裸 `@chance` → 视为 `chance=1`
* 其它裸 key 保持原行为（不强行容错）

示例 WARN：

```
[LootPP-Legacy] LuckyAttr bare @chance treated as chance=1
```

---

## 2) LuckyBlock 生成实体的 Legacy ID 归一化

旧包常见实体名（`EntityHorse`, `PigZombie`, `Item` 等）在 1.13+ 会触发 `Identifier` 报错。

**兼容规则（必须 WARN）：**

* 统一走 `LegacyEntityIdFixer`
* 支持显式映射表（PigZombie→zombified_piglin 等）
* `EntityHorse` 需按 NBT `Type` 分流到 horse/donkey/mule/zombie_horse/skeleton_horse
* `Item` → `minecraft:item`，并补齐 NBT `Item.id` namespace

示例 WARN：

```
[LootPP-Legacy] LegacyEntityId mapped 'EntityHorse' type=1 -> 'minecraft:donkey'
[LootPP-Legacy] LegacyItemId assumed namespace for 'iron_boots' -> 'minecraft:iron_boots'
```

---

## 3) LuckyBlock 内置资源包的 Legacy 资源修复

LuckyBlock 内置 pack（如 `lucky/lucky_block_water_resources`）不会走你的 zip ResourcePack。  
必须在 **ResourcePackProfile#createResourcePack** 处包一层 wrapper，做下面修复并 WARN：

* blockstate：`variants.normal` → `""`
* model：缺省 `block/` 前缀自动补
* textures：`blocks/` → `block/`，`items/` → `item/`
* 常见改名：wool/terracotta/planks/log 等旧纹理名 → 1.18+ 新名
* `.lang` → `.json`（含 BOM strip + UTF-8 strict / Latin-1 fallback）
* `findResources` 里补充 `.lang` 对应的 `.json`

示例 WARN：

```
[LootPP-Legacy] LegacyBlockstate converted variant 'normal' to '' in lucky/xxx_resources
[LootPP-Legacy] LegacyTexture mapped texture blocks/hardened_clay_stained_blue -> block/blue_terracotta
```

---

# Addition 3（item_additions / item_effects 补全）

下面这两套目录（`config/item_additions/`、`config/item_effects/`）是 **Loot++ 在 1.8.9 的外置配置语法**：

* `item_additions`：定义要新增/注册的物品/材质
* `item_effects`：给物品/方块挂被动效果/触发命令

> 分隔符：字段用 `_____` 分割。  
> 注释/空行：`#` 与 `//` 都应忽略（兼容社区写法）。

---

## A) `config/item_additions/` 语法

### 0) 全局规则
1. 新物品 `Item name` 常不带 namespace（如 `water.water_sword`），应注册为 `lootplusplus:<name>`（或做别名重写）。
2. `Boolean.parseBoolean` 语义：只有 `true` 才算 true（`fals` 会变 false）。
3. 概率允许 >1（>1 等价必触发），不要 clamp。
4. NBT 中可能混入不可见字符（soft hyphen 等）——进入 SNBT 解析前应清洗并 WARN。

### 1) `generic_items.txt`
```
<Item name>_____<Display name>[_____<Shiny(boolean)>]
```

### 2) `materials.txt`
```
<Material item id>_____<Material meta(-1 any)>_____<HarvestLevel>
_____<BaseDurability>_____<Efficiency>_____<Damage>_____<Enchantability>
_____<ArmourDurabilityFactor>_____<ArmourProtectionList(helmet-chest-legs-boots)>
```

### 3) `swords.txt`
```
<Item name>_____<Display name>_____<Material item id>_____<Damage(float)>[_____<Material meta>]
```

### 4) `pickaxes.txt / axes.txt / shovels.txt / hoes.txt`
```
<Item name>_____<Display name>_____<Material item id>[_____<Material meta>]
```

### 5) 盔甲 `helmets.txt / chestplates.txt / leggings.txt / boots.txt`
```
<Item name>_____<Display name>_____<Material item id>_____<ArmourTextureBase>[_____<Material meta>]
```

### 6) `foods.txt`
基础 8 段：
```
<Item name>_____<Display name>_____<Shiny(boolean)>_____<FoodRestored(int)>
_____<Saturation(float)>_____<WolvesEat(boolean)>_____<AlwaysEdible(boolean)>
_____<TimeToEat(int)>[_____<PotionSpec>...]
```
PotionSpec（每段必须 5 段，否则该段跳过）：
```
<EffectId> - <DurationTicks> - <Amplifier> - <Probability(float)> - <ParticleType>
```
`ParticleType`：`none | faded | normal`

### 7) `thrown.txt`
（已在主文档中定义；特有二级分隔符 `%%%%%`）

### 8) `bows.txt / guns.txt / multitools.txt`
这些字段比较多且有可选尾字段；实现时：
* 缺字段用默认值，不要整行 fail
* 允许额外字段（保留/忽略并 WARN）

---

## B) `config/item_effects/` 语法

### 1) 触发文件
物品触发（效果/命令）：
* `held` / `in_inventory` / `right_click`
* `wearing_armour`（支持 set bonus）
* `hitting_entity_to_entity`
* `hitting_entity_to_yourself`
* `digging_block`

方块触发（效果/命令）：
* `standing_on_block` / `inside_block`
* `digging_block_block` / `blocks_in_inventory`

命令版前缀：`command_`（如 `command_held.txt`）

### 2) 物品效果（Potion effects）
```
<Item id>_____<Item meta(-1 any)>_____<Item NBT({} any)>
_____<Effect id>_____<DurationTicks>_____<Amplifier>_____<Probability>_____<ParticlesType>
```
* `Item id`：完整 id 或 `any`
* `Effect id`：字符串或数字（数字需 WARN + 映射）

### 3) 物品命令（Commands）
```
<Item id>_____<Item meta(-1 any)>_____<Item NBT({} any)>_____<CommandProbability>_____<Command>
```

### 4) `wearing_armour` 的 set-bonus
在基础 8 段后追加最多 3 个盔甲 id：
```
..._____<Armor item 1 id>_____<Armor item 2 id>_____<Armor item 3 id>
```

### 5) 方块效果/命令
方块效果：
```
<Block id>_____<Block meta(-1 any)>_____<Effect id>_____<Duration>_____<Amplifier>_____<Probability>_____<ParticlesType>
```
方块命令：
```
<Block id>_____<Block meta(-1 any)>_____<CommandProbability>_____<Command>
```
