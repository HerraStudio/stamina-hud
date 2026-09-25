package com.herra.stamina.client.hud;

import com.herra.stamina.HerraStamina;
import com.herra.stamina.config.StaminaClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 体力条样式预设（皮肤）。
 *
 * <p>每个样式 = 边框贴图 + 填充叠加贴图 + 四段渐变配色。</p>
 * <ul>
 *   <li>{@link #CLASSIC} —— 经典：原版金蓝边框 + 米白→金黄→橙→红（颜色走客户端配置）</li>
 *   <li>{@link #TACTICAL} —— 战术：装甲块边框 + 硬分段刻度，军绿荧光→卡其→橙→警示红</li>
 *   <li>{@link #MINIMAL} —— 极简：1px 细线边框，冷白→银灰→琥珀→红</li>
 *   <li>{@link #ENERGY} —— 生电：青色辉光切角边框 + 斜纹高光，青→天蓝→紫→品红</li>
 * </ul>
 *
 * <p>该类不能引用任何 client-only 类型（被 {@link StaminaClientConfig} 的
 * EnumValue 引用，服务器构建 CLIENT 配置 spec 时也会加载本类）。</p>
 */
public enum BarStyle {
    CLASSIC("classic", 0xEDF3E2, 0xF2C94C, 0xE8873B, 0xD64541),
    TACTICAL("tactical", 0xB8E62E, 0xE8C43D, 0xE8873B, 0xE04A3A),
    MINIMAL("minimal", 0xE8EEF2, 0xBFC9D4, 0xE8A44C, 0xE05A50),
    ENERGY("energy", 0x52F0E0, 0x4CA0F0, 0x9A70F0, 0xF05090);

    private final String id;
    private final int full;
    private final int mid;
    private final int low;
    private final int crit;

    BarStyle(String id, int full, int mid, int low, int crit) {
        this.id = id;
        this.full = full;
        this.mid = mid;
        this.low = low;
        this.crit = crit;
    }

    public String id() {
        return id;
    }

    /** 样式显示名（lang key：herra_stamina.style.&lt;id&gt;）。 */
    public Component displayName() {
        return Component.translatable("herra_stamina.style." + id);
    }

    /** 边框贴图（100x12）。CLASSIC 复用 v1.0.0 的经典贴图。 */
    public ResourceLocation frame() {
        return this == CLASSIC
                ? HerraStamina.id("textures/gui/stamina_frame.png")
                : HerraStamina.id("textures/gui/style_" + id + "_frame.png");
    }

    /** 填充叠加贴图（96x8，仅 alpha 高光/刻痕）。 */
    public ResourceLocation shade() {
        return this == CLASSIC
                ? HerraStamina.id("textures/gui/stamina_fill_shade.png")
                : HerraStamina.id("textures/gui/style_" + id + "_shade.png");
    }

    /** 充足色（0xRRGGBB）。CLASSIC 走客户端配置。 */
    public int colorFull() {
        return this == CLASSIC ? (StaminaClientConfig.COLOR_FULL.get() & 0xFFFFFF) : full;
    }

    /** 中等色。 */
    public int colorMid() {
        return this == CLASSIC ? (StaminaClientConfig.COLOR_MID.get() & 0xFFFFFF) : mid;
    }

    /** 低体力色。 */
    public int colorLow() {
        return this == CLASSIC ? (StaminaClientConfig.COLOR_LOW.get() & 0xFFFFFF) : low;
    }

    /** 临界色。 */
    public int colorCrit() {
        return this == CLASSIC ? (StaminaClientConfig.COLOR_CRIT.get() & 0xFFFFFF) : crit;
    }

    /** 按 id 解析（配置存储 / 指令参数），未知 id 返回 null。 */
    @Nullable
    public static BarStyle byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (BarStyle style : values()) {
            if (style.id.equals(id)) {
                return style;
            }
        }
        return null;
    }

    /** 当前玩家选择的样式（读客户端配置）。 */
    public static BarStyle current() {
        return StaminaClientConfig.HUD_STYLE.get();
    }
}
