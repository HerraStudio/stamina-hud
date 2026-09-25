package com.herra.stamina.client.hud;

import com.herra.stamina.HerraStamina;
import com.herra.stamina.client.ClientStaminaData;
import com.herra.stamina.config.StaminaClientConfig;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.Position;
import com.lowdragmc.lowdraglib2.math.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 体力条 HUD 根元素（全屏透明层，内部自行定位）。
 *
 * <p>渲染分层（自下而上）：</p>
 * <ol>
 *   <li>槽内暗底</li>
 *   <li>幽灵拖尾 —— 被扣掉的体力逐列白→黄→红渐变后消失（大额扣减时的渐变动画）</li>
 *   <li>主体填充 —— 按当前比例白→黄→红渐变 + 低体力呼吸脉动 + 归零闪烁 + 2px 亮色端帽</li>
 *   <li>填充纹理叠加层（高光/阴影/分段刻痕，像素风质感）</li>
 *   <li>边框（圆角像素边框）</li>
 *   <li>闪电图标（随填充色染色）</li>
 *   <li>爆发白闪 / 回满扫光 / 像素火花</li>
 *   <li>下方：身体状态占位图标 或 呼吸急促/体力透支 文字</li>
 * </ol>
 *
 * <p>v1.0.2：全部外观由 {@link BarStyle} 预设驱动（4 种皮肤），
 * 并支持整体缩放（pose 矩阵，像素几何不变）。</p>
 *
 * <p>贴图几何与 gen_textures.py / gen_bar_styles.py 严格对应：BAR 100x12，边框 2px，内部 96x8。</p>
 */
@OnlyIn(Dist.CLIENT)
public class StaminaHudElement extends UIElement {

    // ---- 贴图几何（与 resources 内 PNG 一致，全部样式共用） ----
    public static final int BAR_W = 100;
    public static final int BAR_H = 12;
    public static final int BORDER = 2;
    public static final int INNER_W = BAR_W - BORDER * 2; // 96
    public static final int INNER_H = BAR_H - BORDER * 2; // 8

    private static final ResourceLocation BOLT_TEX_LOC = HerraStamina.id("textures/gui/stamina_icon.png");
    private static final ResourceLocation BODY_HEAD = HerraStamina.id("textures/gui/body_head.png");
    private static final ResourceLocation BODY_CHEST = HerraStamina.id("textures/gui/body_chest.png");
    private static final ResourceLocation BODY_ARMS = HerraStamina.id("textures/gui/body_arms.png");
    private static final ResourceLocation BODY_LEGS = HerraStamina.id("textures/gui/body_legs.png");

    // 可复用的纹理实例（渲染线程内安全变更颜色/子区域）。
    // 每个样式独立缓存边框/叠加层，切换样式零重建开销。
    private static final Map<BarStyle, SpriteTexture> FRAMES = new EnumMap<>(BarStyle.class);
    private static final Map<BarStyle, SpriteTexture> SHADES = new EnumMap<>(BarStyle.class);

    private static SpriteTexture frame(BarStyle style) {
        return FRAMES.computeIfAbsent(style, s -> new SpriteTexture().setImageLocation(s.frame()));
    }

    private static SpriteTexture shade(BarStyle style) {
        return SHADES.computeIfAbsent(style, s -> new SpriteTexture().setImageLocation(s.shade()));
    }

    private static final SpriteTexture BOLT = new SpriteTexture().setImageLocation(BOLT_TEX_LOC);
    private static final SpriteTexture BODY_HEAD_TEX = new SpriteTexture().setImageLocation(BODY_HEAD);
    private static final SpriteTexture BODY_CHEST_TEX = new SpriteTexture().setImageLocation(BODY_CHEST);
    private static final SpriteTexture BODY_ARMS_TEX = new SpriteTexture().setImageLocation(BODY_ARMS);
    private static final SpriteTexture BODY_LEGS_TEX = new SpriteTexture().setImageLocation(BODY_LEGS);

    private static final Component TEXT_WINDED = Component.translatable("herra_stamina.hud.winded");
    private static final Component TEXT_EXHAUSTED = Component.translatable("herra_stamina.hud.exhausted");

    // ---- 配色 ----
    private static final int BG_EMPTY = argb(255, 13, 15, 19);

    // 四段渐变配色（由当前样式预设决定；CLASSIC 走客户端配置）
    private static BarStyle style() {
        return BarStyle.current();
    }

    private static int colorFull() {
        return style().colorFull();
    }

    private static int colorMid() {
        return style().colorMid();
    }

    private static int colorLow() {
        return style().colorLow();
    }

    private static int colorCrit() {
        return style().colorCrit();
    }

    private static final int RGB_SPARK = rgb(0xFF, 0xFF, 0xFF);
    private static final int RGB_SPARK_YELLOW = rgb(0xF2, 0xC9, 0x4C);
    private static final int COLOR_BODY_DIM = argb(200, 168, 176, 168);
    private static final int COLOR_WINDED = rgb(0xF2, 0xC7, 0x4C);
    private static final int COLOR_EXHAUSTED = rgb(0xE2, 0x4A, 0x40);

    @Override
    public void drawContents(GUIContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!StaminaClientConfig.HUD_ENABLED.get()) return;

        ClientStaminaData.updateFrame();
        float alpha = ClientStaminaData.getVisibilityAlpha();
        if (alpha <= 0.02F) return;

        // 爆发火花换算（在拿到条内坐标后立即消费）
        ClientStaminaData.drainBurst(
                INNER_W * ClientStaminaData.getRatio(), INNER_H * 0.5F);

        GuiGraphics g = ctx.graphics;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float scale = Mth.clamp(StaminaClientConfig.HUD_SCALE.get().floatValue(), 0.5F, 2.0F);
        // 缩放后条的实际占位（定位基准 = 缩放后底边中点）
        int drawW = Math.round(BAR_W * scale);
        int x = Math.round(screenW / 2.0F - drawW / 2.0F
                + StaminaClientConfig.HUD_OFFSET_X.get() * scale);
        int y = Math.round(screenH - StaminaClientConfig.HUD_OFFSET_Y.get() * scale);

        // 整体缩放：translate 到条左上角再 scale，内部几何全部保持像素常量
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        renderBar(g, ctx, 0, 0, alpha);
        renderBelow(g, ctx, mc.font, 0, 0, alpha);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ 主条

    private void renderBar(GuiGraphics g, GUIContext ctx, int x, int y, float alpha) {
        BarStyle style = style();
        int innerX = x + BORDER;
        int innerY = y + BORDER;
        float ratio = ClientStaminaData.getRatio();
        float ghostRatio = ClientStaminaData.getGhostRatio();
        int fillW = Math.round(INNER_W * ratio);
        int ghostW = Math.round(INNER_W * ghostRatio);

        // 1. 槽内暗底
        g.fill(innerX, innerY, innerX + INNER_W, innerY + INNER_H, mulAlpha(BG_EMPTY, alpha));

        // 2. 幽灵拖尾（fill 边缘=红，ghost 边缘=白，逐列渐变）
        if (ghostW > fillW) {
            int span = ghostW - fillW;
            for (int i = 0; i < span; i++) {
                float t = span == 1 ? 1.0F : (float) i / span; // 0 红 -> 1 白
                int c = ghostColumnColor(t, alpha);
                g.fill(innerX + fillW + i, innerY, innerX + fillW + i + 1, innerY + INNER_H, c);
            }
            // 拖尾区也叠刻痕纹理（保持整体感）
            SpriteTexture shadeTex = shade(style);
            shadeTex.setColor(mulAlpha(0xFFFFFFFF, alpha));
            shadeTex.setSpritePosition(Position.of(fillW, 0));
            shadeTex.setSpriteSize(Size.of(span, INNER_H));
            ctx.drawTexture(shadeTex, innerX + fillW, innerY, span, INNER_H);
        }

        // 3. 主体填充
        int fillColor = fillColor(ratio, alpha);
        if (fillW > 0) {
            g.fill(innerX, innerY, innerX + fillW, innerY + INNER_H, fillColor);
            // 端帽 2px 亮色（像素画的"高光边"）
            int cap = mixRgb(fillColor, 0xFFFFFFFF, 0.45F);
            int capX = innerX + Math.max(0, fillW - 2);
            g.fill(capX, innerY, innerX + fillW, innerY + INNER_H, cap);
            // 纹理叠加层（只叠加到填充宽度，1:1 采样无拉伸）
            SpriteTexture shadeTex = shade(style);
            shadeTex.setColor(mulAlpha(0xFFFFFFFF, alpha));
            shadeTex.setSpritePosition(Position.of(0, 0));
            shadeTex.setSpriteSize(Size.of(fillW, INNER_H));
            ctx.drawTexture(shadeTex, innerX, innerY, fillW, INNER_H);
        }

        // 4. 边框（随整体淡出）
        SpriteTexture frameTex = frame(style);
        frameTex.setColor(mulAlpha(0xFFFFFFFF, alpha));
        frameTex.setSpritePosition(Position.of(0, 0));
        frameTex.setSpriteSize(Size.of(BAR_W, BAR_H));
        ctx.drawTexture(frameTex, x, y, BAR_W, BAR_H);

        // 5. 闪电图标（染当前填充色）
        if (StaminaClientConfig.SHOW_ICON.get()) {
            BOLT.setColor(mulAlpha(fillColor, 1.0F));
            BOLT.setSpritePosition(Position.of(0, 0));
            BOLT.setSpriteSize(Size.of(9, 9));
            ctx.drawTexture(BOLT, x - 11, y + (BAR_H - 9) / 2, 9, 9);
        }

        // 6. 爆发白闪
        float flash = ClientStaminaData.getBurstFlash();
        if (flash > 0.01F) {
            g.fill(innerX, innerY, innerX + INNER_W, innerY + INNER_H,
                    argb((int) (flash * 0.30F * alpha * 255.0F), 255, 255, 255));
        }

        // 7. 回满扫光
        float glint = ClientStaminaData.getGlintProgress();
        if (glint >= 0.0F && glint <= 1.0F) {
            float bandX = glint * (INNER_W + 12.0F) - 6.0F;
            float amp = (float) Math.sin(Math.PI * glint);
            for (int i = 0; i < 4; i++) {
                int col = innerX + Math.round(bandX) + i;
                if (col < innerX || col >= innerX + INNER_W) continue;
                int a = (int) (amp * (255.0F * (4 - i) / 4.0F) * 0.55F * alpha);
                g.fill(col, innerY, col + 1, innerY + INNER_H, argb(a, 255, 255, 255));
            }
        }

        // 8. 像素火花
        long nowNs = System.nanoTime();
        List<ClientStaminaData.Spark> sparks = ClientStaminaData.getSparks();
        if (!sparks.isEmpty()) {
            for (ClientStaminaData.Spark spark : sparks) {
                float age = (nowNs - spark.bornNs) / 1_000_000_000.0F;
                if (age >= spark.life) continue;
                float a = spark.alpha(age) * alpha;
                int sx = Math.round(innerX + spark.x(age));
                int sy = Math.round(innerY + spark.y(age));
                g.fill(sx, sy, sx + 2, sy + 2,
                        withAlpha(spark.yellowish ? RGB_SPARK_YELLOW : RGB_SPARK, a));
            }
        }
    }

    // ------------------------------------------------------------------ 条下方

    private void renderBelow(GuiGraphics g, GUIContext ctx, Font font, int x, int y, float alpha) {
        boolean exhausted = ClientStaminaData.isExhausted();
        boolean winded = ClientStaminaData.isWinded();

        if (StaminaClientConfig.SHOW_STATUS_TEXT.get() && (exhausted || winded)) {
            // 状态文字（呼吸式透明度）
            Component text = exhausted ? TEXT_EXHAUSTED : TEXT_WINDED;
            float breathe = 0.72F + 0.28F * (float) Math.sin(System.currentTimeMillis() * (exhausted ? 0.012 : 0.008));
            int color = withAlpha(exhausted ? COLOR_EXHAUSTED : COLOR_WINDED, Mth.clamp(breathe, 0, 1) * alpha);
            int tw = font.width(text);
            g.drawString(font, text, x + BAR_W / 2 - tw / 2, y + BAR_H + 2, color, true);
        } else if (StaminaClientConfig.SHOW_BODY_STATUS.get()) {
            // 身体状态占位图标（供后续身体部位系统扩展）
            int iconY = y + BAR_H + 2;
            int startX = x + BAR_W / 2 - 17; // 4 个 7px 图标 + 3 个 2px 间隙 = 34
            drawBodyIcon(ctx, BODY_HEAD_TEX, startX, iconY, 7, 6, alpha);
            drawBodyIcon(ctx, BODY_CHEST_TEX, startX + 9, iconY, 7, 6, alpha);
            drawBodyIcon(ctx, BODY_ARMS_TEX, startX + 18, iconY, 7, 6, alpha);
            drawBodyIcon(ctx, BODY_LEGS_TEX, startX + 27, iconY, 7, 7, alpha);
        }
    }

    private void drawBodyIcon(GUIContext ctx, SpriteTexture tex, int x, int y, int w, int h, float alpha) {
        tex.setColor(mulAlpha(COLOR_BODY_DIM, alpha));
        tex.setSpritePosition(Position.of(0, 0));
        tex.setSpriteSize(Size.of(w, h));
        ctx.drawTexture(tex, x, y, w, h);
    }

    // ------------------------------------------------------------------ 颜色

    /** 主体填充色：比例渐变 + 低体力脉动 + 归零闪烁 */
    private static int fillColor(float ratio, float alpha) {
        int rgb;
        if (ratio >= 0.5F) {
            rgb = lerpRgb(colorMid(), colorFull(), (ratio - 0.5F) * 2.0F);
        } else if (ratio >= 0.2F) {
            rgb = lerpRgb(colorLow(), colorMid(), (ratio - 0.2F) / 0.3F);
        } else {
            rgb = lerpRgb(colorCrit(), colorLow(), ratio / 0.2F);
        }
        double now = System.currentTimeMillis();
        if (ClientStaminaData.isSprintBlocked() && ratio > 0.02F) {
            // 低体力呼吸脉动（幅度小，避免刺眼）
            float wave = 0.5F + 0.5F * (float) Math.sin(now * 0.008);
            rgb = mixRgb(rgb, colorCrit(), 0.28F * wave);
        }
        if (ratio <= 0.02F) {
            // 归零硬闪烁
            float blink = (float) Math.sin(now * 0.019);
            rgb = blink > 0 ? mixRgb(rgb, 0xFFFFFFFF, 0.35F) : mixRgb(rgb, 0xFF000000, 0.25F);
        }
        return argb((int) (alpha * 255.0F), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** 幽灵拖尾单列颜色：t=0 红(旧) -> 0.5 黄 -> 1 白(新)，半透明 */
    private static int ghostColumnColor(float t, float alpha) {
        int rgb = t < 0.5F ? lerpRgb(colorCrit(), colorMid(), t * 2.0F) : lerpRgb(colorMid(), colorFull(), (t - 0.5F) * 2.0F);
        int a = (int) (alpha * (120 + 70 * t));
        return argb(a, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    // ------------------------------------------------------------------ 工具

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private static int argb(int a, int r, int g, int b) {
        return (Mth.clamp(a, 0, 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, float alpha) {
        return argb((int) (Mth.clamp(alpha, 0, 1) * 255.0F),
                (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static int mulAlpha(int argb, float mult) {
        int a = (int) (((argb >>> 24) & 0xFF) * Mth.clamp(mult, 0, 1));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerpRgb(int c1, int c2, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return rgb(r, g, b);
    }

    private static int mixRgb(int c1, int c2, float t) {
        return lerpRgb(c1, c2, t);
    }
}
