package com.herra.stamina.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务端玩法数值配置（SERVER 类型，存于世界 serverconfig 目录，
 * 整个搜打撤服务器统一生效）。
 *
 * <p>默认数值按搜打撤手感调校（参考三角洲行动/暗区突围）：
 * 满体力约 8 秒疾跑，跳跃一次性消耗，透支后恢复减速、
 * 需回气到阈值才解除禁跑。</p>
 */
public final class StaminaServerConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAX_STAMINA;
    public static final ModConfigSpec.BooleanValue APPLY_IN_CREATIVE;

    public static final ModConfigSpec.DoubleValue SPRINT_DRAIN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue JUMP_COST;
    public static final ModConfigSpec.DoubleValue SWIM_DRAIN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue SWIM_SPRINT_DRAIN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue ATTACK_COST;
    public static final ModConfigSpec.DoubleValue BREAK_BLOCK_COST;

    public static final ModConfigSpec.IntValue RECOVERY_DELAY_TICKS;
    public static final ModConfigSpec.DoubleValue RECOVERY_PER_SECOND;
    public static final ModConfigSpec.IntValue EXHAUSTED_RECOVERY_DELAY_TICKS;
    public static final ModConfigSpec.DoubleValue EXHAUSTED_RECOVERY_PER_SECOND;

    public static final ModConfigSpec.DoubleValue SPRINT_STOP_THRESHOLD;
    public static final ModConfigSpec.DoubleValue EXHAUSTED_RELEASE_THRESHOLD;
    public static final ModConfigSpec.BooleanValue EXHAUSTED_BLOCK_JUMP;
    public static final ModConfigSpec.BooleanValue EXHAUSTED_BLOCK_SPRINT;
    public static final ModConfigSpec.DoubleValue EXHAUSTED_WALK_SLOWDOWN;
    public static final ModConfigSpec.BooleanValue WINDED_BLOCK_JUMP;

    public static final ModConfigSpec.IntValue SYNC_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue SYNC_DELTA;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        MAX_STAMINA = b.comment("最大体力值")
                .defineInRange("general.max_stamina", 100.0, 1.0, 1_000_000.0);
        APPLY_IN_CREATIVE = b.comment("创造模式是否消耗体力（默认关闭，创造下 HUD 自动隐藏）")
                .define("general.apply_in_creative", false);
        b.push("drain").comment("消耗速率");
        SPRINT_DRAIN_PER_SECOND = b.comment("疾跑每秒消耗体力（12 = 满体力约 8.3 秒疾跑）")
                .defineInRange("sprint_per_second", 12.0, 0.0, 100_000.0);
        JUMP_COST = b.comment("每次跳跃一次性消耗（疾跑中跳跃 = 疾跑消耗 + 该值）")
                .defineInRange("jump_cost", 10.0, 0.0, 100_000.0);
        SWIM_DRAIN_PER_SECOND = b.comment("游泳每秒消耗")
                .defineInRange("swim_per_second", 5.0, 0.0, 100_000.0);
        SWIM_SPRINT_DRAIN_PER_SECOND = b.comment("疾速游泳每秒消耗")
                .defineInRange("swim_sprint_per_second", 9.0, 0.0, 100_000.0);
        ATTACK_COST = b.comment("每次近战攻击命中实体的一次性消耗（0 = 关闭）")
                .defineInRange("attack_cost", 2.0, 0.0, 100_000.0);
        BREAK_BLOCK_COST = b.comment("每破坏一个方块的一次性消耗（0 = 关闭）")
                .defineInRange("break_block_cost", 1.0, 0.0, 100_000.0);
        b.pop();
        b.push("recovery").comment("恢复参数");
        RECOVERY_DELAY_TICKS = b.comment("停止消耗后多少 tick 才开始恢复（20 tick = 1 秒）")
                .defineInRange("delay_ticks", 24, 0, 1_200);
        RECOVERY_PER_SECOND = b.comment("正常恢复速度（每秒）")
                .defineInRange("per_second", 25.0, 0.0, 100_000.0);
        EXHAUSTED_RECOVERY_DELAY_TICKS = b.comment("体力透支（归零）后的额外恢复延迟")
                .defineInRange("exhausted_delay_ticks", 60, 0, 1_200);
        EXHAUSTED_RECOVERY_PER_SECOND = b.comment("透支状态下的恢复速度（每秒）")
                .defineInRange("exhausted_per_second", 15.0, 0.0, 100_000.0);
        b.pop();
        b.push("penalty").comment("低体力惩罚（更多惩罚留给生态模组通过 API/事件扩展）");
        SPRINT_STOP_THRESHOLD = b.comment("体力低于该值时禁止疾跑")
                .defineInRange("sprint_stop_threshold", 15.0, 0.0, 1_000_000.0);
        EXHAUSTED_RELEASE_THRESHOLD = b.comment("透支后需恢复到该值才解除禁跑")
                .defineInRange("exhausted_release_threshold", 30.0, 0.0, 1_000_000.0);
        EXHAUSTED_BLOCK_JUMP = b.comment("体力透支（归零）期间是否禁止跳跃", "通过 JUMP_STRENGTH 属性修饰符实现（同步到客户端，无橡皮筋）；", "透支期跳跃药水仍有 0.1/级 的微弱弹跳（原版公式加算，可忽略）")
                .define("exhausted_block_jump", true);
        EXHAUSTED_BLOCK_SPRINT = b.comment("体力透支（归零）期间是否禁止疾跑（严格模式核心开关）", "开启时透支玩家只能正常行走；关闭则仅保留低体力禁跑阈值")
                .define("exhausted_block_sprint", true);
        EXHAUSTED_WALK_SLOWDOWN = b.comment("透支期间行走减速比例（0 = 不减速，只能正常行走；0.15 = 移速 x0.85）", "通过 MOVEMENT_SPEED 属性修饰符实现，仅影响地面移速，不影响击退/坠落")
                .defineInRange("exhausted_walk_slowdown", 0.0, 0.0, 0.6);
        WINDED_BLOCK_JUMP = b.comment("低体力（低于禁跑阈值但未透支）时是否也禁止跳跃（默认关闭：保留最后一跳的战术逃生空间）")
                .define("winded_block_jump", false);
        b.pop();
        b.push("network").comment("同步节流（一般无需修改）");
        SYNC_INTERVAL_TICKS = b.comment("数值变化时的最小同步间隔（tick）")
                .defineInRange("sync_interval_ticks", 2, 1, 20);
        SYNC_DELTA = b.comment("触发同步的最小数值变化量")
                .defineInRange("sync_delta", 2.0, 0.1, 100.0);
        b.pop();

        SPEC = b.build();
    }

    private StaminaServerConfig() {
    }

    /** 便捷取值（DoubleValue -> float）。 */
    public static float f(ModConfigSpec.DoubleValue value) {
        return value.get().floatValue();
    }
}
