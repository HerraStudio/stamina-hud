package com.herra.stamina.api;

import java.util.Objects;

/**
 * 定时体力修改器 —— 医疗 / 增益类模组的主要集成点（如医药模组的兴奋剂、
 * 能量饮料、重病 debuff 等），一次调用即可同时影响：
 *
 * <ul>
 *   <li>体力上限（加算 + 乘算）——「增加体力条的长度」</li>
 *   <li>所有消耗的乘算倍率 ——「减少体力条的消耗速率」（疾跑/跳跃/游泳/自定义全部生效）</li>
 *   <li>恢复速度（加算 + 乘算）</li>
 * </ul>
 *
 * <p>典型用法（医药模组：能量饮料，喝下后 90 秒内消耗减半、上限 +30）：</p>
 * <pre>{@code
 * StaminaAPI.applyModifier(player, StaminaModifier.builder("herra_med:energy_drink")
 *         .maxStaminaBonus(30.0F)
 *         .drainMultiplier(0.5F)
 *         .durationSeconds(90)
 *         .build());
 * }</pre>
 *
 * <p>同 id 重复 apply = 刷新（覆盖旧的剩余时间与数值）。修改器在服务端
 * 每 tick 自动倒计时，到期自动失效并重新同步 HUD；不需要医药模组自己计时。</p>
 *
 * <p>修改器为运行时数据（不落盘）：玩家退出即消失。药品效果本身由医药
 * 模组管理（它的药效时长与体力修改器对齐即可），战局重置等场景可调用
 * {@link StaminaAPI#clearModifiers} 主动清空。</p>
 */
public final class StaminaModifier {

    private final String id;
    private final float maxStaminaBonus;
    private final float maxStaminaMultiplier;
    private final float drainMultiplier;
    private final float regenBonus;
    private final float regenMultiplier;
    private final int durationTicks;

    private StaminaModifier(Builder builder) {
        this.id = builder.id;
        this.maxStaminaBonus = builder.maxStaminaBonus;
        this.maxStaminaMultiplier = builder.maxStaminaMultiplier;
        this.drainMultiplier = builder.drainMultiplier;
        this.regenBonus = builder.regenBonus;
        this.regenMultiplier = builder.regenMultiplier;
        this.durationTicks = builder.durationTicks;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 全局唯一 id（建议 命名空间:名字，如 "herra_med:energy_drink"）。 */
    public String id() {
        return id;
    }

    /** 体力上限加算（可与乘算叠加，最终 = (基础 + Σ加算) × Π乘算）。 */
    public float maxStaminaBonus() {
        return maxStaminaBonus;
    }

    /** 体力上限乘算（1.0 = 无影响）。 */
    public float maxStaminaMultiplier() {
        return maxStaminaMultiplier;
    }

    /** 所有体力消耗的乘算倍率（1.0 = 无影响；0.6 = 全部消耗打六折）。 */
    public float drainMultiplier() {
        return drainMultiplier;
    }

    /** 恢复速度加算（每秒，正常与透支恢复都生效）。 */
    public float regenBonus() {
        return regenBonus;
    }

    /** 恢复速度乘算（1.0 = 无影响）。 */
    public float regenMultiplier() {
        return regenMultiplier;
    }

    /** 剩余 tick（-1 = 永久，直到被 clear 或玩家退出）。 */
    public int durationTicks() {
        return durationTicks;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StaminaModifier that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "StaminaModifier[" + id + "]";
    }

    /** 构建器。所有字段默认「无影响」。 */
    public static final class Builder {
        private final String id;
        private float maxStaminaBonus = 0.0F;
        private float maxStaminaMultiplier = 1.0F;
        private float drainMultiplier = 1.0F;
        private float regenBonus = 0.0F;
        private float regenMultiplier = 1.0F;
        private int durationTicks = -1;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "modifier id 不能为空");
        }

        public Builder maxStaminaBonus(float bonus) {
            this.maxStaminaBonus = bonus;
            return this;
        }

        public Builder maxStaminaMultiplier(float multiplier) {
            this.maxStaminaMultiplier = Math.max(0.0F, multiplier);
            return this;
        }

        public Builder drainMultiplier(float multiplier) {
            this.drainMultiplier = Math.max(0.0F, multiplier);
            return this;
        }

        public Builder regenBonus(float bonusPerSecond) {
            this.regenBonus = bonusPerSecond;
            return this;
        }

        public Builder regenMultiplier(float multiplier) {
            this.regenMultiplier = Math.max(0.0F, multiplier);
            return this;
        }

        /** 持续 tick 数（20 tick = 1 秒）。-1 = 永久。 */
        public Builder durationTicks(int ticks) {
            this.durationTicks = Math.max(-1, ticks);
            return this;
        }

        public Builder durationSeconds(int seconds) {
            return durationTicks(seconds * 20);
        }

        public Builder durationMinutes(int minutes) {
            return durationTicks(minutes * 20 * 60);
        }

        public StaminaModifier build() {
            return new StaminaModifier(this);
        }
    }
}
