package com.herra.stamina.config;

import com.herra.stamina.client.hud.BarStyle;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * HUD 表现配置（CLIENT 类型，存于客户端 config 目录，每个玩家可自行调整）。
 *
 * <p>游戏内可直接用 {@code /sta} 打开设置界面可视化调整位置/样式/提示音，
 * 修改即时生效并写回本文件。</p>
 *
 * <p>配色为四段渐变：满（米白）→ 中（金黄）→ 低（橙）→ 临界（红）。
 * 注意：该配色仅作用于「经典」样式，其他样式预设自带配色。</p>
 */
public final class StaminaClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.IntValue HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.EnumValue<BarStyle> HUD_STYLE;
    public static final ModConfigSpec.BooleanValue AUTO_HIDE;
    public static final ModConfigSpec.IntValue HIDE_DELAY_TICKS;
    public static final ModConfigSpec.BooleanValue SHOW_ICON;
    public static final ModConfigSpec.BooleanValue SHOW_BODY_STATUS;
    public static final ModConfigSpec.BooleanValue SHOW_STATUS_TEXT;
    public static final ModConfigSpec.DoubleValue ANIMATION_SPEED;

    /** 体力耗尽提示音。 */
    public static final ModConfigSpec.BooleanValue SOUND_ENABLED;
    public static final ModConfigSpec.IntValue SOUND_VOLUME;

    // 四段渐变配色（RGB，alpha 由 HUD 逻辑控制；仅「经典」样式使用）
    public static final ModConfigSpec.IntValue COLOR_FULL;
    public static final ModConfigSpec.IntValue COLOR_MID;
    public static final ModConfigSpec.IntValue COLOR_LOW;
    public static final ModConfigSpec.IntValue COLOR_CRIT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("hud");
        HUD_ENABLED = b.comment("是否显示体力条 HUD")
                .define("enabled", true);
        HUD_OFFSET_X = b.comment("水平偏移（0 = 屏幕正中，对齐物品栏中心）")
                .defineInRange("offset_x", 0, -1_000, 1_000);
        HUD_OFFSET_Y = b.comment("体力条底边距屏幕底部的像素数（默认 84：避开自定义血条与护甲条，",
                        "位于氧气条上方；数值越大越靠上。游戏内 /sta 可视化拖拽调整，即时生效）")
                .defineInRange("offset_y", 84, 0, 1_000);
        HUD_SCALE = b.comment("体力条整体缩放（1.0 = 原始尺寸，像素风建议 0.5~2.0）")
                .defineInRange("scale", 1.0, 0.5, 2.0);
        HUD_STYLE = b.comment("样式预设：classic 经典 / tactical 战术 / minimal 极简 / energy 生电（/sta 菜单可选）")
                .defineEnum("style", BarStyle.CLASSIC);
        AUTO_HIDE = b.comment("体力回满后自动隐藏（再次消耗时淡入）")
                .define("auto_hide", true);
        HIDE_DELAY_TICKS = b.comment("回满后延迟多少 tick 再隐藏（20 tick = 1 秒）")
                .defineInRange("hide_delay_ticks", 40, 0, 600);
        SHOW_ICON = b.comment("体力条左侧显示闪电图标")
                .define("show_icon", true);
        SHOW_BODY_STATUS = b.comment("体力条下方显示身体状态占位图标（供后续身体部位系统扩展）")
                .define("show_body_status", true);
        SHOW_STATUS_TEXT = b.comment("低体力/透支时显示状态文字（呼吸急促/体力透支）")
                .define("show_status_text", true);
        ANIMATION_SPEED = b.comment("动画速度倍率（1.0 = 默认手感）")
                .defineInRange("animation_speed", 1.0, 0.1, 10.0);
        b.pop();

        b.push("sound").comment("体力耗尽提示音（低频心跳）");
        SOUND_ENABLED = b.comment("体力条耗尽瞬间是否播放提示音")
                .define("exhausted_enabled", true);
        SOUND_VOLUME = b.comment("提示音音量（0~100，0 或关闭开关 = 静音；/sta 菜单可调）")
                .defineInRange("exhausted_volume", 60, 0, 100);
        b.pop();

        b.push("colors").comment("四段渐变配色（0xRRGGBB，满→中→低→临界；仅「经典」样式使用）");
        COLOR_FULL = b.comment("体力充足时的填充色（米白）")
                .defineInRange("full", 0xEDF3E2, 0x000000, 0xFFFFFF);
        COLOR_MID = b.comment("中等体力时的填充色（金黄）")
                .defineInRange("mid", 0xF2C94C, 0x000000, 0xFFFFFF);
        COLOR_LOW = b.comment("低体力时的填充色（橙）")
                .defineInRange("low", 0xE8873B, 0x000000, 0xFFFFFF);
        COLOR_CRIT = b.comment("临界体力时的填充色（红）")
                .defineInRange("crit", 0xD64541, 0x000000, 0xFFFFFF);
        b.pop();

        SPEC = b.build();
    }

    private StaminaClientConfig() {
    }
}
