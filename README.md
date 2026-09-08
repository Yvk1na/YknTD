# YknTD

YknTD 是面向 Paper 1.21.10 及以上版本的浮岛夜袭插件。1～6 名玩家需要共同防守北、东、南、西四路结界；结界失守后敌人会进攻中央核心。玩家可以消耗紫水晶碎片修复结界，最终奖励按照可验证贡献发放。

## 环境

- Windows
- 固定构建 JDK 25：`C:\java\25`
- Paper 1.21.10+
- 必需插件：Multiverse-Core 5.7.3+
- 可选插件：AuraSkills 2.3.12（队伍战力快照）、MythicMobs（自定义怪物）、RoseStacker（自动阻止夜袭怪合并）

## 构建

项目使用 Gradle Kotlin DSL，固定由 `C:\java\25` 的 Java 25 toolchain 构建，并以 Java 25 字节码为目标；服务端也需要使用 JDK 25。Gradle 用户缓存建议固定为 `C:\Users\y\.gradle`。

```powershell
$env:JAVA_HOME = 'C:\java\25'
.\gradlew.bat clean build -g 'C:\Users\y\.gradle'
```

产物固定为 `build\libs\YknTD-1.4.0.jar`。

## 专属虚空竞技场

YknTD 自带浮岛夜袭区块生成器，并通过 Multiverse-Core 管理最多十个独立世界。带编号的世界名仅供插件内部管理，
玩家始终使用逻辑场地 ID `night_raid`：中央浮岛放置核心，北、东、南、西四路
分别包含结界、浮空桥、路点和怪物出生岛。世界固定午夜、关闭自然刷怪/PVP/天气与生物破坏，
世界边界之外保持虚空；夜袭怪的自然日照燃烧还会被单独拦截。

四路竞技场现已升级为高档主题地形：中央核心、四向浮桥、结界祭坛、路点平台和出生岛均带有独立纹饰、灯光与立体结构。
旧实例会通过地形版本指纹识别，并在下一次分配给玩家、执行入场前重置时自动重建，无需逐一操作十个内部世界。

竞技场不会交给 Multiverse 在启动早期自动加载。默认 `auto-create: false`，像 YknBoss 一样在需要时按需创建；
也可设为 `true`，让 YknTD 启用后预建全部实例。
首次实际创建或加载 01 实例时会把入口元数据写入 `arenas.yml`；固定坐标在各实例世界加载后自动注册，无需手工设置位置。可用以下命令管理：

```text
/ykntd arena dedicated
/ykntd arena generate
/ykntd arena reset
```

默认 `instance-count: 10`、`reset-before-session: true`：玩家只使用 `/ykntd join night_raid`，系统会先匹配现有可加入的等待场次；
没有可加入场次时才占用下一个空闲竞技场，并在首位玩家传送前由 Multiverse 完整重建。后续队员加入同一等待场次不会重复重置；
全部竞技场均忙时拒绝新场次。编号仅供插件内部管理，玩家指令、帮助、状态和 Tab 补全均不会显示或接受内部编号。
管理员使用 `start`/`stop` 时，Tab 补全只会动态显示正在运行的内部场次，不会列出空闲槽位。
手工执行 `reset` 只能在没有专属场次且所有实例世界中都没有玩家时进行。非参与玩家以及其他实例的参与者不能跨实例传送进入，
专属世界始终禁止方块破坏、放置、流体、点火、活塞和爆炸改图。

玩家进入夜袭后，普通 SKYBLOCK 侧边栏只临时切换内容并始终保留原 `SKYBLOCK` 标题，集中显示当前状态、波次、敌人数、
四路结界生命条、核心生命条和队伍人数；原 ActionBar 状态条不再重复显示。离开、结算、管理员停止或插件关闭时，
侧边栏都会归还给原 HUD。最多 15 行内容可在 `config.yml` 的 `sidebar` 节点自定义。核心 BossBar 的中段使用紫色，
避开 BetterHud 专门占用的黄色着色器通道，确保进度纹理不会在 25%～50% 血量时消失。
夜袭怪及专属竞技场内的非玩家生物不会产生原版物品、装备或经验掉落，怪物箭矢也不可拾取；结算奖励不受影响。

旧版单世界 `tdmap/floating_island_night_raid` 会在确认带有 YknTD 所有权标记且无人后，安全迁移为首个内部槽位。
`radius` 可通过 `/ykntd reload` 热更新；`arena-id`、`world-name`、`instance-count`、`floor-y`、`seed` 涉及世界身份或地形，
修改后必须完整重启服务器。若 `arena-id` 已被旧的手工场地占用，YknTD 会拒绝覆盖并提示更换 ID。

## 快速配置

1. 确认 Multiverse-Core 5.7.3+ 已安装，把 YknTD JAR 放入测试服 `plugins` 并启动。
2. 执行 `/ykntd arena dedicated` 查看实例池状态；懒加载模式下无需预先生成世界。
3. 玩家执行 `/ykntd join` 即可按需创建并进入 `night_raid`，随后自动倒计时。
4. 在 `config.yml` 调整数值与奖励，在 `waves.yml` 调整怪物。修改后须先结束场次，再执行 `/ykntd reload`。

如需额外手工场地，仍可使用 `/ykntd arena create/setlobby/setcore/setspawn/setward/addwaypoint`，
并以 `/ykntd arena validate <场地>` 校验。

`arenas.yml` 初始为空，专属世界就绪后才写入真实坐标，不会使用占位坐标开局。

## 波次格式

每个波次由若干怪物组组成：

```yaml
groups:
  - provider: VANILLA
    id: ZOMBIE
    amount-per-lane: 3
    lanes: [NORTH, EAST, SOUTH, WEST]
    level: 1
    base-health: 55
    base-damage: 18
    health-multiplier: 1.0
    damage-multiplier: 1.0
    ward-damage: 8.0
    boss: false
```

`amount` 是单人基础总数；`amount-per-lane` 可直接保证各路数量一致，二者最终都会按参战人数放大。
`base-health` 是单人基础生命，`base-damage` 是进入 YknRpg 防御、AuraSkills 减伤和原版护甲前的基础攻击；
远程怪的投射物也使用该攻击值。`lanes` 决定怪物路线，`level` 只对 MythicMobs 生效。

默认十波分为 1～3 前期、4～7 中期、8～10 后期，分别参考 YknRpg 普通/少见、稀有/史诗、
传说/神话武器护甲及同阶段宠物预算，并按 AuraSkills 新生成的 53 组分段奖励重新校准。第一波开始时分别锁定
队伍 Aura 进攻与生存中位指数：怪物生命仅跟随进攻轴按 0.80～1.25 修正，攻击仅跟随生存轴按 0.85～1.20 修正。
实时指数只读取 AuraSkills 各技能奖励的基础属性；装备、宠物、护符等额外修正已包含在各阶段波次基准中，
不会再次计入。AuraSkills 不可用时自动使用 1.0。
升级到 1.4.0 时，旧波次会先备份为 `waves.pre-1.4.0.yml`，再迁移到带 `content-version: 3` 的新版平衡值。

## 奖励命令

所有奖励命令均由控制台执行，可使用 `{player}`、`{uuid}`、`{arena}`、`{wave}`、`{players}`、`{score}` 和六类贡献占位符。胜利奖励包含基础命令及最高匹配档位；失败奖励独立配置。修改命令前，建议先在测试服控制台手动验证。

插件会自动统计伤害、击杀、承伤与实际修复量。治疗和控制无法由 Bukkit 通用事件可靠判断施法者，只能由其他插件在主线程调用 `YknTdApi#addContribution` 记入。

## 权限

- `ykntd.use`：查看帮助和使用主命令，默认所有玩家拥有。
- `ykntd.play`：加入或离开夜袭，默认所有玩家拥有。
- `ykntd.admin`：配置场地、开始、停止和重载，默认仅管理员拥有。
- `ykntd.repair.free`：创造模式下免除修复材料，默认管理员拥有。
