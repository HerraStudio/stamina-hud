package com.herra.stamina.client;

import com.herra.stamina.api.ClientDisplayBridge;
import com.herra.stamina.api.StaminaAPI;
import com.herra.stamina.config.StaminaClientConfig;
import com.herra.stamina.network.StaminaSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 客户端体力数据 + 动画状态机（单玩家静态状态）。
 *
 * <p>动画设计（参考三角洲行动但不照抄其僵硬感）：</p>
 * <ul>
 *   <li>display：对同步目标值的指数平滑（平滑过渡，无跳变）</li>
 *   <li>ghost：只在下降时滞后追赶 —— 格斗游戏血条式"残影"，被扣掉的体力以
 *       白→黄→红渐变色缓慢消失（爆发性消耗时的渐变动画）</li>
 *   <li>burstFlash：大额扣减时的白闪</li>
 *   <li>sparks：大额扣减时从扣减边缘飞出的像素火花（受重力）</li>
 *   <li>glint：回满瞬间一道扫光，随后自动隐藏</li>
 *   <li>visibilityAlpha：消耗时快速淡入 / 回满延迟后缓慢淡出</li>
 * </ul>
 */
public final class ClientStaminaData {
    private static final long NONE = Long.MIN_VALUE;
    private static final long GLINT_DURATION_MS = 420L;

    // ---- 服务端同步值 ----
    private static float max = 100.0F;
    private static float target = 100.0F;
    private static boolean exhaustedLock;
    private static boolean sprintBlocked;
    private static boolean initialized;

    // ---- 动画状态 ----
    private static float display = 100.0F;
    private static float ghost = 100.0F;
    private static float visibilityAlpha;
    private static float burstFlash;
    private static long fullSinceMs = NONE;
    private static long glintStartMs = NONE;
    private static boolean wasBelowFull;
    private static long lastFrameNs;

    // ---- 待触发的爆发效果（由 HUD 在下一帧转换为火花，坐标换算留在渲染层） ----
    private static int pendingBurstSparks;
    private static float pendingBurstEdgeRatio;

    /** 像素火花：位置由初速 + 重力解析求值，避免帧率相关漂移 */
    public static final class Spark {
        public final float x0, y0, vx, vy, life;
        public final long bornNs;
        public final boolean yellowish;

        Spark(float x0, float y0, float vx, float vy, float life, boolean yellowish) {
            this.x0 = x0;
            this.y0 = y0;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.bornNs = System.nanoTime();
            this.yellowish = yellowish;
        }

        public float x(float age) {
            return x0 + vx * age;
        }

        public float y(float age) {
            return y0 + vy * age + 0.5F * GRAVITY * age * age;
        }

        public float alpha(float age) {
            return Mth.clamp(1.0F - age / life, 0.0F, 1.0F);
        }
    }

    private static final float GRAVITY = 110.0F; // px/s^2（GUI 像素）
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RNG = new Random();

    // ------------------------------------------------------------------ 输入

    public static void acceptSync(StaminaSyncPayload payload) {
        float oldTarget = initialized ? target : payload.stamina();
        max = Math.max(1.0F, payload.maxStamina());
        target = Mth.clamp(payload.stamina(), 0.0F, max);
        exhaustedLock = payload.exhaustedLock();
        sprintBlocked = payload.sprintBlocked();
        if (!initialized) {
            display = ghost = target;
            initialized = true;
        }
        float drop = oldTarget - target;
        if (drop >= 4.0F) {
            // 爆发性扣减：白闪 + 火花（跳跃、API 大额扣除等）
            burstFlash = Math.min(1.0F, burstFlash + drop / 12.0F);
            pendingBurstSparks = Math.min(10, pendingBurstSparks + Math.max(2, (int) (drop / 2.0F)));
            pendingBurstEdgeRatio = target / max;
        }
    }

    /** 客户端 tick：玩家不在时重置；镜像低体力禁跑。
     * 主压制在 LocalPlayerMixin（原版饥饿禁跑判定点，双端一致）；
     * 这里的镜像仅作 desync 兜底 —— 若同步包迟到导致短暂疾跑，主动关掉。 */
    public static void clientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            reset();
            return;
        }
        if (sprintBlocked && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    public static void reset() {
        max = 100.0F;
        target = display = ghost = 100.0F;
        exhaustedLock = false;
        sprintBlocked = false;
        initialized = false;
        visibilityAlpha = 0.0F;
        burstFlash = 0.0F;
        fullSinceMs = NONE;
        glintStartMs = NONE;
        wasBelowFull = false;
        lastFrameNs = 0L;
        pendingBurstSparks = 0;
        SPARKS.clear();
    }

    // ------------------------------------------------------------------ 帧推进

    /** 每帧由 HUD 渲染器调用（真实时间驱动，帧率无关） */
    public static void updateFrame() {
        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.016F : Math.min(0.1F, (nowNs - lastFrameNs) / 1_000_000_000.0F);
        lastFrameNs = nowNs;
        long nowMs = nowNs / 1_000_000L;
        float speed = Mth.clamp(StaminaClientConfig.ANIMATION_SPEED.get().floatValue(), 0.1F, 10.0F);

        // 平滑显示值
        display += (target - display) * (1.0F - (float) Math.exp(-dt * 14.0F * speed));
        // 幽灵拖尾：只向下滞后
        if (ghost > display) {
            ghost -= (ghost - display) * (1.0F - (float) Math.exp(-dt * 3.2F * speed));
            if (ghost - display < 0.05F) ghost = display;
        } else {
            ghost = display;
        }

        boolean full = target >= max - 0.01F;
        if (full) {
            if (fullSinceMs == NONE) fullSinceMs = nowMs;
        } else {
            fullSinceMs = NONE;
            wasBelowFull = true;
            glintStartMs = NONE; // 回满前再次消耗则取消扫光
        }
        // 回满瞬间（display 追上 max）触发扫光
        if (full && wasBelowFull && display >= max - 0.5F) {
            glintStartMs = nowMs;
            wasBelowFull = false;
        }
        if (glintStartMs != NONE && nowMs - glintStartMs > GLINT_DURATION_MS) {
            glintStartMs = NONE;
        }

        // 自动隐藏 / 淡入
        boolean wantVisible = !full
                || !StaminaClientConfig.AUTO_HIDE.get()
                || (fullSinceMs != NONE && nowMs - fullSinceMs < StaminaClientConfig.HIDE_DELAY_TICKS.get() * 50L);
        float fadeRate = wantVisible ? 16.0F : 6.0F;
        visibilityAlpha += ((wantVisible ? 1.0F : 0.0F) - visibilityAlpha)
                * (1.0F - (float) Math.exp(-dt * fadeRate * Math.min(2.0F, speed)));

        // 白闪衰减
        burstFlash = Math.max(0.0F, burstFlash - dt * 3.5F);

        // 火花生命周期
        SPARKS.removeIf(spark -> (nowNs - spark.bornNs) / 1_000_000_000.0F >= spark.life);
    }

    /** 由 HUD 渲染器调用：把待触发的爆发转换为火花（spawnX/spawnY 为条内局部坐标） */
    public static void drainBurst(float spawnX, float spawnY) {
        int count = pendingBurstSparks;
        pendingBurstSparks = 0;
        for (int i = 0; i < count; i++) {
            float vx = (RNG.nextFloat() * 2.0F - 1.0F) * 34.0F;
            float vy = -(6.0F + RNG.nextFloat() * 34.0F);
            float life = 0.28F + RNG.nextFloat() * 0.34F;
            SPARKS.add(new Spark(spawnX, spawnY, vx, vy, life, RNG.nextFloat() < 0.35F));
        }
    }

    // ------------------------------------------------------------------ 读取

    public static float getMax() {
        return max;
    }

    public static float getDisplay() {
        return display;
    }

    public static float getGhost() {
        return ghost;
    }

    public static float getRatio() {
        return Mth.clamp(display / max, 0.0F, 1.0F);
    }

    public static float getGhostRatio() {
        return Mth.clamp(ghost / max, 0.0F, 1.0F);
    }

    public static boolean isExhausted() {
        return exhaustedLock;
    }

    public static boolean isWinded() {
        return sprintBlocked && !exhaustedLock;
    }

    public static boolean isSprintBlocked() {
        return sprintBlocked;
    }

    public static float getVisibilityAlpha() {
        return visibilityAlpha;
    }

    public static float getBurstFlash() {
        return burstFlash;
    }

    /** 0~1 扫光进度；-1 表示无扫光 */
    public static float getGlintProgress() {
        if (glintStartMs == NONE) return -1.0F;
        return (System.currentTimeMillis() - glintStartMs) / (float) GLINT_DURATION_MS;
    }

    public static List<Spark> getSparks() {
        return SPARKS;
    }

    // ------------------------------------------------------------------ 生态桥

    static {
        // 把插值显示值暴露给其他客户端 HERRA 模组（GWO 枪械 HUD 等），
        // 它们通过 StaminaAPI.getRatio(player) 等方法读取。
        StaminaAPI.installClientDisplay(new ClientDisplayBridge() {
            @Override
            public double ratio() {
                return getRatio();
            }

            @Override
            public float max() {
                return getMax();
            }

            @Override
            public boolean depleted() {
                return isExhausted();
            }
        });
    }
}
