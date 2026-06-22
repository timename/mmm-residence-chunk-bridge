# MMMResidenceChunkBridge 说明文档

## 1. 插件定位

`MMMResidenceChunkBridge` 是一个依赖 `Residence 6.0.0.1` 的上层业务插件。

它的核心目标不是自己重写一套完整的领地保护系统，而是：

- 继续使用 `Residence` 作为底层领地与保护引擎
- 由本插件负责“服务器规则约束 + 玩家交互流程 + 菜单体验 + 价格体系 + 领地元数据管理”
- 把原本偏命令化、自由度过高的领地系统，包装成更适合本服使用的“区块化圈地系统”

可以把它理解为：

- `Residence` 负责真正的领地创建、碰撞检测、保护规则、传送点等底层能力
- `MMMResidenceChunkBridge` 负责把玩家的操作转译成符合本服规则的 `Residence` 调用

## 2. 当前设计思路

当前采用的是“桥接层”方案，而不是“独立领地系统”方案。

这样做的原因：

- 不重复造轮子，直接复用 `Residence` 已经成熟的保护逻辑
- 兼容原有 Residence 生态和已有数据结构
- 出问题时可以直接定位到底层保护问题还是上层业务问题
- 更适合后续继续接入货币插件、权限插件等服内系统

当前整体分层如下：

- `Residence`
  - 底层领地创建、删除、碰撞检测、区域替换、领地传送点
- `Vault`
  - 经济接口，负责扣费
- `MMMResidenceChunkBridge`
  - 负责价格规则、世界限制、区块限制、菜单流程、确认流程、元数据记录

## 3. 当前已实现的业务规则

### 3.1 圈地世界限制

只允许在配置指定的世界圈地。

当前配置项：

```yml
allowed-worlds:
  - world
```

如果玩家不在允许世界中，不能创建领地。

### 3.2 圈地单位

领地最小单位是“区块”。

当前规则：

- 新建领地默认是玩家当前所在区块
- 扩张与缩小的最小单位也是区块
- 当前只支持矩形区块领地

### 3.3 高度规则

玩家圈地不限制手动选择 Y 轴。

当前实现是：

- 领地自动覆盖世界最小高度到世界最大高度
- 即创建的是整列领地

### 3.4 领地价格规则

当前默认价格规则为：

- 第 1 个领地免费
- 第 2 个领地 `1000` 奶油币
- 第 3 个领地 `2000` 奶油币
- 第 4 个领地 `4000` 奶油币

配置位置：

```yml
pricing:
  create:
    fallback-last-tier: true
    tiers:
      1: 0
      2: 1000
      3: 2000
      4: 4000
```

其中：

- `tiers` 表示第几个领地对应的价格
- `fallback-last-tier: true` 表示如果玩家领地数量超过已配置阶梯，则继续沿用最后一档价格

### 3.5 扩张价格规则

扩张价格按照新增区块数计算。

当前默认：

```yml
pricing:
  expand:
    price-per-chunk: 500
```

即：

- 每增加 1 个区块，收费 `500` 奶油币

### 3.6 缩小规则

缩小领地不会返还货币。

当前默认：

```yml
pricing:
  contract:
    refund-enabled: false
```

目前这个配置已经保留，但当前实现本身就是“不返还”。

### 3.7 领地上限规则

领地上限支持按权限控制。

当前配置：

```yml
limits:
  default-max-claims: 4
  permission-max-claims:
    mmmland.limit.1: 1
    mmmland.limit.2: 2
    mmmland.limit.4: 4
    mmmland.limit.8: 8
```

逻辑是：

- 玩家默认上限为 `default-max-claims`
- 如果玩家拥有更高上限权限，则取最大值

### 3.8 服务器中心保护范围

支持限制服务器地图中心附近不可创建领地。

相关配置：

```yml
claims:
  no-claim-radius-blocks: 0
  protected-center-x: 0
  protected-center-z: 0
```

说明：

- `protected-center-x` 和 `protected-center-z` 是保护中心点
- `no-claim-radius-blocks` 是禁止圈地的半径，单位为方块
- 当领地区域与这个圆形保护区相交时，不允许创建

## 4. 玩家操作流程

## 4.1 打开菜单

玩家可通过以下方式打开菜单：

- 执行 `/mmmland menu`

当前菜单由插件内置 Bukkit GUI 提供。

## 4.2 创建领地

当前创建流程如下：

1. 玩家打开领地主菜单
2. 点击“创建当前区块领地”
3. 插件关闭菜单
4. 插件显示当前区块的粒子预览
5. 聊天栏提示玩家输入 `确认` 或 `confirm`
6. 玩家仍处于该预览范围内时，输入确认
7. 插件再次执行创建前校验
8. 校验通过后扣费并调用 `Residence` 创建领地
9. 写入本插件自己的领地元数据

当前取消逻辑：

- 输入 `取消` 或 `cancel`，取消本次创建
- 玩家离开当前预览区块范围，自动取消
- 玩家退出服务器，自动取消

## 4.3 查看领地

玩家在“我的领地”菜单中可以查看：

- 显示名
- 内部名
- 所在世界
- 区块范围

## 4.4 预览领地

玩家在领地详情菜单中，可以点击“预览领地边界”。

当前预览效果：

- 使用粒子沿矩形边界绘制
- 四角额外使用明显的粒子标记
- 仅对当前玩家显示

## 4.5 扩张领地

当前操作流程：

1. 打开某个领地详情
2. 点击“扩张领地”
3. 选择方向
4. 选择区块数
5. 插件先显示扩张后的粒子预览
6. 然后执行碰撞检测、余额检测、扣费、区域替换

支持方向：

- `north`
- `south`
- `east`
- `west`

## 4.6 缩小领地

当前操作流程与扩张类似：

1. 打开领地详情
2. 点击“缩小领地”
3. 选择方向
4. 选择区块数
5. 显示缩小后的预览
6. 替换 `Residence` 区域

限制：

- 缩小后不能低于最小区块数
- 缩小后宽或长不能为 0
- 缩小不返还货币

## 4.7 删除领地

当前删除流程：

1. 打开领地详情
2. 点击“删除领地”
3. 进入删除确认菜单
4. 玩家确认后删除 `Residence` 领地并删除本插件记录

## 4.8 重命名领地

当前重命名通过命令进行：

```text
/mmmland rename <旧显示名> <新显示名>
```

当前只修改本插件中的显示名，不修改 `Residence` 的内部领地名。

## 5. 当前命令

```text
/mmmland menu
/mmmland list
/mmmland create [显示名]
/mmmland rename <旧显示名> <新显示名>
/mmmland expand <领地名> <north|south|east|west> <区块数>
/mmmland contract <领地名> <north|south|east|west> <区块数>
/mmmland delete <领地名>
/mmmland help
```

## 6. 当前权限

```text
mmmland.use
mmmland.admin
mmmland.limit.1
mmmland.limit.2
mmmland.limit.4
mmmland.limit.8
```

说明：

- `mmmland.use` 允许使用插件
- `mmmland.admin` 用于管理权限扩展预留，当前主要用于部分所有权绕过逻辑
- `mmmland.limit.*` 用于控制玩家最大领地数

## 7. 当前实现结构

## 7.1 核心入口

文件：

- `src/main/java/local/mmm/residencechunk/MMMResidenceChunkBridgePlugin.java`

职责：

- 读取配置
- 初始化数据存储
- 连接 `Vault`
- 检查 `Residence` 是否启用
- 初始化业务服务
- 注册命令与事件

## 7.2 业务核心

文件：

- `src/main/java/local/mmm/residencechunk/service/LandService.java`

职责：

- 领地创建前校验
- 创建领地
- 扩张与缩小逻辑
- 删除逻辑
- 重命名逻辑
- 价格计算
- 世界限制校验
- 中心保护范围校验
- 领地归属校验

这个类是当前最核心的业务层。

## 7.3 菜单与交互层

文件：

- `src/main/java/local/mmm/residencechunk/service/GuiService.java`

职责：

- Bukkit GUI 菜单构建
- 玩家点击菜单后的流程分发
- 粒子预览显示
- 创建确认状态管理
- 聊天确认监听
- 玩家移动越界取消确认

当前“聊天确认圈地”的主要逻辑在这个类中。

## 7.4 Residence 反射桥

文件：

- `src/main/java/local/mmm/residencechunk/service/ResidenceHook.java`

职责：

- 通过反射调用 `Residence` 的底层 API
- 获取 `ResidenceManager`
- 创建 `CuboidArea`
- 检查碰撞
- 添加领地
- 替换区域
- 删除领地

之所以采用反射而不是强耦合直接调用，是为了减少类加载和兼容问题。

## 7.5 数据存储

文件：

- `src/main/java/local/mmm/residencechunk/service/LandDataStore.java`

职责：

- 管理 `claims.yml`
- 保存由本插件创建的领地元数据

这里保存的是上层业务数据，不替代 `Residence` 自己的底层数据。

保存内容主要包括：

- `Residence` 内部领地名
- 显示名
- 玩家 UUID
- 玩家名
- 世界名
- 区块边界

## 7.6 命令层

文件：

- `src/main/java/local/mmm/residencechunk/command/LandCommand.java`

职责：

- 处理 `/mmmland` 命令
- 参数校验
- 调用 `LandService` 或 `GuiService`

## 7.7 配置解析

文件：

- `src/main/java/local/mmm/residencechunk/config/PluginSettings.java`

职责：

- 从 `config.yml` 读取并整理配置
- 提供类型安全的配置访问

## 8. 为什么使用内置 Bukkit GUI

领地功能需要维护大量实时状态和服务端校验，直接使用 Java 代码实现更可控。

例如下面这些逻辑需要由插件直接处理：

- 圈地前多重校验
- 与 `Residence` 的深度 API 对接
- 玩家创建待确认状态
- 聊天确认
- 玩家离开范围自动取消
- 扩张缩小后的区块边界计算
- 数据记录与显示名映射

所以当前菜单和监听逻辑统一由本插件负责。

## 9. 当前配置文件说明

当前主配置文件：

- `src/main/resources/config.yml`

重要配置块如下：

- `allowed-worlds`
  - 允许圈地的世界列表
- `currency.display-name`
  - 菜单和消息里展示的货币名称
- `claims.*`
  - 领地规则，如最小区块、保护半径、中心坐标
- `visual.*`
  - 粒子预览显示相关设置
- `limits.*`
  - 玩家领地数量限制
- `pricing.*`
  - 创建与扩张价格规则
- `messages.*`
  - 插件消息和 GUI 标题

## 10. 当前版本状态

当前版本：

```text
0.15.1
```

版本规则：

- `x`：重大架构变更
- `y`：功能新增
- `z`：Bug 修复

当前 `0.15.1` 属于 Bug 修复与清理版本，主要调整了：

- 移除旧的 Invero 菜单入口自动导出逻辑
- 移除 Invero 软依赖和内置入口菜单模板
- 保留插件内置 Bukkit GUI 作为当前菜单实现
- 保留 Residence、Vault、MMMVaultSync 的核心集成

## 11. 当前实现的优点

- 底层继续依赖成熟的 `Residence`
- 规则完全可配置
- 支持按权限控制领地上限
- 支持分档领地价格
- 支持区块级扩张与缩小
- 玩家体验比原始命令式圈地更清晰
- 内置菜单和命令流程集中维护，避免菜单入口与业务状态分散

## 12. 当前需要注意的点

- 本插件只管理“由本插件创建并记录”的领地
- 如果有历史 `Residence` 领地，不一定会自动纳入本插件管理
- `Residence` 本身如果配置损坏或启动失败，本插件也无法工作
- 当前菜单是插件自己的 Bukkit GUI，不依赖 Invero

## 13. 结论

当前插件已经具备一套可用的“基于 Residence 的区块化圈地系统”雏形。

它现在已经能完成：

- 限定世界圈地
- 限制中心区域不可圈地
- 按区块创建整列领地
- 按阶梯价格收费
- 按区块扩张收费
- 缩小不返还
- 菜单化管理
- 创建前粒子预览
- 聊天确认创建
- 离开范围自动取消

后续继续优化时，建议围绕“交互体验、配置灵活度、管理能力、权限与传送体验”四个方向推进。
