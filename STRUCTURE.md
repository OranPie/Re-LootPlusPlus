下面是面向 **1.20.1 Fabric**、目标 **“不改 addon zip，复刻 Loot++ 1.8.9 行为”** 的一份 **预设完整 mod 结构**（目录树 + 每个模块职责 + 启动/运行时装配点）。
下一条你再要“严格 parser 语法”，我会把每个 `config/*.txt` 的 BNF/EBNF、token 规则、转义/容错/WARN 规则完整写出来。

---

## 项目目录树（建议的完整结构）

```
lootpp-compat-fabric/
├─ build.gradle
├─ settings.gradle
├─ gradle.properties
├─ README.md
├─ LICENSE
├─ src/main/resources/
│  ├─ fabric.mod.json
│  ├─ lootpp.mixins.json
│  ├─ assets/lootpp/
│  │  ├─ lang/en_us.json
│  │  ├─ lang/zh_cn.json
│  │  ├─ textures/ (可选：你自带的基础材质)
│  │  └─ models/   (可选：你自带的基础模型)
│  └─ data/lootpp/ (可选：你自带的数据包内容)
│
├─ src/main/java/<your/group>/lootpp/
│  ├─ LootPP.java                         // ModInitializer：服务端/通用初始化入口
│  ├─ LootPPClient.java                   // ClientModInitializer：资源包注入、客户端渲染挂钩
│  ├─ LootPPConstants.java                // MODID、路径、版本、分隔符常量（"_____","%%%%%"）
│  ├─ bootstrap/
│  │  ├─ Bootstrap.java                   // 总装配器：scan → parse → register → buildIndex → installHooks
│  │  ├─ BootstrapStage.java              // 分阶段枚举/状态，用于日志与失败降级
│  │  └─ BootstrapReport.java             // 统计：加载了多少 pack、多少规则、多少 legacy warn
│  │
│  ├─ diagnostic/
│  │  ├─ Log.java                         // 统一 logger 包装
│  │  ├─ LegacyWarnReporter.java          // 关键：所有 legacy 适配必须走这里，强制 WARN
│  │  ├─ WarnKey.java                     // warn 去重键（同原因 warnOnce）
│  │  ├─ ParseError.java                  // 可恢复错误（行级别跳过）
│  │  └─ SourceLoc.java                   // packId/zip/path/line/rawLine：用于精准定位
│  │
│  ├─ pack/
│  │  ├─ PackDiscovery.java               // 扫描 mods/ & 可选自定义目录，识别 addon zip
│  │  ├─ AddonPack.java                   // 一个 zip 的抽象：id、路径、优先级、文件索引
│  │  ├─ PackIndex.java                   // 列出 config/**.txt + 读取行（带 SourceLoc）
│  │  ├─ PackMergePolicy.java             // 合并顺序策略：priority → 文件名排序
│  │  └─ io/
│  │     ├─ ZipFs.java                    // zip 文件系统/缓存读取（避免反复打开）
│  │     └─ PackFileReader.java           // 读取 config 文本（UTF-8 + 容错 BOM）
│  │
│  ├─ resourcepack/
│  │  ├─ ExternalPackResource.java         // 把 addon zip 的 assets/lootplusplus/* 暴露给资源系统
│  │  ├─ ExternalPackProvider.java         // 资源包提供者（客户端）：把 zip 挂进 ResourcePackManager
│  │  └─ mixin/
│  │     └─ ResourcePackManagerMixin.java  // 注入 provider（1.20.1 通常需要 mixin）
│  │
│  ├─ legacy/
│  │  ├─ LegacyCompat.java                // 兼容入口（禁止在别处偷偷修）
│  │  ├─ mapping/
│  │  │  ├─ LegacyIdMapper.java            // 1.8 id/meta → 1.20.1 id/state 的映射
│  │  │  ├─ LegacySoundMapper.java         // mob.wither.death → 新 sound id
│  │  │  ├─ LegacyParticleMapper.java      // spell/enchantmenttable → 新粒子
│  │  │  ├─ LegacyPotionMapper.java        // 数字/旧名 → registry effect
│  │  │  └─ LegacyEntityMapper.java        // Cow → minecraft:cow
│  │  ├─ nbt/
│  │  │  ├─ LenientNbtParser.java          // 宽松 SNBT：规范化时必须 WARN
│  │  │  └─ NbtPredicate.java              // contains-match：规则 NBT 匹配
│  │  └─ selector/
│  │     ├─ LegacySelectorParser.java      // @e[r=3,score_x_min=...] → 目标列表（必须 WARN）
│  │     └─ SelectorQuery.java             // 选择器查询对象（不依赖 Brigadier）
│  │
│  ├─ config/
│  │  ├─ ConfigDomains.java               // 约定的目录：item_effects、block_drops、world_gen...
│  │  ├─ model/
│  │  │  ├─ key/
│  │  │  │  ├─ ItemKey.java               // id + meta + nbtPredicate
│  │  │  │  ├─ BlockKey.java              // id + meta
│  │  │  │  └─ EntityKey.java             // id + nbtPredicate
│  │  │  ├─ rule/
│  │  │  │  ├─ EffectRule.java            // 物品/方块触发 → 药水效果
│  │  │  │  ├─ CommandRule.java           // 物品/方块触发 → 命令串
│  │  │  │  ├─ DropRule.java              // block/entity drops
│  │  │  │  ├─ ThrownDef.java             // thrown item 定义
│  │  │  │  ├─ WorldGenDef.java           // surface/underground 定义
│  │  │  │  ├─ ChestLootDef.java          // chest_content 定义
│  │  │  │  └─ CreativeEntry.java         // creative_menu_additions
│  │  │  └─ drop/
│  │  │     ├─ DropEntry.java             // i/e/c 三类
│  │  │     ├─ DropGroup.java             // %%%%% 组：组内全执行/全掉落
│  │  │     └─ DropRoller.java            // 权重抽取（复刻 Loot++ 权重语义）
│  │  │
│  │  ├─ loader/
│  │  │  ├─ LoaderContext.java            // packIndex + legacyCompat + warnReporter
│  │  │  ├─ ItemAdditionsLoader.java      // config/item_additions/*
│  │  │  ├─ BlockAdditionsLoader.java     // config/block_additions/*
│  │  │  ├─ ThrownLoader.java             // config/item_additions/thrown.txt
│  │  │  ├─ EffectLoader.java             // config/item_effects/*.txt
│  │  │  ├─ CommandLoader.java            // config/item_effects/command_*.txt
│  │  │  ├─ BlockDropsLoader.java         // config/block_drops/adding|removing
│  │  │  ├─ EntityDropsLoader.java        // config/entity_drops (兼容 enity_drops)
│  │  │  ├─ WorldGenLoader.java           // config/world_gen/surface|underground
│  │  │  ├─ ChestContentLoader.java       // config/chest_content/*
│  │  │  ├─ RecipesLoader.java            // config/recipes/*
│  │  │  ├─ OreDictLoader.java            // config/ore_dictionary/*
│  │  │  └─ CreativeMenuLoader.java       // config/general/creative_menu_additions.txt
│  │  │
│  │  └─ parse/
│  │     ├─ LineReader.java               // 行读取：跳过空行/#，保留 raw
│  │     ├─ Splitter.java                 // "_____" 切分，保留空段
│  │     ├─ NumberParser.java             // int/float/bool 解析（失败 → WARN + skip line）
│  │     └─ Escapes.java                  // § 颜色码处理（可选）
│  │
│  ├─ registry/
│  │  ├─ RegistryCoordinator.java         // 把 defs 注册进 Registry（只在 bootstrap 阶段）
│  │  ├─ DynamicItemRegistrar.java        // 运行时根据 item_additions 注册
│  │  ├─ DynamicBlockRegistrar.java       // 运行时根据 block_additions 注册
│  │  ├─ EntityRegistrar.java             // ThrownItemEntity 等实体注册
│  │  ├─ BlockEntityRegistrar.java        // CommandTriggerBlockEntity 注册
│  │  ├─ CreativeTabRegistrar.java        // 1.20.1 的 item group/creative tabs 注入
│  │  └─ RenderRegistrar.java             // 客户端渲染注册（如果需要）
│  │
│  ├─ content/
│  │  ├─ block/
│  │  │  └─ CommandTriggerBlock.java       // 复刻 command_trigger_block：读 CommandList
│  │  ├─ blockentity/
│  │  │  └─ CommandTriggerBlockEntity.java // tick 或 onPlace 触发命令（按旧行为）
│  │  ├─ entity/
│  │  │  └─ ThrownItemEntity.java          // impact 执行 DropGroup（i/e/c）
│  │  └─ item/
│  │     ├─ LootItem.java                 // 可选：loot item（右键变 loot）
│  │     └─ OtherDynamicItems...          // item_additions 生成的物品基类
│  │
│  ├─ runtime/
│  │  ├─ RuntimeIndex.java                // 把规则建索引：trigger → itemId/blockId/entityId → list
│  │  ├─ RuntimeContext.java              // world/server/player/random/sourceLoc
│  │  ├─ RuleEngine.java                  // 入口：tick/事件 → 查索引 → 执行
│  │  ├─ trigger/
│  │  │  ├─ TriggerType.java              // HELD/INVENTORY/WEARING/RIGHT_CLICK/...
│  │  │  ├─ PlayerTickScanner.java        // 性能关键：背包扫描优化（只扫可能命中 itemId）
│  │  │  └─ BlockSpaceProbe.java          // standing/inside 采样策略
│  │  ├─ action/
│  │  │  ├─ ApplyEffectAction.java        // 药水效果
│  │  │  ├─ RunCommandAction.java         // 命令（legacy runner）
│  │  │  ├─ SpawnDropAction.java          // i/e/c 掉落执行
│  │  │  └─ WorldGenAction.java           // 放置方块/组
│  │  └─ rng/
│  │     └─ LootPPRandom.java             // 权重/概率 roll（保持可复现的细节）
│  │
│  ├─ hooks/
│  │  ├─ HookInstaller.java               // 安装所有 Fabric events + 必要 mixin hook
│  │  ├─ ServerTickHook.java              // 玩家 tick 触发 held/inv/wearing/standing/inside
│  │  ├─ UseItemHook.java                 // right_click
│  │  ├─ AttackHook.java                  // hitting_entity_to_entity
│  │  ├─ BlockBreakHook.java              // digging_block + block_drops
│  │  ├─ EntityDeathHook.java             // entity_drops
│  │  ├─ LootTableHook.java               // chest_content 注入（loot table 修改）
│  │  └─ WorldGenHook.java                // surface/underground feature 注册
│  │
│  ├─ command/
│  │  ├─ LegacyCommandRunner.java         // 旧命令子集解释器（返回 successCount）
│  │  ├─ LppBuiltins.java                 // lppcondition / lppeffect 实现
│  │  ├─ exec/
│  │  │  ├─ ExecContext.java              // 执行者、位置、世界、随机、sourceLoc
│  │  │  ├─ ExecResult.java               // successCount + 可选 debug
│  │  │  └─ CommandChain.java             // 支持分号/多命令串（按旧包常见写法）
│  │  └─ impl/
│  │     ├─ EffectCmd.java
│  │     ├─ ParticleCmd.java
│  │     ├─ SummonCmd.java
│  │     ├─ ExecuteCmd.java
│  │     ├─ TestforCmd.java
│  │     ├─ PlaysoundCmd.java
│  │     ├─ ScoreboardCmd.java
│  │     ├─ SetblockCmd.java
│  │     ├─ GiveCmd.java
│  │     ├─ ClearCmd.java
│  │     ├─ KillCmd.java
│  │     └─ EnchantCmd.java
│  │
│  ├─ reload/
│  │  ├─ ReloadListener.java              // /reload 时重建 RuntimeIndex（不重新注册物品方块）
│  │  └─ ReloadState.java                 // reload 状态与版本戳
│  │
│  └─ mixin/
│     ├─ common/
│     │  ├─ BlockDropMixin.java            // 如果 Fabric event 不够用：拦截掉落/fortune/silk
│     │  ├─ EntityDeathMixin.java          // 同理：确保能拿到 looting/击杀者
│     │  └─ WorldGenMixin.java             // 如果需要更底层注入
│     └─ client/
│        └─ ResourcePackClientMixin.java   // 把外部 zip 注入资源包列表（若 API 不够）
│
└─ src/client/java/... (可选：也可以都放 main/java 并用 @Environment 区分)
```

---

## 关键设计点（为了 1:1 复刻 + 不改 zip）

### 1) “外部 zip”双重身份

* **配置源**：服务端也要读 `config/**/*.txt`
* **资源包**：客户端要挂载 `assets/lootplusplus/**`（模型/贴图/语言）
* 因此 `pack/` 与 `resourcepack/` 必须拆开：同一个 `AddonPack` 同时供两边使用，但客户端额外创建 `ExternalPackResource`。

### 2) registry 与 reload 的硬边界（1.20.1 必须遵守）

* **动态注册物品/方块/实体**只能在启动 bootstrap 阶段做（注册表冻结后不可再加）
* `/reload` 只能：

    * 重读规则（effects/commands/drops/chest/worldgen 放置规则）并重建 `RuntimeIndex`
    * 不能新增 item/block（否则 1.20.1 会崩或行为不稳定）

### 3) Legacy 兼容必须强制 WARN

* 所有兼容入口集中在 `legacy/LegacyCompat`
* 任意地方想做映射（meta、旧实体名、旧 sound、旧选择器、宽松 NBT）都必须调用 `LegacyCompat.*`，由它触发：

    * `LegacyWarnReporter.warnOnce(WarnKey, msg, SourceLoc)`
* **禁止** loader/runtime/command 里私自 silent fix

### 4) 行级容错（复刻 Loot++ 的“坏一行不影响全局”）

* `ConfigLoader` 对每一行：解析失败 → `WARN + 跳过该行`
* 只有“文件无法读取/zip 损坏”这种才升级为 ERROR（并且最好降级继续加载其他 pack）

### 5) runtime 索引必须做“快速命中”

1.20.1 背包/装备 tick 扫描是性能热点：

* `RuntimeIndex` 建 `itemId → rules`、`blockId → rules`、`trigger → map`
* `PlayerTickScanner` 先用“可能命中 itemId 集合”过滤，避免每 tick 扫全背包做复杂 NBT predicate

---

## Bootstrap 执行顺序（写在结构里方便你直接照着实现）

`bootstrap/Bootstrap.java` 的阶段建议固定：

1. `discoverPacks()` → `List<AddonPack>`
2. `indexFiles()` → `PackIndex`（行号/SourceLoc）
3. `parseDefinitions()`：

    * item_additions / block_additions / thrown / command_trigger_block 依赖等
4. `registerContent()`：

    * blocks/items/entities/blockEntities/creativeTabs
5. `parseRules()`：

    * item_effects / command_* / drops / chest_content / world_gen / recipes(可选)
6. `buildRuntimeIndex()` → `RuntimeIndex`
7. `installHooks()` → `hooks/HookInstaller`
8. `installReload()` → `reload/ReloadListener`
9. `reportSummary()` → packs/规则数量/legacy warn 统计

---

如果你认可这个结构，下一条我会按你要求给 **“严格的 parser 语法”**：

* 每个文件类型（item_effects、command_*、thrown、world_gen/surface、entity_drops/adding、chest_content 等）的 **EBNF**
* token/空白/注释/字段缺失规则
* `%%%%%` 分组与权重抽取语义
* NBT 的“宽松解析”何时触发、何时 WARN、何时跳过整行
* legacy 路径别名（`enity_drops`）等的判定规则与 WARN 文案模板
