# HERRA Stamina — 独立体力条模组

HERRA 搜打撤生态的独立小模组：只负责**体力系统本身**（跑/跳/游消耗、延迟恢复、
透支惩罚、物品栏上方像素风 HUD），不含战局、播报、枪械等内容，与其他 HERRA
模组零硬耦合（联动统一走 `com.herra.stamina.api`）。

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
# 产物：build/libs/herra_stamina-1.0.0.jar
```

运行环境（服务器与客户端都需）：
- NeoForge 21.1.216+
- LDLib2（Modrinte/CurseForge 搜 "LDLib2"，1.21.1 版 ≥ 2.2.1；开发坐标见下）

开发环境调试：`./gradlew runClient` / `./gradlew runServer`

---

## 2. 玩法设计（默认值）

- **满体力 100**，疾跑 12/秒（约 8.3 秒跑空），跳跃一次性 10，
  游泳 5/秒、疾速游泳 9/秒（疾跑中跳跃 = 疾跑速率 + 跳跃值）
- **恢复**：停止消耗 1.2 秒后开始，25/秒回满（约 4 秒）
- **透支**：体力归零进入透支锁——禁跑、恢复延迟变 3 秒、恢复速度降为 15/秒，
  恢复到 30 才解除禁跑（三角洲式"必须缓过来才能再跑"）
- **低体力**（<15）：立即禁止疾跑，服务端压制 + 客户端镜像（无 FOV 抖动）
- 创造/旁观模式不消耗；死亡重生满体力复活

## 3. HUD（LDLib2 ModularHudLayer）

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
| recovery.delay_ticks | 24 | 恢复延迟（tick） |
| recovery.per_second | 25 | 恢复速度/秒 |
| recovery.exhausted_delay_ticks | 60 | 透支后额外延迟 |
| recovery.exhausted_per_second | 15 | 透支恢复速度/秒 |
| penalty.sprint_stop_threshold | 15 | 低于禁跑 |
| penalty.exhausted_release_threshold | 30 | 透支解除阈值 |
| network.sync_interval_ticks / sync_delta | 2 / 2.0 | 同步节流 |

**客户端 HUD 表现** `herra_stamina-client.toml`（`config/` 目录）：
`hud.enabled / offset_x / offset_y / auto_hide / hide_delay_ticks /
show_icon / show_body_status / show_status_text / animation_speed`；
配色四段渐变 `colors.full / mid / low / crit`（0xRRGGBB，默认 米白→金黄→橙→红）

> 数值全部实时读取，无需重启；整合包可用 `defaultconfigs/` 统一发货。
> 运行中改客户端 toml 即时生效（文件监听自动重载）。

---

## 5. 游戏内调节指令 `/stamina`

服务端 OP（权限 2）可直接在游戏里调数值，改完立即生效**并写入 TOML**
（`world/serverconfig/herra-stamina-server.toml`，重启不丢）：

```text
/stamina                                查看自己的体力
/stamina info [player]                 查看体力（看别人需 OP）
/stamina set <player> <value>          设置体力
/stamina add <player> <value>          增减体力（可为负）
/stamina exhaust <player>              清空 + 透支锁（测试低体力惩罚）
/stamina reset <player>                回满
/stamina modifier list [player]        生效中的修改器
/stamina modifier clear <player>       清空修改器
/stamina modifier give <player> <id> <seconds> [drain×] [maxBonus] [regen/s]
                                        挂测试增益（与医药模组同路径，id 含冒号要加引号）
/stamina config show                   列出全部服务器数值
/stamina config max|sprint-drain|jump-cost|swim-drain|swim-sprint-drain|
                 regen|regen-exhausted|delay|delay-exhausted|
                 sprint-stop|release <value>   改数值并落盘
```

示例：`/stamina config sprint-drain 8`（疾跑变慢耗）、
`/stamina modifier give Steve "herra_med:test" 60 0.5 30 5`
（60 秒：消耗减半、上限+30、恢复+5/s —— 不写代码就能验证药品接口）

> 手改 TOML 同样支持：SERVER 配置文件被监听，存盘即热重载。

---

## 6. HERRA 生态对接（其他模组如何联动）

本模组可被单独禁用；其他模组请**先判空再使用**，不要在 mods.toml 里硬依赖。

### 6.1 编译依赖

```groovy
repositories { maven { url = "https://maven.firstdark.dev/snapshots" } }
dependencies {
    compileOnly "com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.41:all"
    compileOnly files("libs/herra_stamina-1.0.0.jar") // 或发布到 HERRA 内部 maven
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
StaminaAPI.applyModifier(player, StaminaModifier.builder("herra_med:energy_drink")
        .maxStaminaBonus(30.0f)      // 上限加算（体力条变长）
        .drainMultiplier(0.5f)       // 疾跑/跳跃/游泳/自定义消耗全部减半
        .regenMultiplier(1.5f)       // 恢复提速
        .durationSeconds(90)
        .build());

// 药效被解药打断
StaminaAPI.clearModifier(player, "herra_med:energy_drink");

// 重病 debuff：上限乘算 + 消耗加重 + 恢复变慢
StaminaAPI.applyModifier(player, StaminaModifier.builder("herra_med:fever")
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
6. 确认 HUD 不遮挡自定义血条（遮挡则调 `hud.offset_y`，默认已上移到 72）
7. 游泳/疾速游泳按低速率消耗；水下跳跃不触发跳跃消耗
8. `/stamina` 指令：`config show` / `config sprint-drain 8` 看条下降变慢并检查 TOML 已写入；
   `set/add/exhaust/reset` 直接操纵体力；
   `modifier give 自己 "test:buff" 60 0.5 30 5` 验证药品接口（消耗减半+条变长）
9. 服务端：`./gradlew runServer` 冒烟（已在开发环境 RCON 端到端验证：19/19 通过）

## 8. 源码结构

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
- 修改器（`StaminaModifier`）为运行时聚合：上限/消耗/恢复每次实时聚合，
  修改或到期时钳制当前体力并强制重同步（HUD 条长度即时变化）

## 11. 版本记录

### v1.0.0（首发版）

- 核心：疾跑/跳跃/游泳/疾速游泳消耗、延迟恢复、透支锁定、低体力禁跑
- HUD：LDLib2 像素风体力条（白→黄→红渐变、幽灵拖尾、火花、扫光、自动隐藏）
- 配置：SERVER 玩法数值 + CLIENT HUD 表现，全中文注释，热重载
- **修复**：疾跑不消耗（服务端 `deltaMovement` 恒 0 问题，改位置差检测）
- 指令：`/stamina` 全套游戏内调节（数值改完即写盘）
- 生态 API：`StaminaAPI` 门面、`StaminaModifier` 定时修改器（医药模组入口）、
  `StaminaDrainModifier` 消耗规则、4 个事件、`ClientDisplayBridge` 客户端桥
- CI/CD：GitHub Actions（push 构建、打 tag 自动发 Release 附带 jar）
