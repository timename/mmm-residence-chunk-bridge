# mmm-residence-chunk-bridge

`mmm-residence-chunk-bridge` 是一个基于 `Residence` 的区块化领地桥接插件。

它不替代 Residence 的保护系统，而是把 Residence 的领地能力包装成更适合服务器使用的玩家流程：

- 按区块创建、扩张、缩小领地
- 自动覆盖整列高度
- 统一价格、权限、世界和面积限制
- 插件内置 Bukkit GUI
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

- MMMVaultSync，用于超过金币扩建上限后的自定义货币扣费

## 构建

```bash
mvn package
```

构建产物位于：

```text
target/mmm-residence-chunk-bridge-版本号.jar
```

## 部署

将 jar 放入生存服插件目录：

```text
E:\MCserver\MMM\purpur1.21.10_Survival\plugins
```

首次启动会生成：

```text
plugins/MMMResidenceChunkBridge/config.yml
plugins/MMMResidenceChunkBridge/lang/zh_CN.yml
plugins/MMMResidenceChunkBridge/claims.yml
plugins/MMMResidenceChunkBridge/operations.log
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
- 传送到领地
- 设置领地传送点
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

### 领地传送

```text
/mmmland tp <领地名>
```

会传送到该领地的 Residence 传送点。只能传送到自己通过本插件托管的领地。

默认普通玩家传送等待 `5` 秒，移动会取消传送。拥有配置权限的玩家可以缩短等待时间或立即传送。

### 设置领地传送点

```text
/mmmland sethome <领地名>
```

必须站在该领地范围内执行。执行后会把当前位置设置为该领地的 Residence 传送点。

### 公开传送

```text
/mmmland publictp <领地名> <on|off>
/mmmland visit <玩家名> [领地名]
```

规则：

- 玩家开启公开传送后，自己的名字会出现在 `/mmmland visit` 的 Tab 补全里
- 第一个参数只补全已开放公开传送的玩家
- 第二个参数只补全该玩家已开放的领地
- 如果目标玩家只开放了一个领地，可以省略领地名
- 公开传送复用 `teleport` 传送等待配置
- 等待期间会显示 BossBar 倒计时，移动或退出会取消传送

### 扩张领地

```text
/mmmland expand <领地名> <north|south|east|west> <区块数>
```

GUI 中扩张会先显示预览，再输入 `确认` 执行。

命令方式会直接执行。

默认扩张货币规则：

- 扩张后领地总区块数不超过 `25` 时，使用服务器默认货币（萌萌币）
- 扩张后总区块数超过 `25` 时，使用 MMMVaultSync 自管货币
- 默认自管货币 ID 为 `mengmeng_shell`，显示为萌萌贝壳
- 上限、货币 ID、显示名、单价都可以在 `config.yml` 调整

### 工具调整领地边界

```text
/mmmland resize <领地名>
```

流程：

1. 执行命令进入工具调整模式
2. 使用圈地工具左键选择起点区块
3. 右键选择终点区块
4. 新矩形会作为该领地的新边界
5. 输入 `确认` 或 `confirm` 执行
6. 输入 `取消` 或 `cancel` 退出

计费规则：

- 新边界总面积大于旧边界时，只对新增区块收费
- 新边界总面积小于或等于旧边界时，不收费且不退款
- 仍检查矩形、单领地最大区块数、中心保护区和 Residence 碰撞

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

会重新读取 `config.yml`，同时由插件代码补回配置中文注释。

### 管理员传送

```text
/mmmland admin tp <玩家名> <领地名>
```

可以传送到指定玩家的任意托管领地，不要求目标领地开启公开传送。该命令需要 `mmmland.admin`。

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

### 存储配置

```yml
storage:
  type: yaml
  mysql:
    host: 127.0.0.1
    port: 3306
    database: minecraft
    username: root
    password: "MC.123MYSQL"
    table: mmm_land_claims
    jdbc-url-override: ""
    migrate-from-yaml-if-empty: true
```

说明：

- `storage.type: yaml`：继续使用 `plugins/MMMResidenceChunkBridge/claims.yml`。
- `storage.type: mysql`：使用 MySQL 表保存托管领地元数据。
- MySQL 数据库需要提前创建，插件会自动创建表。
- `migrate-from-yaml-if-empty: true` 时，如果 MySQL 表为空，会自动从原 `claims.yml` 导入旧数据。
- `table` 是插件自动创建的表名，无需手动建表，默认 `mmm_land_claims`。`jdbc-url-override` 不为空时会优先使用完整 JDBC URL。

### 世界限制

```yml
allowed-worlds:
  - world

world-claim-rules:
  admin-bypass: true
  worlds:
    world:
      min-distance-from-origin-xz: 300
      max-distance-from-origin-xz: 0
```

`allowed-worlds` 为空时表示不限制世界；不为空时，玩家只能在列表内的世界圈地。

`world-claim-rules.worlds` 按世界名配置距离规则：

- `min-distance-from-origin-xz`：领地整体必须距离 `X=0,Z=0` 至少多少格，`0` 表示不限制。
- `max-distance-from-origin-xz`：领地最远处不能超过 `X=0,Z=0` 多少格，`0` 表示不限制。
- `admin-bypass`：管理员使用管理创建、扩张、缩小时是否绕过距离限制；世界白名单仍会生效。

距离规则按最终矩形领地范围计算，不扫描区块，性能开销可以忽略。

### 领地规则

```yml
claims:
  internal-name-prefix: "chunk"
  full-height: true
  min-chunks: 1
  max-chunks-per-claim: 64
  min-spacing-chunks: 1
  no-claim-radius-blocks: 0
  protected-center-x: 0
  protected-center-z: 0
  rectangular-only: true
  set-teleport-on-create: true
```

`min-spacing-chunks` 控制任意两个托管领地之间至少保留多少个空区块。默认 `1` 表示两个领地不能贴边或贴角，中间至少要隔 1 个完整区块；设置为 `0` 表示关闭这个限制。

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

teleport:
  default-delay-seconds: 5
  permission-delays:
    mmmland.teleport.fast: 2
    mmmland.teleport.instant: 0
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
    vault-max-chunks: 16
    progressive:
      base-price: 500
      price-increase-per-chunk: 200
    custom-currency:
      enabled: true
      id: "mengmeng_shell"
      display-name: "萌萌贝壳"
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
使用服务器默认货币（萌萌币）收费

扩张后总区块数 > pricing.expand.vault-max-chunks：
使用 MMMVaultSync 自管货币收费

扩建第 1 个区块价格 = pricing.expand.progressive.base-price
之后每个扩建区块价格比上一个增加 pricing.expand.progressive.price-increase-per-chunk

默认从 1 区块扩到 4 区块：
500 + 700 + 900 = 2100
```

缩小默认不返还货币。

### 语言文件

```text
plugins/MMMResidenceChunkBridge/lang/zh_CN.yml
```

玩家提示、GUI 标题和帮助文本都在语言文件中维护。`config.yml` 只保留功能配置和价格配置。

### 传送延迟

```yaml
teleport:
  default-delay-seconds: 5
  permission-delays:
    mmmland.teleport.fast: 2
    mmmland.teleport.instant: 0
```

规则：

- 普通玩家使用 `default-delay-seconds`
- 玩家拥有多个传送延迟权限时，取最短时间
- 延迟为 `0` 时立即传送
- 等待期间移动或退出会取消传送

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

## 菜单实现

当前插件使用内置 Bukkit GUI，不再自动生成 Invero 菜单入口。

核心业务由 Java 插件处理，包括：

- 粒子选区
- 聊天确认
- Residence 碰撞检测
- 经济扣费
- 数据保存

## 更新记录

### 0.22.0

类型：功能新增

新增/调整：

- 领地详情菜单的传送权限和移动权限直接读取 Residence 当前 flag，高级配置界面修改后重新打开菜单即可同步显示
- 传送权限、移动权限快捷按钮支持左键修改全局权限，右键输入玩家名修改单个玩家权限
- 成员权限菜单新增“查看玩家权限详情”，可查看该领地所有玩家的单独权限记录
- 公开传送列表改为按 Residence 当前 `tp` flag 判断，减少插件记录与 Residence 权限不一致的问题
### 0.21.0

类型：功能新增

新增：

- 领地详情菜单新增“领地高级配置”入口
- 点击后打开领地权限与规则配置界面
### 0.20.1

类型：Bug 修复

修复：

- 可视化工具自定义范围创建领地时，费用改为与扩建/调整边界相同的分段货币逻辑
- 创建大范围领地超过金币区块上限后，正确显示并扣除自管货币萌萌贝壳
- 开启/关闭公开传送时同步设置 Residence 的 `tp` flag，避免第三方 `/tpa` 提示成功但被 Residence 阻止
### 0.20.0

类型：功能新增

新增/调整：

- MySQL 配置改为 `host`、`port`、`database`、`username`、`password`、`table` 的直观结构
- 保留 `jdbc-url-override`，需要完整 JDBC URL 时仍可覆盖自动拼接结果
- 兼容读取旧版 `storage.mysql.jdbc-url` 和 `storage.mysql.table-prefix`
- 默认配置说明补充“插件会自动创建表，无需手动建表”

### 0.19.0

类型：功能新增与视觉优化

调整：

- 领地边界粒子预览新增多层 Y 轴水平边框显示，边界不再只显示玩家脚下单层 Y 轴
- 新增 `visual.preview-vertical-levels` 和 `visual.preview-vertical-step-blocks` 默认配置
- 新增 `visual.selection.vertical-levels`、`visual.selection.vertical-step-blocks` 控制手动选区边界层数
- 新增 `visual.current-chunk.vertical-levels`、`visual.current-chunk.vertical-step-blocks` 控制当前区块提示层数
### 0.18.0

类型：功能新增与计费规则调整

调整：

- 扩建和工具调整边界支持同一次操作拆分显示多种货币费用
- 默认萌萌币只支付到第 16 个扩建区块，超过后改用 `mengmeng_shell`（萌萌贝壳）
- 萌萌贝壳默认递增消耗为第 1 个区块 10、第 2 个 20、第 3 个 30，以此类推
- 新增 `pricing.expand.custom-currency.progressive.base-price` 和 `price-increase-per-chunk` 配置
- 扩建预览、确认、成功和余额不足提示会显示完整费用摘要，例如 `萌萌币 + 萌萌贝壳`
### 0.17.4

类型：Bug 修复与配置调整

调整：

- 修复工具调整领地边界时错误复用创建选区校验，导致多余显示“已有同名领地”等提示的问题
- 修复工具调整边界预览时费用和面积变化显示不稳定的问题
- 工具调整边界会稳定使用 Residence 内部领地名执行，界面提示继续显示玩家领地名
- 默认圈地工具改回 `GOLDEN_SHOVEL`，并同步菜单图标与配置说明
### 0.17.3

类型：Bug 修复与配置调整

调整：

- 明确“萌萌币”为服务器默认货币，不作为 MMMVaultSync 额外自管货币注册
- 配置注释统一将 25 区块内扩建货币描述为“服务器默认货币”
- 保持超过 25 区块后的扩建自管货币为 `mengmeng_shell`（萌萌贝壳）

### 0.17.2

类型：Bug 修复与配置调整

调整：

- Vault 默认货币显示名改为“萌萌币”，作为服务器默认货币展示
- 保持超过 25 区块后的扩建自管货币为 `mengmeng_shell`（萌萌贝壳）

### 0.17.1

类型：Bug 修复与配置调整

调整：

- 扩张领地超过 `pricing.expand.vault-max-chunks` 后使用的自管货币改为 `mengmeng_shell`
- 玩家显示名改为“萌萌贝壳”
- 默认配置与 README 同步新的货币 ID

### 0.17.0

类型：功能新增

新增：

- 新增 `claims.min-spacing-chunks` 配置
- 创建、扩建、缩小、工具调整边界和管理员调整都会检查领地间距
- 默认要求任意两个托管领地之间至少间隔 1 个空区块

说明：

- 该检查基于插件托管领地缓存，不扫描世界方块
- 当前正在调整的同一个领地会被排除

### 0.16.0

类型：功能新增

新增：

- `claims.yml` 托管领地数据支持切换为 MySQL 存储
- 新增 `storage.type`，可选 `yaml` 或 `mysql`
- MySQL 启动时自动创建托管领地表
- MySQL 表为空时可自动从 `claims.yml` 导入旧数据
- `plugin.yml` 增加 MySQL JDBC 驱动库声明

保留：

- 默认仍使用 `yaml`，不会影响现有服务器
- 配置文件夹仍固定为 `plugins/MMMResidenceChunkBridge`

### 0.15.5

类型：Bug 修复与命名调整

调整：

- jar 文件名改为 `mmm-residence-chunk-bridge-版本号.jar`
- jar 文件名和 Bukkit 插件名保持一致
- 配置文件夹仍固定使用 `plugins/MMMResidenceChunkBridge`

### 0.15.4

类型：Bug 修复与命名调整

调整：

- 保持 Bukkit 插件名为 `mmm-residence-chunk-bridge`
- 插件 jar 文件继续使用 `MMMResidenceChunkBridge-版本号.jar`
- 插件配置文件夹固定使用 `plugins/MMMResidenceChunkBridge`
- 插件内部配置、语言、claims 和日志读取全部改为固定数据目录，避免 Bukkit 默认按插件名生成连字符目录

### 0.15.3

类型：Bug 修复与命名调整

调整：

- Bukkit 插件名改为 `mmm-residence-chunk-bridge`
- 构建 jar 名恢复为 `MMMResidenceChunkBridge-版本号.jar`
- 启动时会尝试把旧数据目录 `plugins/mmmResidenceChunkBridge` 或 `plugins/MMMResidenceChunkBridge` 迁移为 `plugins/mmm-residence-chunk-bridge`

### 0.15.2

类型：Bug 修复与命名调整

调整：

- 插件名从 `MMMResidenceChunkBridge` 改为 `mmmResidenceChunkBridge`
- 构建 jar 名改为 `mmmResidenceChunkBridge-版本号.jar`
- 启动时会尝试把旧数据目录 `plugins/MMMResidenceChunkBridge` 迁移为 `plugins/mmmResidenceChunkBridge`

### 0.15.1

类型：Bug 修复与清理

调整：

- 移除旧的 Invero 菜单入口自动导出逻辑
- 移除 `Invero` 软依赖和内置 `MMM领地入口.yml` 模板
- 文档改为说明当前菜单由插件内置 Bukkit GUI 提供

### 0.15.0

类型：功能新增

新增：

- 支持按世界白名单限制玩家圈地世界
- 支持按世界配置距离 `X=0,Z=0` 的最小圈地距离
- 支持按世界配置距离 `X=0,Z=0` 的最大圈地距离
- 管理员创建、扩张、缩小领地可通过配置选择是否绕过距离限制

调整：

- 创建、可视化选区、扩张、缩小、重选边界和管理员调整都会校验最终矩形范围
- 配置文件增加中文注释和玩家提示文案

### 0.14.1

类型：Bug 修复

新增：

- 管理员命令 `/mmmland admin tp <玩家名> <领地名>`
- 管理员传送不受公开传送限制
- 管理员传送支持 Tab 补全玩家名和该玩家的托管领地名

文档：

- 插件帮助和 README 补充管理员传送说明

### 0.14.0

类型：功能新增

新增：

- `/mmmland publictp <领地名> <on|off>` 开启或关闭领地公开传送
- `/mmmland visit <玩家名> [领地名]` 访问其他玩家公开的领地
- `/mmmland visit` Tab 补全只显示已开放公开传送的玩家
- `/mmmland visit <玩家名>` 第二段只补全该玩家公开的领地
- 领地详情 GUI 增加公开传送开关
- 传送等待期间显示 BossBar 倒计时

调整：

- `claims.yml` 增加 `public-teleport`
- 修正 Residence 进入提示中领地主人后多出的 `%`

### 0.13.2

类型：Bug 修复

修复：

- 托管领地创建后自动写入中文 Residence 进入/离开提示
- 插件启动和 `/mmmland reload` 时会同步所有托管领地的 Residence 提示
- 进入提示使用本插件显示名，不再显示 `chunk_...` 内部领地名
- 重命名领地显示名后同步更新 Residence 进入/离开提示

### 0.13.1

类型：Bug 修复与界面调整

修复：

- 传送等待结束后不再执行 `/res tp`
- 改为直接读取 Residence 传送点并由本插件执行传送，避免触发 Residence 自带二次等待

调整：

- 领地详情菜单重新排版
- 第一行放传送和设置传送点
- 第二行放预览、调整、扩张、缩小、成员权限
- 第三行放重命名提示和删除

### 0.13.0

类型：功能新增

新增：

- 领地传送等待时间配置：
  - `teleport.default-delay-seconds`
  - `teleport.permission-delays`
- 默认普通玩家传送等待 `5` 秒
- 权限 `mmmland.teleport.fast` 默认等待 `2` 秒
- 权限 `mmmland.teleport.instant` 默认立即传送

规则：

- 玩家拥有多个传送权限时，使用最短等待时间
- 等待传送期间移动或退出会取消传送

### 0.12.0

类型：功能新增

新增：

- 玩家命令 `/mmmland tp <领地名>`，传送到自己的托管领地
- 玩家命令 `/mmmland sethome <领地名>`，设置自己的领地传送点
- 领地详情 GUI 增加“传送到领地”和“设置传送点”按钮

规则：

- 只能操作自己通过本插件托管的领地
- 设置传送点时，玩家必须站在该领地范围内
- 设置传送点使用 Residence 的传送点 API，传送动作走 Residence 自带传送命令

### 0.11.1

类型：Bug 修复

修复：

- 配置中文注释改为由插件代码维护
- 启动和 `/mmmland reload` 会重新写回配置注释，避免配置被 Bukkit 保存后注释丢失
- 保留当前配置值，只补回注释和文件头说明

### 0.11.0

类型：功能新增

新增：

- 语言文件独立为 `lang/zh_CN.yml`
- `config.yml` 增加完整中文注释
- `/mmmland reload` 会同时重载配置和语言文件
- 扩建价格改为递增计价：
  - 第一个扩建区块默认 `500`
  - 每个后续扩建区块默认比上一个贵 `200`
  - 配置项为 `pricing.expand.progressive.base-price`
  - 配置项为 `pricing.expand.progressive.price-increase-per-chunk`

调整：

- 工具调整边界、命令扩建和 GUI 预计费用统一使用递增计价
- 保留旧 `messages.*` 读取兜底，降低旧配置升级风险
- 启动和 `/mmmland reload` 默认不依赖旧 `messages.*` 配置

### 0.10.0

类型：功能新增

新增：

- `/mmmland resize <领地名>`
- 玩家可以继续使用圈地工具重新选择已有领地的新矩形边界
- 领地详情 GUI 增加“工具调整边界”按钮
- 工具调整时会显示新边界粒子预览、面积变化和预计费用

规则：

- 新边界面积增加时，只对新增区块收费
- 新边界面积减少或不变时，不退款
- 新边界仍受矩形、最大区块数、中心保护区和 Residence 碰撞限制

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

- 扩张领地超过金币区块上限后的自管货币改为 MMMVaultSync 的 `mengmeng_shell`
- 玩家显示名改为“萌萌贝壳”
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
- 默认 `pricing.expand.vault-max-chunks` 为 `16`
- 扩建后总区块数不超过 `25` 时使用服务器默认货币，超过后使用 MMMVaultSync 自管货币
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
- PlaceholderAPI 扩展，供其他菜单或计分板显示领地数量、上限、价格和选区状态
- 历史底层领地（Residence）导入工具
- 管理员分页查看和搜索领地
- 将创建、选区、扩张、缩小、删除确认进一步统一到独立会话服务
