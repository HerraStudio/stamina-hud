# Stamina — 独立体力条模组

面向搜打撤玩法的独立小模组：只负责**体力系统本身**（跑/跳/游消耗、延迟恢复、
透支惩罚、物品栏上方像素风 HUD），不含战局、播报、枪械等内容，与其他模组
零硬耦合（联动统一走 `com.herra.stamina.api`）。

| 项目 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251（要求 ≥ 21.1.216，随 LDLib2 要求） |
| LDLib2 | 2.2.41（要求 ≥ 2.2.1，HUD 层宿主） |
| Java | 21 |

---

## 1. 构建与安装

```bash
# 需要 JDK 21（Gradle 会按需自动下载）
./gradlew build
# 产物：build/libs/herra_stamina-1.0.3.jar
```

运行环境（服务器与客户端都需）：
- NeoForge 21.1.216+
- LDLib2（Modrinth/CurseForge 搜 "LDLib2"，1.21.1 版 ≥ 2.2.1；开发坐标见下）

开发环境调试：`./gradlew runClient` / `./gradlew runServer`

---

## 2. 玩法设计（默认值）

- **满体力 100**，疾跑 12/秒（约 8.3 秒跑空），跳跃一次性 10，
  游泳 5/秒、疾速游泳 9/秒（疾跑中跳跃 = 疾跑速率 + 跳跃值）；
  近战攻击 2/次、破坏方块 1/块（均可配置，0 = 关闭）
- **恢复**：停止消耗 1.2 秒后开始，25/秒回满（约 4 秒）
- **透支**：体力归零进入透支锁——**禁止疾跑与跳跃**（只能正常行走）、
  恢复延迟变 3 秒、恢复速度降为 15/秒，
  恢复到 30 才解除（三角洲式"必须缓过来才能再跑"）
- **低体力**（<15）：立即禁止疾跑，客户端 mixin 在原版饥饿禁跑的同一判定点
  压制（按住疾跑键 / 切换式疾跑 / 双击 W 全覆盖，无 FOV 抖动）+ 服务端兜底
- 创造/旁观模式不消耗；死亡重生满体力复活

## 3. HUD（LDLib2 ModularHudLayer）

> v1.0.2：支持 4 种样式预设 + 0.5~2.0 整体缩放 + 默认位置上移（offset_y 84），
> 全部可在游戏内 `/sta` 界面实时调整（见第 5 节）。

- 位置：物品栏上方正中（默认距底 72px，避开自定义血条；在护甲条之上，
  不遮挡血/甲/氧/经验；`hud.offset_y` 可调，改客户端 toml 即时生效）
- 像素风贴图 + 分段刻痕 + 2px 高光端帽，与原版 GUI 质感统一
- **动画**：数值平滑插值；大额扣减时格斗游戏式"幽灵拖尾"（被扣部分
  逐列 白→黄→红 渐变消失）+ 白闪 + 像素火花迸溅；低体力红色呼吸脉动；
  归零硬闪烁；回满瞬间扫光 → 延迟 2 秒自动隐藏（消耗时快速淡入）
- 体力条下方：身体状态占位图标（头/胸/臂/腿，供后续身体部位系统扩展）；
  低体力时替换为「呼吸急促」/「体力透支」状态文字（本地化 zh_cn / en_us）

设计预览图：`download/herra_stamina_hud_preview.png`

## 4. 配置

**服务端玩法数值** `herra_stamina-server.toml`（存于世界 `serverconfig/`，
单机在 `saves/<世界>/serverconfig/`；改后重进世界生效）：

| 键 | 默认 | 说明 |
|---|---|---|
| general.max_stamina | 100 | 最大体力 |
| general.apply_in_creative | false | 创造是否消耗 |
| drain.sprint_per_second | 12 | 疾跑每秒消耗 |
| drain.jump_cost | 10 | 跳跃一次性消耗 |
| drain.swim_per_second | 5 | 游泳每秒消耗 |
| drain.swim_sprint_per_second | 9 | 疾速游泳每秒消耗 |
| drain.attack_cost | 2 | 近战攻击一次性消耗（0 = 关） |
| drain.break_block_cost | 1 | 破坏方块一次性消耗（0 = 关） |
| recovery.delay_ticks | 24 | 恢复延迟（tick） |
| recovery.per_second | 25 | 恢复速度/秒 |
| recovery.exhausted_delay_ticks | 60 | 透支后额外延迟 |
| recovery.exhausted_per_second | 15 | 透支恢复速度/秒 |
| penalty.sprint_stop_threshold | 15 | 低于禁跑 |
| penalty.exhausted_release_threshold | 30 | 透支解除阈值 |
| penalty.exhausted_block_jump | true | 透支期间禁止跳跃 |
| penalty.exhausted_block_sprint | true | 透支期间禁止疾跑（严格模式核心开关） |
| penalty.exhausted_walk_slowdown | 0.0 | 透支行走减速比例（0 = 只能正常行走；0.15 = 移速 x0.85） |
| penalty.winded_block_jump | false | 低体力（未透支）也禁跳（默认保留最后一跳逃生空间） |
| network.sync_interval_ticks / sync_delta | 2 / 2.0 | 同步节流 |

**客户端 HUD 表现** `herra_stamina-client.toml`（`config/` 目录，**每个玩家各自生效**，
游戏内 `/sta` 打开设置界面可视化调整，见第 5 节）：
`hud.enabled / offset_x / offset_y(默认 84) / scale(0.5~2.0) / style(classic|tactical|minimal|energy) /
auto_hide / hide_delay_ticks / show_icon / show_body_status / show_status_text / animation_speed`；
提示音 `sound.exhausted_enabled / exhausted_volume(0~100)`；
配色四段渐变 `colors.full / mid / low / crit`（仅「经典」样式使用）

> 数值全部实时读取，无需重启；整合包可用 `defaultconfigs/` 统一发货。
> 运行中改客户端 toml 即时生效（文件监听自动重载）。

---

## 5. 游戏内指令与设置界面

### 5.1 `/sta` 设置界面（玩家级，调整只针对自己）

输入 **`/sta`** 直接打开搜打撤风格战术设置面板（暗色军规 + 琥珀强调 +
扫描线 + 角落括号，全代码绘制）。修改**即时生效**，关闭界面自动保存到
本地 `config/herra_stamina-client.toml`，不影响其他玩家：

- **位置调整**：拖拽小地图直接摆放体力条（含物品栏参考框、中心参考线、
  悬停十字线），或用 水平X / 高度Y / 缩放 滑条微调；一键「居中复位」
- **样式预设**：4 种体力条皮肤 + 动态大预览（呼吸填充 + ghost 残影）
  - `经典 classic` 金蓝像素边框，四段渐变走配置
  - `战术 tactical` 装甲块边框 + 硬分段刻度，军绿荧光→警示红
  - `极简 minimal` 1px 细线边框，冷白→琥珀→红
  - `生电 energy` 青色辉光切角 + 斜纹高光，青→紫→品红
- **耗尽提示音**：体力归零瞬间播放低频心跳闷响；开关 + 0~100 音量滑条 +
  「试听」按钮
- **显示选项**：闪电图标 / 状态文字 / 身体图标 / 自动隐藏 四个 LED 开关，
  外加动画速度滑条

### 5.2 指令权限一览

```text
/sta                                   打开设置界面（玩家级）
/sta ui                                同上（别名）
/sta help                              指令帮助（玩家级）
/stamina                               查看自己的体力（唯一需打全称的玩家级指令）
```

**以下子命令均挂 /sta 且需要 OP（权限 2）—— 所有涉及数值修改的指令
一律管理员权限**，改完立即生效并写入 TOML
（`world/serverconfig/herra_stamina-server.toml`，重启不丢）：

```text
/sta info <player>                     查看指定玩家体力
/sta set <player> <value>              设置体力
/sta add <player> <value>              增减体力（可为负）
/sta exhaust <player>                  清空 + 透支锁（测试低体力惩罚）
/sta reset <player>                    回满
/sta modifier list [player]            生效中的修改器
/sta modifier clear <player>           清空修改器
/sta modifier give <player> <id> <seconds> [drain×] [maxBonus] [regen/s]
                                       挂测试增益（与医药模组同路径，id 含冒号要加引号）
/sta config show                       列出全部服务器数值
/sta config max|sprint-drain|jump-cost|swim-drain|swim-sprint-drain|
             attack-cost|break-cost|regen|regen-exhausted|delay|
             delay-exhausted|sprint-stop|release <value>   改数值并落盘
/sta config block-jump <true|false>        透支是否禁跳
/sta config block-sprint <true|false>      透支是否禁跑（严格模式）
/sta config walk-slowdown <0~0.6>          透支行走减速比例
/sta config winded-block-jump <true|false> 低体力（未透支）是否禁跳
```

示例：`/sta config sprint-drain 8`（疾跑变慢耗）、
`/sta config walk-slowdown 0.15`（透支移速 x0.85）、
`/sta modifier give Steve "med:test" 60 0.5 30 5`
（60 秒：消耗减半、上限+30、恢复+5/s —— 不写代码就能验证药品接口）

> 手改 TOML 同样支持：SERVER 配置文件被监听，存盘即热重载。

---

## 6. 生态对接（其他模组如何联动）

本模组可被单独禁用；其他模组请**先判空再使用**，不要在 mods.toml 里硬依赖。

### 6.1 编译依赖

```groovy
repositories { maven { url = "https://maven.firstdark.dev/snapshots" } }
dependencies {
    compileOnly "com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.41:all"
    compileOnly files("libs/herra_stamina-1.0.3.jar") // 或发布到内部 maven
}
```

### 6.2 读取 / 操作体力（服务端权威，静态门面 `StaminaAPI`）

```java
// 无需 Holder 判空 —— 类始终存在；模组被禁用时方法自然无人调用
import com.herra.stamina.api.StaminaAPI;
import com.herra.stamina.api.StaminaAction;

float cur = StaminaAPI.getStamina(player);          // 0 ~ max（客户端传本地玩家 = 插值显示值）
float ratio = StaminaAPI.getRatio(player);          // 0~1，HUD 类模组直接用
boolean ok = StaminaAPI.tryConsume(player, 8f,      // 原子扣除（不足则不扣）
        StaminaAction.CUSTOM);                      //   自定义来源标机（开镜/挥霍…）
float used = StaminaAPI.drain(player, 3f,           // 连续扣（扣多少算多少）
        StaminaAction.CUSTOM);
StaminaAPI.setStamina(player, 100f);                // 能量饮料之类
StaminaAPI.reset(player);                           // 战局开局重置
boolean canRun = StaminaAPI.canSprint(player);      // 预留的低体力判定接口
boolean burnt  = StaminaAPI.isExhausted(player);    // 透支锁中
```

客户端视觉读取（HUD 类模组）两种方式：
- 跨端安全：`StaminaAPI.getRatio(localPlayer)`（内部走显示桥，无需判端）
- 直接取：`com.herra.stamina.client.ClientStaminaData`（静态：getRatio /
  isExhausted / isWinded / getGhostRatio 等，均为同步后的插值状态）

### 6.3 消耗规则修改器（持续消耗：疾跑/游泳）

持续消耗每 tick 结算、不走事件 —— 用 `StaminaDrainModifier` 改写规则：

```java
// 重甲加大疾跑消耗，外骨骼减免跳跃消耗（GWO 联动示例）
StaminaAPI.registerDrainModifier((player, action, cost) -> {
    if (action == StaminaAction.SPRINT && isHeavilyLoaded(player)) return cost * 1.35f;
    if (action == StaminaAction.JUMP && hasExoskeleton(player)) return cost * 0.7f;
    return cost;
});
// 卸载时：StaminaAPI.unregisterDrainModifier(...)
```

### 6.4 定时修改器 `StaminaModifier`（医药/增益类模组推荐入口）

一次调用同时影响 **体力上限、全部消耗、恢复速度**，服务端自动倒计时、
到期自动失效并同步 HUD，无需自己计时。同 id 重复 apply = 刷新：

```java
import com.herra.stamina.api.StaminaModifier;

// 能量饮料：90 秒内消耗减半、上限 +30、恢复提速 50%
StaminaAPI.applyModifier(player, StaminaModifier.builder("med:energy_drink")
        .maxStaminaBonus(30.0f)      // 上限加算（体力条变长）
        .drainMultiplier(0.5f)       // 疾跑/跳跃/游泳/自定义消耗全部减半
        .regenMultiplier(1.5f)       // 恢复提速
        .durationSeconds(90)
        .build());

// 药效被解药打断
StaminaAPI.clearModifier(player, "med:energy_drink");

// 重病 debuff：上限乘算 + 消耗加重 + 恢复变慢
StaminaAPI.applyModifier(player, StaminaModifier.builder("med:fever")
        .maxStaminaMultiplier(0.7f)
        .drainMultiplier(1.4f)
        .regenMultiplier(0.6f)
        .durationMinutes(10)
        .build());

// 查询 / 清空
StaminaAPI.getActiveModifiers(player);
StaminaAPI.clearModifiers(player);   // 洗胃、死亡、新战局
```

聚合规则：`最终上限 = (基础 + Σ加算) × Π乘算`（最低 1）；消耗与恢复同理乘算叠加。
修改器为运行时数据（不落盘，退出即清），药品自身管理药效周期即可。

### 6.5 事件（NeoForge 游戏总线，服务端）

| 事件 | 时机 | 用途 |
|---|---|---|
| `StaminaEvent.Consume` | 一次性扣除前（跳跃/自定义） | **可改量 / 可取消** |
| `StaminaEvent.Changed` | 数值变化 | 联动 UI / 统计（高频，轻量监听） |
| `StaminaEvent.Exhausted` | 归零进入透支锁 | 叠加惩罚（枪械抖动↑、呼吸音效…） |
| `StaminaEvent.Recovered` | 透支解除（回气到阈值） | 撤销惩罚 |

```java
@SubscribeEvent
static void onConsume(StaminaEvent.Consume e) {
    if (e.getAction() == StaminaAction.JUMP && isHeavilyLoaded(e.getPlayer())) {
        e.setAmount(e.getAmount() * 1.5f); // GWO 负重联动示例
    }
}
```

消耗来源枚举：`StaminaAction.SPRINT / JUMP / SWIM / SWIM_SPRINT / CUSTOM`。

---

## 7. 测试清单

1. `./gradlew runClient` → 生存模式（创造不消耗）
2. **疾跑消耗**（v1.0.0 已修复：服务端移动检测改用位置差）：疾跑时条应平滑下降；
   疾跑 + 跳跃 = 持续消耗 + 一次性 10
3. 停下 1.2 秒：平滑回升；跳一下（消耗 10）→ 白闪 + 火花 + 幽灵拖尾渐变
4. 透支后恢复到 30 → 恢复疾跑能力（`StaminaEvent.Recovered` 触发）
5. 回满 → 扫光 → 2 秒后自动隐藏；再跑立刻淡入
6. 确认 HUD 不遮挡自定义血条（默认已上移到 84；遮挡则 `/sta` 拖拽微调）
7. 游泳/疾速游泳按低速率消耗；水下跳跃不触发跳跃消耗
8. `/sta` 管理指令（OP）：`config show` / `config sprint-drain 8` 看条下降变慢并检查 TOML 已写入；
   `set/add/exhaust/reset` 直接操纵体力；
   `modifier give 自己 "test:buff" 60 0.5 30 5` 验证药品接口（消耗减半+条变长）；
   非OP 执行应提示无权限；普通玩家 `/stamina` 只能看自己
9. 服务端：`./gradlew runServer` 冒烟（已在开发环境 RCON 端到端验证：29/29 通过）
10. **v1.0.2 界面**（真机）：`/sta` 打开设置面板 → 拖拽小地图摆位（HUD 实时跟随）
    → 切 4 种样式看预览和 HUD 变化 → 拉音量 + 试听提示音 → 耗尽体力听心跳音
    → 缩放滑条 → ESC 关闭后重进世界确认已保存
11. **v1.0.2 透支严格性**：耗尽瞬间按住疾跑键应**立即**停跑（无 1~2 tick 窗口）；
    恢复到 30 解锁瞬间立即可跑；`/sta config walk-slowdown 0.15` 后透支应明显变慢

## 8. 源码结构

> v1.0.2 新增类：`client/gui/StaminaSettingsScreen`（设置界面 + 全套自绘
> 战术控件）、`client/hud/BarStyle`（样式预设枚举）、`client/ClientGuiHandlers`
> （client-only 中转，规避服务端 dist 崩溃）、`client/StaminaSounds`（提示音
> 播放）、`core/ModSounds`（音效注册）、`network/OpenSettingsPayload`
> （S2C 打开界面）。

```
src/main/java/com/herra/stamina/
├── HerraStamina.java              主类（注册 Attachment/网络/配置/事件/指令）
├── core/                          核心：StaminaData（Attachment 数据 + 透支锁 + 修改器 + 同步节流）、
│                                    StaminaManager（消耗/恢复/跳跃/同步规则）、
│                                    StaminaGameEvents（事件接线）、
│                                    StaminaCommands（/stamina 指令）、ModAttachments
├── config/                        Server/Client 双配置（中文注释）
├── network/                       StaminaSyncPayload（4 字段同步包）+ ModNetworking
├── api/                           生态 API：StaminaAPI（静态门面）、StaminaAction、
│     (+ api/event/)                StaminaModifier（定时修改器）、StaminaDrainModifier、
│                                    ClientDisplayBridge、
│                                    StaminaEvent.Consume/Changed/Exhausted/Recovered
└── client/ (+ client/hud/)        ClientStaminaData（动画状态机+显示桥）、
                                     StaminaHudElement（像素绘制 HUD）、
                                     HerraStaminaClient/ClientEvents/ClientPacketHandlers
```

## 9. 后续扩展方向（已预留接口）

- **身体部位系统**：HUD 下方 4 个占位图标即挂点；新增身体状态同步包 + 图标染色
- **GWO 负重/枪械联动**：`StaminaDrainModifier` 改写规则；开镜/据枪消耗用 `tryConsume`
- **战局系统**：进局重置（`setStamina`）、药剂 (`setStamina`)、疲劳累积（监听 Changed）
- **低体力惩罚扩展**：`StaminaEvent.Exhausted` 叠加（移速、挖掘、开镜稳定度）
- **表现层**：呼吸音效、LDLib2 StyleAnimation 主题化、数值显示、`ANIMATION_SPEED`
- **配置界面**：LDLib2 Configurable 注解体系可自动生成配置 UI

## 10. 技术要点备忘

- 跳跃消耗用 `LivingEvent.LivingJumpEvent`（在 `jumpFromGround()` 内触发，
  含跳跃药水/马匹等变体，精确无误判）。**注意**：NeoForge 21.1 中该事件
  **不可取消**——策略是「允许跳跃、一次性扣费」，体力不足则扣到空并进入
  透支锁（战术上允许最后一丝体力赌命跳，代价是喘不上气）
- 体力存 NeoForge **Attachment**（运行时存储不落盘：新战局永远满体力开局；
  提供 NBT 快照方法供战局系统持久化）；登录/重生/跨维度事件保证同步
- 同步节流（数值变化 ≥2 或状态位变化才发包，间隔 ≥2 tick）+ 客户端插值补帧
- HUD 用 LDLib2 `ModularHudLayer`（官方 HUD Overlays 方案），自定义
  `UIElement.drawContents` 做像素绘制——着色全部走顶点色/SpriteTexture，
  规避 `RenderSystem.setShaderColor` 在批量渲染下不可靠的问题
- 客户端镜像禁跑：`sprintBlocked` 随包同步，客户端 `ClientTickEvent` 里
  主动 `setSprinting(false)`，与原版饥饿禁跑同思路，避免疾跑 FOV 抖动


- **服务端移动检测必须用位置差，不能用 `getDeltaMovement()`**：
  服务端玩家移动走 `handleMovePlayer -> Entity.move(PLAYER, ...)`，
  只写坐标不写 deltaMovement（仅撞墙/击退等才写），输入移动时它恒为 0 ——
  v1.0.0 曾因此出现"疾跑不消耗、只有跳跃消耗"的 bug，已改为
  `StaminaData.updateMovement()` 记录每 tick 位置差（可靠）
- **透支禁跑必须在客户端原版判定点压制，不能事后 setSprinting(false)**：
  客户端 `LocalPlayer.aiStep` 每 tick 会重启疾跑（按住疾跑键 / 切换式疾跑），
  tick 末尾杀疾跑等于没杀（该 tick 的速度加成已生效）；mixin 进
  `hasEnoughFoodToStartSprinting()`（原版饥饿禁跑判定点，同时门控
  启动与维持）才能全覆盖且无 FOV 抖动
- **透支禁跳用 JUMP_STRENGTH 属性而非取消事件**：LivingJumpEvent 在
  1.21.1 不可取消；JUMP_STRENGTH 是同步属性（范围 0~32），挂
  `ADD_VALUE -1024` 钳到 0 → 两端 `jumpFromGround()` 的
  `f <= 1.0E-5` 守卫直接跳过，无橡皮筋、无客户端预测回弹
- **游泳垂直位移消耗**：上浮/下潜时水平位移几乎为 0，垂直分量单独
  记录（阈值 0.05 格/tick，排除水中被动下沉约 0.02~0.03 格/tick）
- 修改器（`StaminaModifier`）为运行时聚合：上限/消耗/恢复每次实时聚合，
  修改或到期时钳制当前体力并强制重同步（HUD 条长度即时变化）

## 11. 版本记录

### v1.0.3
- **品牌调整**：模组显示名去掉团队前缀（HERRA Stamina → Stamina），作者信息
  同步更新；README 全面去品牌化（如「HERRA 生态对接」→「生态对接」，示例
  修改器 id 改为中性命名）；代码标识（mod id `herra_stamina`、包名
  `com.herra.stamina`）保持不变，存档 / 配置 / API 兼容性不受影响
- 修正：文档中配置文件名笔误（`herra-stamina-*.toml` → `herra_stamina-*.toml`，
  与 NeoForge 实际生成一致）；issue 跟踪链接指向本仓库

### v1.0.2
- **HUD 默认位置上移**：offset_y 默认 72 → 84（避开自定义血条/护甲条，
  位于氧气条上方；`/sta` 界面可拖拽微调）
- **`/sta` 设置界面**（玩家级，调整仅对自己生效）：暗色军规战术面板，
  拖拽小地图摆位 + X/Y/缩放滑条 + 居中复位，全部修改即时生效、
  关闭自动保存；指令改为短入口 `/sta`，`/stamina` 全称仅保留自查
- **4 种体力条样式预设**：经典 / 战术（Tarkov 装甲风）/ 极简 / 生电
  （科幻辉光），界面内点选切换 + 动态大预览
- **体力耗尽提示音**：归零瞬间低频心跳闷响（合成音效 exhausted.ogg），
  开关 + 0~100 音量 + 试听，全部集成在 `/sta` 界面
- **透支惩罚收紧**：透支进入/解除瞬间改为零延迟同步（客户端立即生效，
  消除 1~2 tick 的疾跑窗口）；新增 `exhausted_block_sprint`（透支禁跑
  核心开关）、`exhausted_walk_slowdown`（透支减速，默认 0 = 只能正常
  行走）、`winded_block_jump`（低体力禁跳选项）三个服务端配置
- **权限收紧**：所有涉及数值修改的指令（set/add/exhaust/reset/
  modifier/config/info 查他人）统一要求 OP 权限 2
- Bug 修复：创造模式消耗一致性（跳跃/攻击/挖块现与疾跑同规则，
  受 apply_in_creative 控制）；运行中改 walk-slowdown 配置修饰符即时
  重挂；config 指令数值显示精度（0.15 不再显示为 0.2）；服务器专用端
  Screen 引用 dist 崩溃（抽 ClientGuiHandlers 中转）
- 验证：BUILD SUCCESSFUL + 服务器冒烟 29/29 RCON 用例通过

### v1.0.1

- **修复透支后仍可疾跑**：新增客户端 mixin（`LocalPlayerMixin`）注入原版
  饥饿禁跑判定点 `hasEnoughFoodToStartSprinting()`，服务端 `sprintBlocked`
  标志同步后在该点压制 —— 按住疾跑键 / 切换式疾跑（toggle sprint）/
  双击 W 全部生效，FOV 平滑回落；服务端 `setSprinting(false)` 保留为兜底
- **新增透支禁跳**：体力归零期间禁止跳跃（JUMP_STRENGTH 同步属性修饰符，
  双端一致无橡皮筋；`penalty.exhausted_block_jump` 可关）；
  透支前最后一跳仍允许（赌命跳，直接扣空进透支）
- **新增消耗动作**：近战攻击 2/次（`attack_cost`）、破坏方块 1/块
  （`break_block_cost`），吃全部生态修改器（药品减半同样生效）；
  `StaminaAction` 新增 `ATTACK` / `BREAK_BLOCK`
- **修复游泳消耗判定**：原地踩水不扣；上浮/下潜（垂直位移）正确计入
  游泳消耗；滑翔（鞘翅）与骑乘不再误判为疾跑消耗
- 指令新增：`/stamina config attack-cost | break-cost | block-jump`
- 冒烟测试 26/26 通过（含新配置项落盘验证）

### v1.0.0（首发版）

- 核心：疾跑/跳跃/游泳/疾速游泳消耗、延迟恢复、透支锁定、低体力禁跑
- HUD：LDLib2 像素风体力条（白→黄→红渐变、幽灵拖尾、火花、扫光、自动隐藏）
- 配置：SERVER 玩法数值 + CLIENT HUD 表现，全中文注释，热重载
- **修复**：疾跑不消耗（服务端 `deltaMovement` 恒 0 问题，改位置差检测）
- 指令：`/stamina` 全套游戏内调节（数值改完即写盘）
- 生态 API：`StaminaAPI` 门面、`StaminaModifier` 定时修改器（医药模组入口）、
  `StaminaDrainModifier` 消耗规则、4 个事件、`ClientDisplayBridge` 客户端桥
- CI/CD：GitHub Actions（push 构建、打 tag 自动发 Release 附带 jar）
