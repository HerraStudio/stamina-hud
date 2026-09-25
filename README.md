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

- 位置：物品栏上方正中（默认距底 60px，在护甲条之上，不遮挡血/甲/氧/经验）
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

---

## 5. HERRA 生态对接（其他模组如何联动）

本模组可被单独禁用；其他模组请**先判空再使用**，不要在 mods.toml 里硬依赖。

### 5.1 编译依赖

```groovy
repositories { maven { url = "https://maven.firstdark.dev/snapshots" } }
dependencies {
    compileOnly "com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.41:all"
    compileOnly files("libs/herra_stamina-1.0.0.jar") // 或发布到 HERRA 内部 maven
}
```

### 5.2 读取 / 操作体力（服务端权威，静态门面 `StaminaAPI`）

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

### 5.3 消耗规则修改器（持续消耗：疾跑/游泳）

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

### 5.4 事件（NeoForge 游戏总线，服务端）

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

## 6. 测试清单

1. `./gradlew runClient` → 生存模式（创造不消耗）
2. 疾跑：条淡入，白色平滑下降；到 0 → 变红 + 「体力透支」+ 禁跑
3. 停下 1.2 秒：平滑回升；跳一下（消耗 10）→ 白闪 + 火花 + 幽灵拖尾渐变
4. 透支后恢复到 30 → 恢复疾跑能力（`StaminaEvent.Recovered` 触发）
5. 回满 → 扫光 → 2 秒后自动隐藏；再跑立刻淡入
6. 穿钻石甲站定：确认不遮挡护甲行（遮挡则调 `hud.offset_y`）
7. 游泳/疾速游泳按低速率消耗；水下跳跃不触发跳跃消耗
8. 修改 serverconfig 数值重进世界 → 立即生效
9. 服务端：`./gradlew runServer` 冒烟（已在开发环境验证：Done in 20s，无报错）

## 7. 源码结构

```
src/main/java/com/herra/stamina/
├── HerraStamina.java              主类（注册 Attachment/网络/配置/事件）
├── core/                          核心：StaminaData（Attachment 数据 + 透支锁 + 同步节流）、
│                                    StaminaManager（消耗/恢复/跳跃/同步规则）、
│                                    StaminaGameEvents（事件接线）、ModAttachments
├── config/                        Server/Client 双配置（中文注释）
├── network/                       StaminaSyncPayload（4 字段同步包）+ ModNetworking
├── api/                           生态 API：StaminaAPI（静态门面）、StaminaAction、
│     (+ api/event/)                StaminaDrainModifier、ClientDisplayBridge、
│                                    StaminaEvent.Consume/Changed/Exhausted/Recovered
└── client/ (+ client/hud/)        ClientStaminaData（动画状态机+显示桥）、
                                     StaminaHudElement（像素绘制 HUD）、
                                     HerraStaminaClient/ClientEvents/ClientPacketHandlers
```

## 8. 后续扩展方向（已预留接口）

- **身体部位系统**：HUD 下方 4 个占位图标即挂点；新增身体状态同步包 + 图标染色
- **GWO 负重/枪械联动**：`StaminaDrainModifier` 改写规则；开镜/据枪消耗用 `tryConsume`
- **战局系统**：进局重置（`setStamina`）、药剂 (`setStamina`)、疲劳累积（监听 Changed）
- **低体力惩罚扩展**：`StaminaEvent.Exhausted` 叠加（移速、挖掘、开镜稳定度）
- **表现层**：呼吸音效、LDLib2 StyleAnimation 主题化、数值显示、`ANIMATION_SPEED`
- **配置界面**：LDLib2 Configurable 注解体系可自动生成配置 UI

## 9. 技术要点备忘

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

