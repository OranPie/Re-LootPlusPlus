# Re-LootPlusPlus

Re-LootPlusPlus is a Fabric mod that replicates **Loot++ 1.8.9** and **Lucky Block** addon behavior on modern Minecraft **without modifying addon zip files**. It is a native re-implementation — not an injection shim — that scans addon zips, parses their `config/**/*.txt` rules, and wires everything into Fabric events and loot tables.

| Property | Value |
|---|---|
| Minecraft | `1.18.2` |
| Fabric Loader | `0.18.4` |
| Fabric API | `0.77.0+1.18.2` |
| Java | `17` |
| Mod ID | `re-lootplusplus` |

## What This Mod Does

- **Pack discovery** — scans multiple directories for addon zips/folders (see below).
- **Config parsing** — reads `config/**/*.txt` rules from every discovered pack.
- **Dynamic registration** — registers items, blocks, entities, and creative-tab entries required by addons at bootstrap time.
- **Lucky Block native impl** — own `NativeLuckyBlock` / `NativeLuckyBlockEntity` that evaluates Lucky drop lines without depending on the Lucky Block runtime at play time.
- **Legacy adaptation** — maps 1.8 IDs, sounds, effects, NBT, selectors, and commands to 1.18.2 equivalents, with mandatory WARN for every adaptation.
- **In-game UI** — `✦ Loot++` button in the pause menu opens a full pack-browser with drop-line viewer and texture gallery.
- **Diagnostics** — exports `report.json`, `warnings.tsv`, `thrown.tsv` per run.

## Addon Pack Discovery

`PackDiscovery` scans these directories (in the game directory) in fixed order:

1. `lootplusplus_addons/`
2. `addons/`, `addons/lucky/`, `addons/lucky_block/`
3. `packs/`
4. `mods/` (unless `scanModsDir=false`)
5. Paths in `config.extraAddonDirs`, `RELOOTPLUSPLUS_ADDONS` env var, or `-Drelootplusplus.addons` system property

Both `.zip` files and unpacked directories (any subdirectory containing a `config/` subfolder) are recognized.

## Build and Run

```bash
./gradlew compileJava --no-daemon    # compile only (fast check)
./gradlew build --no-daemon          # compile + package → build/libs/
./gradlew runClient --no-daemon      # launch game client (dev environment)
./gradlew runServer --no-daemon      # launch dedicated server (dev environment)
```

There is no automated test suite; use the in-game commands to validate behavior.

## Runtime Commands (require op)

| Command | Description |
|---|---|
| `dumpnbt item` | Print NBT of held item |
| `dumpnbt block` | Print NBT of targeted block |
| `lppdrop eval [x y z]` | Evaluate Lucky drops at position |
| `lppdrop eval_dry [x y z]` | Evaluate without executing |
| `lppdrop eval_counts <n> [x y z]` | Simulate N evaluations and print counts |
| `lppdrop lucky_eval [x y z]` | Run Lucky drop pipeline |
| `lppdrop lucky_eval_bulk <n> [x y z]` | Run Lucky drop pipeline N times |
| `lppdrop lucky_eval_bulk_dry <n> [x y z]` | Same but without executing |

## Config (`config/relootplusplus.json`)

The file is auto-created on first run. All fields can be overridden via system properties (`-Drelootplusplus.<field>=<value>`) or environment variables (`RELOOTPLUSPLUS_<FIELD>=<value>`).

### Core

| Field | Default | Description |
|---|---|---|
| `dryRun` | `false` | Parse-only mode — skip registration and hook installation |
| `exportReports` | `true` | Write diagnostics files on shutdown |
| `exportRawLines` | `true` | Include raw source lines in `warnings.tsv` |
| `exportDir` | `"logs/re_lootplusplus"` | Root directory for diagnostic exports |
| `extraAddonDirs` | `[]` | Additional directories to scan for addon packs |
| `duplicateStrategy` | `"suffix"` | How to handle duplicate pack/block/item IDs: `suffix` or `ignore` |
| `potioncoreNamespace` | `"re_potioncore"` | Namespace used when remapping `potioncore:*` effect IDs |
| `disabledAddonPacks` | `[]` | Pack IDs to skip during load |
| `injectResourcePacks` | `true` | Mount addon zip `assets/**` as a resource pack |
| `skipMissingEntityRenderers` | `false` | Suppress errors for entities without client renderers |

### Logging

| Field | Default | Description |
|---|---|---|
| `logWarnings` | `false` | Global warning logging gate |
| `logLegacyWarnings` | `false` | Print legacy WARN lines to console |
| `logDetailLevel` | `null` | Detail log level: `summary`, `detail`, or `trace` |
| `logDetailFilters` | `[]` | Module filter list (empty = all modules) |
| `legacyWarnConsoleLimitPerType` | `5` | Max console entries per WARN type (`0` = unlimited) |
| `legacyWarnConsoleSummary` | `true` | Print suppressed-count summary at bootstrap end |
| `debugFileEnabled` | `true` | Enable debug log file (requires level ≥ `detail`) |
| `debugFileMaxLines` | `0` | Truncate debug log file at N lines (`0` = unlimited) |

### Drop Engine

| Field | Default | Description |
|---|---|---|
| `luckModifier` | `0.1` | Luck-to-weight scale factor in `LuckyDropRoller` |
| `defaultLuck` | `0` | Baseline luck added to every Lucky Block break |
| `commandDropEnabled` | `true` | `false` skips all `type=command` Lucky drops |
| `dropChatEnabled` | `true` | `false` suppresses in-game chat messages for Lucky drops |

### Tick Hooks

| Field | Default | Description |
|---|---|---|
| `tickIntervalTicks` | `1` | Run player tick scan every N ticks |
| `enabledTriggerTypes` | `[]` | Trigger types to run (empty = all): `held`, `wearing_armour`, `in_inventory`, `standing_on_block`, `inside_block` |

### World & Pack

| Field | Default | Description |
|---|---|---|
| `structureMaxDimension` | `256` | Skip schematics with any axis larger than this (`0` = no limit) |
| `scanModsDir` | `true` | `false` prevents scanning the `mods/` directory for addon packs |
| `naturalGenEnabled` | `true` | `false` disables Lucky Block natural generation feature registration |
| `legacySanitizeEnabled` | `true` | `false` disables legacy drop-string sanitization |

## In-Game UI

The `✦ Loot++` button appears in the pause menu (top-left corner). It opens:

- **MenuScreen** — lists all discovered addon packs with enable/disable toggle, search filter, and status badges.
- **PackDetailScreen** — tabs: Overview, Items, Drops, Structures, Textures.
- **DropLinesScreen** — scrollable list of parsed Lucky drop lines for a pack.
- **ItemDetailScreen** — item kind, drop-line references.
- **RawLineScreen** — source text with line numbers and syntax coloring.
- **PackTextureGalleryScreen** — previews all textures found in a pack's assets.

## Diagnostics

When `exportReports=true`, each run writes to `<exportDir>/<timestamp>/`:

| File | Contents |
|---|---|
| `report.json` | Structured summary: config, bootstrap stats, warn-type counts, pack list |
| `warnings.tsv` | All legacy warnings, one per line, tab-separated with source location |
| `thrown.tsv` | Thrown-item definitions |

`<exportDir>/latest.txt` points to the most recent timestamp directory.

When `logDetailLevel=detail` or `trace` and `debugFileEnabled=true`, a `debug-<timestamp>.log` file is written to `<exportDir>/` with all `Log.debug` and `Log.trace` calls regardless of console filter.

## Documentation Map

| File | Contents |
|---|---|
| `docs/STRUCTURE.md` | Package-level architecture: module responsibilities, bootstrap sequence, key design constraints |
| `PARSER.md` | Original Loot++ 1.8.9 parser spec (EBNF grammar, split rules, defaults, clamping) — the reference we implement against |
| `ADAPTION.md` | Legacy selector and command semantics spec |
| `INJECTION.md` | Fabric injection/registration strategy notes |
| `REFERENCE.md` | Hook matrix, execution order, and example parse traces |
| `ADDITION.md` | Additional config file formats and compatibility notes |
| `docs/PLAN.md` | Implementation notes: architecture summary, config fields, behavioral constraints, known gaps |

## Troubleshooting

**Lucky drops produce no result / entity marked as removed:**
- The `minecraft:item` entity is spawned as a LootThrownItem; if the underlying item entity was already marked removed, the drop silently fails. Check `lppdrop eval_dry` output.

**Debug log is too large:**
- Set `debugFileMaxLines` to a non-zero value (e.g. `50000`) to truncate.

**Addon pack not loading:**
- Check `disabledAddonPacks` and ensure the zip contains a `config/` subfolder.
- Run with `logLegacyWarnings=true` to see per-line parse errors in the console.
- Note: packs added while the game is running are not recognized until the next restart (`/reload` does not re-run pack discovery).

**Rule not firing for an item even though the config looks correct:**
- `RuntimeIndex` supports a wildcard item ID `"*"` — rules declared with `itemId = *` match any held/worn item. Only item-keyed rules support wildcard; block-keyed rules use exact match only.
- Verify the item's registered ID matches the ID in the config (case-sensitive, dot-to-underscore normalization applies to dynamically registered items).

**Server startup stops early:**
- Ensure `run/eula.txt` contains `eula=true`.

---

## 中文版本（Chinese Version）

> **Language / 语言:** [English](#re-lootplusplus) · [中文](#中文版本chinese-version)

Re-LootPlusPlus 是一个 Fabric 模组，在现代 Minecraft（1.18.2）上原生复现 **Loot++ 1.8.9** 与 **Lucky Block** 附加包行为，**无需修改附加包 zip 文件**。本模组对 Loot++ 1.8.9 进行原生重实现，而非注入式兼容层：扫描附加包 zip，解析其 `config/**/*.txt` 规则，并将所有规则接入 Fabric 事件与战利品表。

| 属性 | 值 |
|---|---|
| Minecraft | `1.18.2` |
| Fabric Loader | `0.18.4` |
| Fabric API | `0.77.0+1.18.2` |
| Java | `17` |
| Mod ID | `re-lootplusplus` |

### 功能说明

- **附加包发现** — 扫描多个目录中的附加包 zip/文件夹（见下文）。
- **配置解析** — 读取每个发现的附加包中的 `config/**/*.txt` 规则。
- **动态注册** — 在启动时注册附加包所需的物品、方块、实体及创造模式标签栏条目。
- **Lucky Block 原生实现** — 使用自有 `NativeLuckyBlock` / `NativeLuckyBlockEntity` 评估 Lucky 掉落行，无需在运行时依赖 Lucky Block 模组。
- **遗留适配** — 将 1.8 的 ID、音效、效果、NBT、选择器与命令映射为 1.18.2 等价物，每次适配均强制输出 WARN 日志。
- **游戏内 UI** — 暂停菜单中的 `✦ Loot++` 按钮可打开完整的附加包浏览器，含掉落行查看器与纹理图库。
- **诊断导出** — 每次运行导出 `report.json`、`warnings.tsv`、`thrown.tsv`。

### 附加包发现目录

`PackDiscovery` 按固定顺序扫描以下目录（相对于游戏目录）：

1. `lootplusplus_addons/`
2. `addons/`、`addons/lucky/`、`addons/lucky_block/`
3. `packs/`
4. `mods/`（除非 `scanModsDir=false`）
5. `config.extraAddonDirs`、`RELOOTPLUSPLUS_ADDONS` 环境变量或 `-Drelootplusplus.addons` 系统属性所指定的路径

`.zip` 文件与包含 `config/` 子文件夹的未压缩目录均被识别为附加包。

### 构建与运行

```bash
./gradlew compileJava --no-daemon    # 仅编译（快速检查）
./gradlew build --no-daemon          # 编译 + 打包 → build/libs/
./gradlew runClient --no-daemon      # 启动游戏客户端（开发环境）
./gradlew runServer --no-daemon      # 启动专用服务器（开发环境）
```

本模组无自动化测试套件；请使用游戏内命令验证行为。

### 运行时命令（需 OP 权限）

| 命令 | 说明 |
|---|---|
| `dumpnbt item` | 打印手持物品的 NBT |
| `dumpnbt block` | 打印目标方块的 NBT |
| `lppdrop eval [x y z]` | 在指定坐标评估 Lucky 掉落 |
| `lppdrop eval_dry [x y z]` | 评估但不执行 |
| `lppdrop eval_counts <n> [x y z]` | 模拟 N 次评估并打印计数 |
| `lppdrop lucky_eval [x y z]` | 运行 Lucky 掉落流水线 |
| `lppdrop lucky_eval_bulk <n> [x y z]` | 运行 Lucky 掉落流水线 N 次 |
| `lppdrop lucky_eval_bulk_dry <n> [x y z]` | 同上，但不执行 |

### 配置文件（`config/relootplusplus.json`）

首次运行时自动创建。所有字段均可通过系统属性（`-Drelootplusplus.<字段>=<值>`）或环境变量（`RELOOTPLUSPLUS_<字段>=<值>`）覆盖。

#### 核心配置

| 字段 | 默认值 | 说明 |
|---|---|---|
| `dryRun` | `false` | 仅解析模式——跳过注册与钩子安装 |
| `exportReports` | `true` | 关闭时写入诊断文件 |
| `exportRawLines` | `true` | 在 `warnings.tsv` 中包含原始源行 |
| `exportDir` | `"logs/re_lootplusplus"` | 诊断导出根目录 |
| `extraAddonDirs` | `[]` | 额外扫描的附加包目录 |
| `duplicateStrategy` | `"suffix"` | 重复 ID 处理策略：`suffix`（后缀区分）或 `ignore` |
| `potioncoreNamespace` | `"re_potioncore"` | 重映射 `potioncore:*` 效果 ID 时使用的命名空间 |
| `disabledAddonPacks` | `[]` | 加载时跳过的附加包 ID |
| `injectResourcePacks` | `true` | 将附加包 zip 的 `assets/**` 挂载为资源包 |
| `skipMissingEntityRenderers` | `false` | 抑制缺少客户端渲染器的实体报错 |

#### 日志配置

| 字段 | 默认值 | 说明 |
|---|---|---|
| `logWarnings` | `false` | 全局警告日志开关 |
| `logLegacyWarnings` | `false` | 将遗留 WARN 行输出到控制台 |
| `logDetailLevel` | `null` | 详细日志级别：`summary`、`detail` 或 `trace` |
| `logDetailFilters` | `[]` | 模块过滤列表（空 = 所有模块） |
| `legacyWarnConsoleLimitPerType` | `5` | 每种 WARN 类型的最大控制台输出条数（`0` = 不限） |
| `legacyWarnConsoleSummary` | `true` | 在启动末尾打印已抑制条数摘要 |
| `debugFileEnabled` | `true` | 启用调试日志文件（需要级别 ≥ `detail`） |
| `debugFileMaxLines` | `0` | 调试日志文件最大行数（`0` = 不限） |

#### 掉落引擎配置

| 字段 | 默认值 | 说明 |
|---|---|---|
| `luckModifier` | `0.1` | `LuckyDropRoller` 中幸运值到权重的缩放因子 |
| `defaultLuck` | `0` | 每次 Lucky 方块破坏时附加的基础幸运值 |
| `commandDropEnabled` | `true` | `false` 时跳过所有 `type=command` 类型的 Lucky 掉落 |
| `dropChatEnabled` | `true` | `false` 时抑制 Lucky 掉落的游戏内聊天消息 |

#### Tick 钩子配置

| 字段 | 默认值 | 说明 |
|---|---|---|
| `tickIntervalTicks` | `1` | 每 N 个 tick 执行一次玩家扫描 |
| `enabledTriggerTypes` | `[]` | 要运行的触发器类型（空 = 全部）：`held`、`wearing_armour`、`in_inventory`、`standing_on_block`、`inside_block` |

#### 世界与附加包配置

| 字段 | 默认值 | 说明 |
|---|---|---|
| `structureMaxDimension` | `256` | 跳过任意轴超过此尺寸的结构文件（`0` = 不限） |
| `scanModsDir` | `true` | `false` 时禁止扫描 `mods/` 目录 |
| `naturalGenEnabled` | `true` | `false` 时禁用 Lucky 方块自然生成特性注册 |
| `legacySanitizeEnabled` | `true` | `false` 时禁用遗留掉落字符串净化 |

### 游戏内 UI

`✦ Loot++` 按钮位于暂停菜单左上角，点击后进入：

- **MenuScreen** — 列出所有已发现的附加包，支持启用/禁用切换、搜索过滤与状态标记。
- **PackDetailScreen** — 标签页：概览、物品、掉落、结构、纹理。
- **DropLinesScreen** — Lucky 掉落行的可滚动列表（按类型着色）。
- **ItemDetailScreen** — 物品类型与相关掉落行引用。
- **RawLineScreen** — 含行号与语法着色的源文本查看器。
- **PackTextureGalleryScreen** — 预览附加包资源中的所有纹理。

### 诊断导出

当 `exportReports=true` 时，每次运行向 `<exportDir>/<timestamp>/` 写入：

| 文件 | 内容 |
|---|---|
| `report.json` | 结构化摘要：配置、启动统计、WARN 类型计数、附加包列表 |
| `warnings.tsv` | 所有遗留警告，每行一条，制表符分隔，含源位置 |
| `thrown.tsv` | 投掷物物品定义 |

`<exportDir>/latest.txt` 指向最新的时间戳目录。

当 `logDetailLevel=detail` 或 `trace` 且 `debugFileEnabled=true` 时，将在 `<exportDir>/` 写入 `debug-<timestamp>.log` 文件，记录所有 `Log.debug` 与 `Log.trace` 调用，不受控制台过滤级别影响。

### 文档索引

| 文件 | 内容 |
|---|---|
| `docs/STRUCTURE.md` | 包级架构：模块职责、启动序列、关键设计约束 |
| `docs/PARSER.md` | Loot++ 1.8.9 解析器规范（EBNF 语法、分隔规则、默认值、钳制逻辑） |
| `docs/ADAPTION.md` | 遗留选择器与命令语义规范 |
| `docs/INJECTION.md` | Fabric 注入/注册策略说明 |
| `docs/REFERENCE.md` | 钩子矩阵、执行顺序与示例解析追踪 |
| `docs/ADDITION.md` | 附加配置文件格式与兼容性说明 |
| `docs/PLAN.md` | 实现说明：架构摘要、配置字段、行为约束、已知缺口 |

### 常见问题排查

**Lucky 掉落无结果 / 实体被标记为已移除：**
- `minecraft:item` 实体作为 LootThrownItem 生成；若底层物品实体已被标记移除，掉落将静默失败。请检查 `lppdrop eval_dry` 输出。

**调试日志文件过大：**
- 将 `debugFileMaxLines` 设为非零值（例如 `50000`）以截断。

**附加包未加载：**
- 检查 `disabledAddonPacks`，并确认 zip 文件包含 `config/` 子文件夹。
- 使用 `logLegacyWarnings=true` 运行以在控制台查看逐行解析错误。
- 注意：游戏运行时添加的包在下次重启前不会被识别（`/reload` 不会重新运行包发现阶段）。

**规则对某物品不触发，但配置看起来正确：**
- `RuntimeIndex` 支持通配符物品 ID `"*"` —— 以 `itemId = *` 声明的规则匹配任意手持/穿戴物品。只有物品键规则支持通配符；方块键规则仅使用精确匹配。
- 验证物品的注册 ID 是否与配置中的 ID 一致（区分大小写；动态注册物品会进行点转下划线的规范化处理）。

**服务器启动提前停止：**
- 确认 `run/eula.txt` 包含 `eula=true`。
