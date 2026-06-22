# MMMResidenceChunkBridge

`MMMResidenceChunkBridge` 是一个基于 `Residence` 的区块化领地桥接插件。

它不替代 Residence 的保护系统，而是把 Residence 的领地能力包装成更适合服务器使用的玩家流程：

- 按区块创建、扩张、缩小领地
- 自动覆盖整列高度
- 统一价格、权限、世界和面积限制
- Bukkit GUI + Invero 入口
- 可视化粒子选区
- 聊天确认，减少误操作
- 本插件维护托管领地元数据

## 版本规则

版本号格式为 `x.y.z`。

- `x`：重大架构变更
- `y`：新增功能
- `z`：Bug 修复

每次修改插件后必须同步更新：

- `pom.xml`
- `src/main/resources/plugin.yml`
- 本文档的“更新记录”

## 依赖

必需：

- Purpur/Paper `1.21.x`
- Residence `6.0.0.1`
- Vault
- 经济插件

可选：

- Invero，用于服务器统一菜单入口
- MMMVaultSync，用于超过金币扩建上限后的自定义货币扣费

## 构建

```bash
mvn package
```

构建产物位于：

```text
target/MMMResidenceChunkBridge-版本号.jar
```

## 部署

将 jar 放入生存服插件目录：

```text
E:\MCserver\MMM\purpur1.21.10_Survival\plugins
```

首次启动会生成或补全：

```text
plugins/MMMResidenceChunkBridge/config.yml
plugins/MMMResidenceChunkBridge/claims.yml
plugins/MMMResidenceChunkBridge/operations.log
```

如果检测到 Invero，会自动导出入口菜单到：

```text
plugins/Invero/workspace/MMM领地入口.yml
```

## 玩家功能

### 打开菜单

```text
/mmmland menu
```

菜单中可以：

- 创建当前区块领地
- 启动可视化选区圈地
- 查看我的领地
- 扩张领地
- 缩小领地
- 管理成员权限
- 删除领地

### 创建当前区块领地

```text
/mmmland create [显示名]
```

规则：

- 默认创建玩家当前所在区块
- 自动覆盖世界最低高度到最高高度
- 创建前检查世界、数量上限、中心保护区、Residence 碰撞和余额

### 可视化选区圈地

```text
/mmmland select [显示名]
```

默认流程：

1. 手持 `GOLDEN_SHOVEL`
2. 左键方块选择起点区块
3. 右键方块选择终点区块
4. 粒子显示矩形边界
5. 输入 `确认` 或 `confirm` 创建
6. 输入 `取消` 或 `cancel` 退出

粒子颜色：

- 黄色/特殊粒子：玩家当前脚下区块
- 绿色：当前选区可创建
- 红色：当前选区不可创建

选区形状固定为矩形。玩家左键选择起点区块、右键选择终点区块后，插件会自动取两个点之间的矩形范围。

### 确认与取消

```text
/mmmland confirm
/mmmland cancel
```

可用于当前可视化选区。

### 查看领地

```text
/mmmland list
```

### 重命名显示名

```text
/mmmland rename <旧显示名> <新显示名>
```

只修改本插件显示名，不修改 Residence 内部领地名。

### 扩张领地

```text
/mmmland expand <领地名> <north|south|east|west> <区块数>
```

GUI 中扩张会先显示预览，再输入 `确认` 执行。

命令方式会直接执行。

默认扩张货币规则：

- 扩张后领地总区块数不超过 `25` 时，使用 Vault 金币
- 扩张后总区块数超过 `25` 时，使用 MMMVaultSync 自管货币
- 默认自管货币 ID 为 `mengmeng_crystal`，显示为萌萌水晶
- 上限、货币 ID、显示名、单价都可以在 `config.yml` 调整

### 缩小领地

```text
/mmmland contract <领地名> <north|south|east|west> <区块数>
```

GUI 中缩小会先显示预览，再输入 `确认` 执行。

命令方式会直接执行。

### 删除领地

```text
/mmmland delete <领地名>
```

### 成员权限

```text
/mmmland trust <领地名> <玩家名>
/mmmland untrust <领地名> <玩家名>
/mmmland deny <领地名> <玩家名>
/mmmland undeny <领地名> <玩家名>
```

效果：

- `trust`：信任玩家，授予 Residence 的 `trusted` 权限模板
- `untrust`：移除该玩家的 `trusted` 权限模板
- `deny`：禁止玩家进入，设置 `move=false` 和 `tp=false`
- `undeny`：解除禁止进入，移除该玩家的 `move` 和 `tp` 单独限制

GUI 中进入“我的领地” -> 选择领地 -> “成员权限”，选择操作后在聊天栏输入玩家名。

## 管理员功能

需要权限：

```text
mmmland.admin
```

### 重载配置

```text
/mmmland reload
```

会重新读取 `config.yml`，并自动补齐新增默认配置项。

### 查看托管领地

```text
/mmmland admin list
```

列出本插件托管的所有领地。

### 自检托管数据

```text
/mmmland admin check
```

检查 `claims.yml` 中记录的底层领地（Residence）是否仍然存在。

### 清理失效托管记录

```text
/mmmland admin clean
```

删除那些本插件有记录、但 Residence 中已经不存在的托管记录。

### 管理玩家领地

管理员操作不扣除玩家货币，但仍会检查世界、矩形、面积上限和 Residence 碰撞。

在管理员当前所在区块为玩家新增领地：

```text
/mmmland admin create <玩家名> [显示名]
```

扩大指定玩家的领地：

```text
/mmmland admin expand <玩家名> <领地名> <北|南|东|西> <区块数>
```

缩小指定玩家的领地：

```text
/mmmland admin contract <玩家名> <领地名> <北|南|东|西> <区块数>
```

删除指定玩家的托管领地：

```text
/mmmland admin delete <玩家名> <领地名>
```

### 强制删除托管领地

按 Residence 内部领地名删除，适合处理显示名冲突或数据修复：

```text
/mmmland admin forcedelete <内部领地名>
```

会删除底层领地（Residence）和本插件托管记录。

## 权限

```text
mmmland.use
mmmland.admin
mmmland.limit.1
mmmland.limit.2
mmmland.limit.4
mmmland.limit.8
```

说明：

- `mmmland.use`：允许使用玩家命令
- `mmmland.admin`：允许使用管理员工具
- `mmmland.limit.*`：控制玩家最大领地数，取玩家拥有权限中的最大值

## 主要配置

### 世界限制

```yml
allowed-worlds:
  - world
```

为空时表示不限制世界。

### 领地规则

```yml
claims:
  internal-name-prefix: "chunk"
  full-height: true
  min-chunks: 1
  max-chunks-per-claim: 64
  no-claim-radius-blocks: 0
  protected-center-x: 0
  protected-center-z: 0
  rectangular-only: true
  set-teleport-on-create: true
```

### 可视化选区

```yml
visual:
  preview-duration-ticks: 200
  preview-period-ticks: 6
  preview-step-blocks: 1
  preview-corner-height: 8
  preview-dust-size: 1.6
  current-chunk-enabled: true

selection:
  tool: GOLDEN_SHOVEL
  require-tool: true
  timeout-seconds: 120
  preview-period-ticks: 10
```

### 价格

```yml
pricing:
  create:
    fallback-last-tier: true
    price-per-extra-chunk: 500
    tiers:
      1: 0
      2: 1000
      3: 2000
      4: 4000
  expand:
    price-per-chunk: 500
    vault-max-chunks: 25
    custom-currency:
      enabled: true
      id: "mengmeng_crystal"
      display-name: "萌萌水晶"
      price-per-chunk: 1
  contract:
    refund-enabled: false
```

创建价格：

```text
当前第 N 个领地基础价 + 额外区块数 * price-per-extra-chunk
```

扩张价格：

```text
扩张后总区块数 <= pricing.expand.vault-max-chunks：
新增区块数 * pricing.expand.price-per-chunk，使用 Vault 金币

扩张后总区块数 > pricing.expand.vault-max-chunks：
新增区块数 * pricing.expand.custom-currency.price-per-chunk，使用 MMMVaultSync 自管货币
```

缩小默认不返还货币。

### 可视化配置

```yaml
visual:
  selection:
    step-blocks: 1
    corner-height: 8
    dust-size: 1.6
    accent-enabled: true
    accent-particle: END_ROD
  current-chunk:
    color: "255,220,40"
    step-blocks: 2
    corner-height: 4
    dust-size: 1.1
    accent-enabled: true
    accent-particle: HAPPY_VILLAGER
```

`selection` 控制已选择范围的边界，`current-chunk` 控制玩家脚下当前区块提示。两者分开后，玩家离开已选范围时也能区分“当前位置提示”和“已选矩形范围”。

## 数据文件

### claims.yml

保存本插件托管的领地元数据：

- Residence 内部领地名
- 显示名
- 玩家 UUID
- 玩家名
- 世界名
- 区块边界

本插件只管理 `claims.yml` 中存在的领地。

### operations.log

记录领地操作日志：

- 创建
- 扩张
- 缩小
- 删除
- 管理员强删
- 管理员清理失效记录

## Invero 集成

Invero 只作为入口和静态按钮菜单，不承载核心业务状态。

当前插件启动时会导出：

```text
MMM领地入口.yml
```

按钮会调用：

```text
/mmmland menu
/mmmland select
/mmmland cancel
```

核心业务仍由 Java 插件处理，包括：

- 粒子选区
- 聊天确认
- Residence 碰撞检测
- 经济扣费
- 数据保存

## 更新记录

### 0.9.0

类型：功能新增

新增：

- 玩家命令：
  - `/mmmland trust <领地名> <玩家名>`
  - `/mmmland untrust <领地名> <玩家名>`
  - `/mmmland deny <领地名> <玩家名>`
  - `/mmmland undeny <领地名> <玩家名>`
- 领地详情 GUI 增加“成员权限”入口
- 成员权限 GUI 支持信任、取消信任、禁止进入、解除禁止
- 成员权限操作写入 `operations.log`

实现：

- 信任/取消信任通过 Residence 的 `trusted` 玩家权限模板实现
- 禁止进入通过 Residence 的 `move` 和 `tp` 玩家权限实现

### 0.8.0

类型：功能调整

调整：

- 扩张领地超过金币区块上限后的自管货币改为 MMMVaultSync 的 `mengmeng_crystal`
- 玩家显示名改为“萌萌水晶”
- 默认配置和运行服配置同步更新

### 0.7.0

类型：功能新增

新增：

- 管理员可为玩家新增托管领地：
  - `/mmmland admin create <玩家名> [显示名]`
- 管理员可扩大指定玩家领地：
  - `/mmmland admin expand <玩家名> <领地名> <北|南|东|西> <区块数>`
- 管理员可缩小指定玩家领地：
  - `/mmmland admin contract <玩家名> <领地名> <北|南|东|西> <区块数>`
- 管理员可删除指定玩家领地：
  - `/mmmland admin delete <玩家名> <领地名>`
- 原内部名强删保留为：
  - `/mmmland admin forcedelete <内部领地名>`
- 管理员操作写入 `operations.log`

规则：

- 管理员新增、扩大、缩小不扣费
- 仍检查矩形、单领地总区块上限、世界限制、中心保护区和 Residence 碰撞

### 0.6.0

类型：功能调整

调整：

- 金币扩建上限从宽深判断改为总区块数判断
- 默认 `pricing.expand.vault-max-chunks` 为 `25`
- 扩建后总区块数不超过 `25` 时使用 Vault 金币，超过后使用 MMMVaultSync 自管货币
- GUI 价格说明改为显示总区块数阈值
- 创建、扩张、缩小前显式校验领地边界必须是有效矩形
- 新增提示 `messages.rectangle-only`

### 0.5.0

类型：功能新增

新增：

- 玩家脚下当前区块预览和已选区块预览使用独立粒子样式
- 当前区块预览增加独立颜色、密度、光柱高度和高亮粒子配置
- 可视化选区文案明确为矩形选区
- 扩建后领地超过配置宽深上限时，切换为 MMMVaultSync 自管货币扣费
- 默认金币扩建上限为 `5x5` 宽深判断
- 新增扩建配置：
  - `pricing.expand.vault-max-width`
  - `pricing.expand.vault-max-depth`
  - `pricing.expand.custom-currency.enabled`
  - `pricing.expand.custom-currency.id`
  - `pricing.expand.custom-currency.display-name`
  - `pricing.expand.custom-currency.price-per-chunk`
- GUI 扩建预计费用会按扩建后尺寸显示金币或自定义货币

调整：

- 默认配置文件重新整理为 UTF-8 中文文案
- `plugin.yml` 增加 `MMMVaultSync` 软依赖

### 0.4.0

类型：功能新增

新增：

- 插件自带 Bukkit 菜单改为 6 行大菜单
- 菜单使用蓝色、淡蓝色、品红玻璃片边框，风格贴近服务器 Invero 菜单
- 领地列表改为中间内容区显示，外围保留玻璃装饰
- 粒子预览增强，默认持续时间提高到 10 秒
- 粒子边框密度提高，默认每格显示
- 预览四角增加更高的光柱效果
- 粒子边框增加 `END_ROD` 高亮粒子，夜晚更明显
- 可视化选区时持续显示玩家脚下当前区块
- 增加粒子配置项：
  - `visual.preview-period-ticks`
  - `visual.preview-corner-height`
  - `visual.preview-dust-size`
  - `visual.current-chunk-enabled`

调整：

- 玩家可见方向参数优先使用中文：北、南、东、西
- 保留 `north/south/east/west` 兼容
- 插件命令描述改为中文
- 玩家可见的底层领地提示尽量汉化
- GUI 硬编码文案统一整理为中文

### 0.3.0

类型：功能新增

新增：

- 可视化区块选区圈地
- 创建面积价格，支持 `price-per-extra-chunk`
- 单个领地最大区块数限制
- 选区工具限制、选区超时和粒子刷新配置
- 扩张/缩小 GUI 预览后聊天确认
- `VisualService` 统一粒子边框绘制
- `/mmmland reload`
- `/mmmland admin list`
- `/mmmland admin check`
- `/mmmland admin clean`
- `/mmmland admin delete <内部领地名>`
- `operations.log` 操作日志
- Invero 入口增加可视化选区和取消选区按钮

调整：

- 单区块菜单创建确认后固定使用预览时的区块范围，避免确认前移动导致创建位置变化
- 可视化选区定时预览使用缓存校验结果，减少重复 Residence 碰撞检测
- 启动和 reload 时自动补齐新增默认配置

### 0.2.2

类型：Bug 修复与交互流程修正

新增/修复：

- 创建时关闭菜单
- 创建前粒子预览
- 聊天输入 `确认` 后创建
- 输入 `取消` 可退出创建确认
- 离开当前预览区块自动取消
- 修复部分中文文本
- 统一菜单基础风格

## 后续计划

优先级较高：

- 重命名流程菜单化
- PlaceholderAPI 扩展，供 Invero 显示领地数量、上限、价格和选区状态
- 历史底层领地（Residence）导入工具
- 管理员分页查看和搜索领地
- 将创建、选区、扩张、缩小、删除确认进一步统一到独立会话服务
