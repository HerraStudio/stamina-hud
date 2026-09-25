package com.herra.stamina.api.event;

import com.herra.stamina.api.StaminaAction;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/**
 * 体力相关事件基类。全部在 NeoForge 游戏事件总线（NeoForge.EVENT_BUS）
 * 上触发，仅服务端。
 *
 * <p>说明：手动实现取消标志而非 NeoForge 的 ICancellableEvent，
 * 以获得跨版本稳定的取消语义。</p>
 */
public abstract class StaminaEvent extends Event {

    protected final Player player;

    protected StaminaEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 一次性消耗发生前触发（跳跃、API 自定义消耗）。
     * 疾跑/游泳等持续消耗不触发本事件 —— 请使用
     * {@link com.herra.stamina.api.StaminaDrainModifier} 改写持续消耗规则。
     *
     * <p>可取消（取消 = 拒绝这次消耗），数量可通过 {@link #setAmount(float)} 改写。</p>
     */
    public static class Consume extends StaminaEvent {
        private final StaminaAction action;
        private float amount;
        private boolean canceled;

        public Consume(Player player, StaminaAction action, float amount) {
            super(player);
            this.action = action;
            this.amount = Math.max(0.0F, amount);
        }

        public StaminaAction getAction() {
            return action;
        }

        public float getAmount() {
            return amount;
        }

        public void setAmount(float amount) {
            this.amount = Math.max(0.0F, amount);
        }

        /** 取消本次消耗（体力不减）。 */
        public boolean isCanceled() {
            return canceled;
        }

        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }
    }

    /** 每次体力数值变化后触发（高频，监听请保持轻量）。 */
    public static class Changed extends StaminaEvent {
        private final float oldValue;
        private final float newValue;

        public Changed(Player player, float oldValue, float newValue) {
            super(player);
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public float getOldValue() {
            return oldValue;
        }

        public float getNewValue() {
            return newValue;
        }
    }

    /** 体力透支（归零并进入锁定）瞬间触发一次。 */
    public static class Exhausted extends StaminaEvent {
        public Exhausted(Player player) {
            super(player);
        }
    }

    /** 透支锁定解除（回气到阈值）时触发。 */
    public static class Recovered extends StaminaEvent {
        public Recovered(Player player) {
            super(player);
        }
    }
}
