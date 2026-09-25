package com.herra.stamina.client.gui;

import com.herra.stamina.client.StaminaSounds;
import com.herra.stamina.client.hud.BarStyle;
import com.herra.stamina.config.StaminaClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

/**
 * 体力系统设置界面（{@code /sta} 打开）—— 搜打撤风格战术面板。
 *
 * <p>全部为玩家本地（CLIENT 配置）调整，零权限要求、互不影响其他玩家：</p>
 * <ul>
 *   <li>位置：拖拽小地图直接摆位 / X、Y、缩放滑条（拖拽图实时联动）</li>
 *   <li>样式：4 种体力条皮肤预设（经典/战术/极简/生电）+ 动态大预览</li>
 *   <li>提示音：体力耗尽心跳音效开关 / 音量 / 试听</li>
 *   <li>显示：闪电图标、状态文字、身体图标、自动隐藏开关 + 动画速度</li>
 * </ul>
 *
 * <p>修改即时生效（HUD 同帧可见），关闭界面时自动写回
 * {@code config/herra_stamina-client.toml}。</p>
 *
 * <p>视觉语言：暗色军规面板 + 琥珀强调 + 扫描线 + 角落括号，全部代码绘制，
 * 不依赖贴图，与 MC 像素字体天然契合。</p>
 */
@OnlyIn(Dist.CLIENT)
public class StaminaSettingsScreen extends Screen {

    // ---- 面板几何 ----
    private static final int PANEL_W = 444;
    private static final int PANEL_H = 272;
    private static final int TITLE_H = 22;

    // 左列（位置）
    private static final int LX = 12;          // 列 x（相对面板）
    private static final int LW = 214;         // 列宽
    private static final int MAP_Y = 42;       // 拖拽图 y
    private static final int MAP_H = 92;       // 拖拽图高
    private static final int SLIDER_X_H = 140; // X 滑条行 y
    private static final int SLIDER_Y_H = 156;
    private static final int SLIDER_SCALE_H = 172;
    private static final int RECENTER_H = 190;

    // 右列（样式 / 提示音 / 动画）
    private static final int RX = 236;
    private static final int RW = 196;
    private static final int PREVIEW_Y = 40;
    private static final int STYLE_ROW1_Y = 64;
    private static final int STYLE_ROW2_Y = 86;
    private static final int SOUND_HDR_Y = 112;
    private static final int SOUND_ROW_Y = 124;
    private static final int LISTEN_Y = 146;
    private static final int ANIM_HDR_Y = 170;
    private static final int ANIM_ROW_Y = 182;

    // 底部
    private static final int TOGGLES_Y = 216;
    private static final int ACTIONS_Y = 240;

    // ---- 搜打撤配色 ----
    private static final int COL_OVERLAY = 0xB20A0C10;
    private static final int COL_PANEL = 0xF610141B;
    private static final int COL_TITLEBAR = 0xF61A2029;
    private static final int COL_EDGE = 0xFF2E3742;
    private static final int COL_EDGE_HI = 0xFF46525F;
    private static final int COL_OUTLINE = 0xFF05070A;
    private static final int COL_AMBER = 0xFFE8A33D;
    private static final int COL_AMBER_DIM = 0xFF8A6430;
    private static final int COL_AMBER_BG = 0xF6232530;
    private static final int COL_GREEN = 0xFF7BA05B;
    private static final int COL_TEXT = 0xFFC8CDD4;
    private static final int COL_TEXT_DIM = 0xFF7A8494;
    private static final int COL_TRACK = 0xFF0C0F14;
    private static final int COL_TRACK_EDGE = 0xFF232B36;
    private static final int COL_MAP_BG = 0xFF0C1016;

    private static final Component TITLE_BRAND = Component.literal("STAMINA // SETTINGS");

    // ---- 状态 ----
    private int panelX;
    private int panelY;
    private boolean dirty;
    private boolean draggingMap;
    private double grabDX;
    private double grabDY;

    // ---- 控件 ----
    private TacSlider xSlider;
    private TacSlider ySlider;
    private TacSlider scaleSlider;
    private TacSlider volumeSlider;
    private TacSlider animSlider;

    public StaminaSettingsScreen() {
        super(Component.translatable("herra_stamina.screen.title"));
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    protected void init() {
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = Math.max(2, (this.height - PANEL_H) / 2);

        // ---- 位置区 ----
        xSlider = addRenderableWidget(new TacSlider(
                panelX + LX, panelY + SLIDER_X_H, LW, 16,
                -250, 250, StaminaClientConfig.HUD_OFFSET_X.get(),
                lbl("herra_stamina.screen.label.offset_x"),
                v -> String.format("%+.0f", v),
                v -> {
                    StaminaClientConfig.HUD_OFFSET_X.set((int) Math.round(v));
                    changed();
                }));
        ySlider = addRenderableWidget(new TacSlider(
                panelX + LX, panelY + SLIDER_Y_H, LW, 16,
                10, 300, StaminaClientConfig.HUD_OFFSET_Y.get(),
                lbl("herra_stamina.screen.label.offset_y"),
                v -> String.format("%.0f", v),
                v -> {
                    StaminaClientConfig.HUD_OFFSET_Y.set((int) Math.round(v));
                    changed();
                }));
        scaleSlider = addRenderableWidget(new TacSlider(
                panelX + LX, panelY + SLIDER_SCALE_H, LW, 16,
                0.5, 2.0, StaminaClientConfig.HUD_SCALE.get(),
                lbl("herra_stamina.screen.label.scale"),
                v -> String.format("%.0f%%", v * 100.0),
                v -> {
                    StaminaClientConfig.HUD_SCALE.set(Math.round(v * 20.0) / 20.0);
                    changed();
                }));
        addRenderableWidget(new TacButton(
                panelX + LX, panelY + RECENTER_H, 104, 16,
                lbl("herra_stamina.screen.recenter"),
                b -> resetPosition()));

        // ---- 样式区（2x2） ----
        BarStyle[] styles = BarStyle.values();
        for (int i = 0; i < styles.length; i++) {
            BarStyle style = styles[i];
            int bx = panelX + RX + (i % 2) * 101;
            int by = panelY + (i / 2 == 0 ? STYLE_ROW1_Y : STYLE_ROW2_Y);
            addRenderableWidget(new StyleButton(bx, by, 95, 18, style));
        }

        // ---- 提示音区 ----
        addRenderableWidget(new TacToggle(
                panelX + RX, panelY + SOUND_ROW_Y, 48, 16,
                lbl("herra_stamina.screen.sound.toggle"),
                StaminaClientConfig.SOUND_ENABLED::get,
                v -> {
                    StaminaClientConfig.SOUND_ENABLED.set(v);
                    changed();
                }));
        volumeSlider = addRenderableWidget(new TacSlider(
                panelX + RX + 52, panelY + SOUND_ROW_Y, 144, 16,
                0, 100, StaminaClientConfig.SOUND_VOLUME.get(),
                lbl("herra_stamina.screen.label.volume"),
                v -> String.format("%.0f%%", v),
                v -> {
                    StaminaClientConfig.SOUND_VOLUME.set((int) Math.round(v));
                    changed();
                }));
        addRenderableWidget(new TacButton(
                panelX + RX, panelY + LISTEN_Y, 96, 16,
                lbl("herra_stamina.screen.listen"),
                b -> StaminaSounds.playExhausted(
                        Mth.clamp(StaminaClientConfig.SOUND_VOLUME.get() / 100.0F, 0.0F, 1.0F) * 1.25F)));

        // ---- 动画区 ----
        animSlider = addRenderableWidget(new TacSlider(
                panelX + RX, panelY + ANIM_ROW_Y, RW, 16,
                0.25, 3.0, StaminaClientConfig.ANIMATION_SPEED.get(),
                lbl("herra_stamina.screen.label.anim"),
                v -> String.format("x%.1f", v),
                v -> {
                    StaminaClientConfig.ANIMATION_SPEED.set(Math.round(v * 10.0) / 10.0);
                    changed();
                }));

        // ---- 底部显示开关 ----
        int tx = LX;
        addRenderableWidget(new TacToggle(panelX + tx, panelY + TOGGLES_Y, 104, 16,
                lbl("herra_stamina.screen.toggle.icon"),
                StaminaClientConfig.SHOW_ICON::get,
                v -> {
                    StaminaClientConfig.SHOW_ICON.set(v);
                    changed();
                }));
        tx += 110;
        addRenderableWidget(new TacToggle(panelX + tx, panelY + TOGGLES_Y, 104, 16,
                lbl("herra_stamina.screen.toggle.text"),
                StaminaClientConfig.SHOW_STATUS_TEXT::get,
                v -> {
                    StaminaClientConfig.SHOW_STATUS_TEXT.set(v);
                    changed();
                }));
        tx += 110;
        addRenderableWidget(new TacToggle(panelX + tx, panelY + TOGGLES_Y, 104, 16,
                lbl("herra_stamina.screen.toggle.body"),
                StaminaClientConfig.SHOW_BODY_STATUS::get,
                v -> {
                    StaminaClientConfig.SHOW_BODY_STATUS.set(v);
                    changed();
                }));
        tx += 110;
        addRenderableWidget(new TacToggle(panelX + tx, panelY + TOGGLES_Y, 104, 16,
                lbl("herra_stamina.screen.toggle.hide"),
                StaminaClientConfig.AUTO_HIDE::get,
                v -> {
                    StaminaClientConfig.AUTO_HIDE.set(v);
                    changed();
                }));

        // ---- 底部按钮 ----
        addRenderableWidget(new TacButton(
                panelX + LX, panelY + ACTIONS_Y, 96, 18,
                lbl("herra_stamina.screen.defaults"),
                b -> resetDefaults()));
        addRenderableWidget(new TacButton(
                panelX + PANEL_W - 132, panelY + ACTIONS_Y, 120, 18,
                lbl("herra_stamina.screen.save"),
                b -> onClose(), true));
    }

    private static Component lbl(String key) {
        return Component.translatable(key);
    }

    private void changed() {
        this.dirty = true;
    }

    @Override
    public void onClose() {
        if (dirty) {
            StaminaClientConfig.SPEC.save();
            dirty = false;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, COL_OVERLAY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        drawPanel(g, mouseX, mouseY);
        // 控件（滑条/按钮）画在面板装饰之上
        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = panelX;
        int y = panelY;

        // 投影 + 面板底 + 边框
        g.fill(x - 3, y - 3, x + PANEL_W + 3, y + PANEL_H + 3, 0x88000000);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, COL_PANEL);
        g.fill(x, y, x + PANEL_W, y + 1, COL_OUTLINE);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, COL_OUTLINE);
        g.fill(x, y, x + 1, y + PANEL_H, COL_OUTLINE);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, COL_OUTLINE);
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + 2, COL_EDGE);
        g.fill(x + 1, y + PANEL_H - 2, x + PANEL_W - 1, y + PANEL_H - 1, COL_EDGE);
        g.fill(x + 1, y + 1, x + 2, y + PANEL_H - 1, COL_EDGE);
        g.fill(x + PANEL_W - 2, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, COL_EDGE);

        // 标题条
        g.fill(x + 2, y + 2, x + PANEL_W - 2, y + TITLE_H, COL_TITLEBAR);
        g.fill(x + 2, y + TITLE_H, x + PANEL_W - 2, y + TITLE_H + 1, COL_AMBER_DIM);
        g.drawString(this.font, TITLE_BRAND, x + 10, y + 7, COL_AMBER, true);
        int brandW = this.font.width(TITLE_BRAND);
        g.drawString(this.font, this.title, x + 10 + brandW + 8, y + 7, COL_TEXT, true);
        Component hint = lbl("herra_stamina.screen.hint_cmd");
        g.drawString(this.font, hint, x + PANEL_W - 10 - this.font.width(hint), y + 7, COL_TEXT_DIM, true);

        // 扫描线（CRT 战术感）
        for (int sy = y + TITLE_H + 1; sy < y + PANEL_H - 2; sy += 3) {
            g.fill(x + 2, sy, x + PANEL_W - 2, sy + 1, 0x08000000);
        }

        // 角落 L 形括号（军规 HUD）
        drawCorner(g, x + 4, y + TITLE_H + 3, 1, 1);
        drawCorner(g, x + PANEL_W - 5, y + TITLE_H + 3, -1, 1);
        drawCorner(g, x + 4, y + PANEL_H - 5, 1, -1);
        drawCorner(g, x + PANEL_W - 5, y + PANEL_H - 5, -1, -1);

        // 节标题
        sectionHeader(g, x + LX, y + 30, "herra_stamina.screen.pos");
        sectionHeader(g, x + RX, y + 30, "herra_stamina.screen.style");
        sectionHeader(g, x + RX, y + SOUND_HDR_Y, "herra_stamina.screen.sound");
        sectionHeader(g, x + RX, y + ANIM_HDR_Y, "herra_stamina.screen.anim");

        // 拖拽定位图 + 大预览条
        drawDragMap(g, x + LX, y + MAP_Y, LW, MAP_H, mouseX, mouseY);
        drawPreviewBar(g, x + RX, y + PREVIEW_Y, RW);

        // 底部说明
        Component auto = lbl("herra_stamina.screen.hint_autosave");
        g.drawString(this.font, auto, x + PANEL_W - 10 - this.font.width(auto),
                y + PANEL_H - 12, COL_TEXT_DIM, false);
    }

    private void drawCorner(GuiGraphics g, int cx, int cy, int dx, int dy) {
        g.fill(cx, cy, cx + 1 + 8 * dx, cy + 1, COL_AMBER);          // 横
        g.fill(cx, cy, cx + 1, cy + 1 + 8 * dy, COL_AMBER);          // 竖
    }

    private void sectionHeader(GuiGraphics g, int x, int y, String key) {
        g.fill(x, y + 2, x + 3, y + 5, COL_AMBER);                   // ■ 指示块
        g.drawString(this.font, lbl(key), x + 7, y, COL_TEXT, true);
    }

    // ------------------------------------------------------------------ 拖拽定位图

    /** 缩放后的条在真实屏幕上的位置（与 StaminaHudElement.drawContents 完全一致）。 */
    private float barScreenX() {
        float scale = scale();
        return this.width / 2.0F - 50.0F * scale + offsetX() * scale;
    }

    private float barScreenY() {
        return this.height - offsetY() * scale();
    }

    private float scale() {
        return Mth.clamp(StaminaClientConfig.HUD_SCALE.get().floatValue(), 0.5F, 2.0F);
    }

    private int offsetX() {
        return StaminaClientConfig.HUD_OFFSET_X.get();
    }

    private int offsetY() {
        return StaminaClientConfig.HUD_OFFSET_Y.get();
    }

    private void drawDragMap(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        // 底 + 边框
        g.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, COL_OUTLINE);
        g.fill(mx, my, mx + mw, my + mh, COL_MAP_BG);
        g.fill(mx, my, mx + mw, my + 1, COL_TRACK_EDGE);
        g.fill(mx, my + mh - 1, mx + mw, my + mh, COL_TRACK_EDGE);
        g.fill(mx, my, mx + 1, my + mh, COL_TRACK_EDGE);
        g.fill(mx + mw - 1, my, mx + mw, my + mh, COL_TRACK_EDGE);

        // 中心参考线（虚线）
        int midX = mx + mw / 2;
        for (int sy = my + 2; sy < my + mh - 2; sy += 4) {
            g.fill(midX, sy, midX + 1, sy + 2, 0x40E8A33D);
        }
        int midY = my + mh / 2;
        for (int sx = mx + 2; sx < mx + mw - 2; sx += 4) {
            g.fill(sx, midY, sx + 2, midY + 1, 0x28E8A33D);
        }

        // 物品栏示意（底部中心，按真实屏幕比例）
        float hbW = Math.max(12.0F, 182.0F / this.width * mw);
        float hbH = Math.max(2.0F, 22.0F / this.height * mh);
        float hbX = mx + mw / 2.0F - hbW / 2.0F;
        float hbY = my + mh - hbH;
        g.fill((int) hbX, (int) hbY, (int) (hbX + hbW), (int) (hbY + hbH), 0xFF232B36);
        g.fill((int) hbX, (int) hbY, (int) (hbX + hbW), (int) (hbY + 1.0F), 0xFF3A4550);

        // 体力条示意（真实位置换算到小地图）
        float scale = scale();
        float barW = Math.max(10.0F, 100.0F * scale / this.width * mw);
        float barH = Math.max(3.0F, 12.0F * scale / this.height * mh);
        float barCx = mx + (barScreenX() + 50.0F * scale) / this.width * mw;   // 中心
        float barBy = my + barScreenY() / this.height * mh;                     // 底边
        int bx = Math.round(barCx - barW / 2.0F);
        int by = Math.round(barBy - barH);
        int bw = Math.max(6, Math.round(barW));
        int bh = Math.max(2, Math.round(barH));

        // 条体：样式配色 + 琥珀边框
        BarStyle style = BarStyle.current();
        int fillColor = fillColorCurve(style, previewRatio());
        g.fill(bx, by, bx + bw, by + bh, 0xFF0D0F13);
        g.fill(bx + 1, by + 1, bx + 1 + Math.max(1, (bw - 2) * 4 / 5), by + bh - 1, withA(fillColor, 0xFF));
        g.fill(bx, by, bx + bw, by + 1, COL_AMBER_DIM);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, COL_AMBER_DIM);
        g.fill(bx, by, bx + 1, by + bh, COL_AMBER_DIM);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, COL_AMBER_DIM);
        if (StaminaClientConfig.SHOW_ICON.get()) {
            g.fill(bx - 4, by + bh / 2 - 1, bx - 1, by + bh / 2 + 2, withA(fillColor, 0xFF)); // 图标占位点
        }

        // 拖拽提示（顶部居中，半透明）
        Component drag = lbl("herra_stamina.screen.hint_drag");
        int dw = this.font.width(drag);
        g.drawString(this.font, drag, mx + mw / 2 - dw / 2, my + 3, 0x9A8A94A3, false);

        // 悬停十字线
        if (mouseX >= mx && mouseX < mx + mw && mouseY >= my && mouseY < my + mh) {
            g.fill(mx + 2, mouseY, mx + mw - 2, mouseY + 1, 0x30C8CDD4);
            g.fill(mouseX, my + 2, mouseX + 1, my + mh - 2, 0x30C8CDD4);
        }
    }

    // ------------------------------------------------------------------ 预览条

    /** 预览动画的体力比例（呼吸波动）。 */
    private static float previewRatio() {
        double t = (System.currentTimeMillis() % 4000L) / 1000.0;
        return 0.55F + 0.15F * (float) Math.sin(t * Math.PI / 2.0);
    }

    private static float previewGhost() {
        double t = (System.currentTimeMillis() % 4000L) / 1000.0;
        return previewRatio() + 0.12F + 0.06F * (float) Math.sin(t * Math.PI + 1.0);
    }

    /** 右列大预览条：当前样式 + 动画填充 + ghost 残影（纯 GuiGraphics 绘制）。 */
    private void drawPreviewBar(GuiGraphics g, int rx, int ry, int rw) {
        float s = 1.4F;
        int w = Math.round(100 * s);
        int h = Math.round(12 * s);
        int bx = rx + (rw - w) / 2;
        int by = ry;
        BarStyle style = BarStyle.current();

        int innerX = Math.round(bx + 2 * s);
        int innerY = Math.round(by + 2 * s);
        int innerW = Math.round(96 * s);
        int innerH = Math.round(8 * s);

        float ratio = previewRatio();
        float ghost = previewGhost();
        int fillW = Math.round(innerW * ratio);
        int ghostW = Math.round(innerW * ghost);

        // 暗底
        g.fill(innerX, innerY, innerX + innerW, innerY + innerH, 0xFF0D0F13);
        // ghost 残影（半透明）
        if (ghostW > fillW) {
            g.fill(innerX + fillW, innerY, innerX + ghostW, innerY + innerH, 0x809AA89C);
        }
        // 主体填充 + 亮端帽
        int fillColor = fillColorCurve(style, ratio);
        if (fillW > 0) {
            g.fill(innerX, innerY, innerX + fillW, innerY + innerH, withA(fillColor, 0xFF));
            int cap = lerpRgb(fillColor, 0xFFFFFF, 0.45F);
            g.fill(innerX + Math.max(0, fillW - 3), innerY, innerX + fillW, innerY + innerH, withA(cap, 0xFF));
            // 填充叠加层（按填充宽度裁剪）
            g.blit(style.shade(), innerX, innerY, 0, 0.0F, fillW, innerH, 96, 8);
        }
        // 边框
        g.blit(style.frame(), bx, by, 0, 0.0F, w, h, 100, 12);
    }

    // ------------------------------------------------------------------ 拖拽输入（手写命中，不走 widget 系统）

    private boolean inDragMap(double mouseX, double mouseY) {
        int mx = panelX + LX;
        int my = panelY + MAP_Y;
        return mouseX >= mx && mouseX < mx + LW && mouseY >= my && mouseY < my + MAP_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inDragMap(mouseX, mouseY)) {
            // 记录抓取偏移（拖动保持手感）
            float barCx = panelX + LX + (barScreenX() + 50.0F * scale()) / this.width * LW;
            float barBy = panelY + MAP_Y + barScreenY() / this.height * MAP_H;
            this.grabDX = mouseX - barCx;
            this.grabDY = mouseY - barBy;
            this.draggingMap = true;
            updateOffsetFromMouse(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingMap && button == 0) {
            updateOffsetFromMouse(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingMap && button == 0) {
            this.draggingMap = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 小地图坐标 -> 真实屏幕坐标 -> offset 配置（与 HUD 定位公式互逆）。 */
    private void updateOffsetFromMouse(double mouseX, double mouseY) {
        float scale = scale();
        // 条中心/底边（小地图内）
        float barCxMap = (float) (mouseX - grabDX);
        float barByMap = (float) (mouseY - grabDY);
        // 反算到真实屏幕
        float centerScreenX = (barCxMap - (panelX + LX)) / LW * this.width;
        float bottomScreenY = (barByMap - (panelY + MAP_Y)) / MAP_H * this.height;
        // HUD 公式逆运算：centerScreenX = width/2 + offsetX*scale
        int newOffsetX = Math.round((centerScreenX - this.width / 2.0F) / scale);
        // HUD 公式逆运算：bottomScreenY = height - offsetY*scale
        int newOffsetY = Math.round((this.height - bottomScreenY) / scale);
        newOffsetX = Mth.clamp(newOffsetX, -250, 250);
        newOffsetY = Mth.clamp(newOffsetY, 10, 300);

        if (newOffsetX != offsetX() || newOffsetY != offsetY()) {
            StaminaClientConfig.HUD_OFFSET_X.set(newOffsetX);
            StaminaClientConfig.HUD_OFFSET_Y.set(newOffsetY);
            xSlider.setExternal(newOffsetX);
            ySlider.setExternal(newOffsetY);
            changed();
        }
    }

    // ------------------------------------------------------------------ 恢复默认

    private void resetPosition() {
        StaminaClientConfig.HUD_OFFSET_X.set(0);
        StaminaClientConfig.HUD_OFFSET_Y.set(84);
        xSlider.setExternal(0);
        ySlider.setExternal(84);
        changed();
    }

    private void resetDefaults() {
        StaminaClientConfig.HUD_OFFSET_X.set(0);
        StaminaClientConfig.HUD_OFFSET_Y.set(84);
        StaminaClientConfig.HUD_SCALE.set(1.0);
        StaminaClientConfig.HUD_STYLE.set(BarStyle.CLASSIC);
        StaminaClientConfig.SOUND_ENABLED.set(true);
        StaminaClientConfig.SOUND_VOLUME.set(60);
        StaminaClientConfig.ANIMATION_SPEED.set(1.0);
        StaminaClientConfig.SHOW_ICON.set(true);
        StaminaClientConfig.SHOW_STATUS_TEXT.set(true);
        StaminaClientConfig.SHOW_BODY_STATUS.set(true);
        StaminaClientConfig.AUTO_HIDE.set(true);
        xSlider.setExternal(0);
        ySlider.setExternal(84);
        scaleSlider.setExternal(1.0);
        volumeSlider.setExternal(60);
        animSlider.setExternal(1.0);
        changed();
    }

    // ------------------------------------------------------------------ 颜色工具（与 HUD 渲染曲线一致）

    private static int fillColorCurve(BarStyle style, float ratio) {
        int rgb;
        if (ratio >= 0.5F) {
            rgb = lerpRgb(style.colorMid(), style.colorFull(), (ratio - 0.5F) * 2.0F);
        } else if (ratio >= 0.2F) {
            rgb = lerpRgb(style.colorLow(), style.colorMid(), (ratio - 0.2F) / 0.3F);
        } else {
            rgb = lerpRgb(style.colorCrit(), style.colorLow(), ratio / 0.2F);
        }
        return rgb;
    }

    private static int lerpRgb(int c1, int c2, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int gg = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (r << 16) | (gg << 8) | b;
    }

    private static int withA(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // ==================================================================
    // 控件
    // ==================================================================

    /** 战术滑条：内嵌 label + 数值 + 自绘轨道（拖动即时应用）。 */
    private class TacSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final Component label;
        private final Function<Double, String> valueFmt;
        private final DoubleConsumer onApply;

        TacSlider(int x, int y, int w, int h,
                  double min, double max, double initial,
                  Component label, Function<Double, String> valueFmt, DoubleConsumer onApply) {
            super(x, y, w, h, Component.empty(), norm(initial, min, max));
            this.min = min;
            this.max = max;
            this.label = label;
            this.valueFmt = valueFmt;
            this.onApply = onApply;
        }

        private static double norm(double v, double min, double max) {
            return Mth.clamp((v - min) / (max - min), 0.0, 1.0);
        }

        private double real() {
            return min + (max - min) * this.value;
        }

        @Override
        protected void applyValue() {
            onApply.accept(real());
        }

        @Override
        protected void updateMessage() {
        }

        /** 程序化刷新（拖拽图改了 offset -> 滑条跟随），不回调防回环。 */
        void setExternal(double realValue) {
            this.value = norm(realValue, min, max);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            // 轨道区域（label 右侧起，value 左侧止）
            int labelW = label == null ? 0 : font.width(label) + 8;
            int valueW = 34;
            int trackX = x + labelW;
            int trackW = Math.max(20, w - labelW - valueW);
            int cy = y + h / 2;

            // 轨道
            g.fill(trackX - 1, cy - 3, trackX + trackW + 1, cy + 3, COL_TRACK_EDGE);
            g.fill(trackX, cy - 2, trackX + trackW, cy + 2, COL_TRACK);
            // 已填充段
            int handleX = (int) (trackX + this.value * (trackW - 8));
            g.fill(trackX, cy - 2, handleX + 4, cy + 2, COL_AMBER_DIM);
            // 手柄（8px，方块 + 高光）
            g.fill(handleX - 1, y + 1, handleX + 9, y + h - 1, COL_OUTLINE);
            g.fill(handleX, y + 2, handleX + 8, y + h - 2, COL_AMBER);
            g.fill(handleX, y + 2, handleX + 8, y + 3, 0xFFF2C58A);

            // label / 数值
            if (label != null) {
                g.drawString(font, label, x + 2, y + h / 2 - 4, COL_TEXT_DIM, false);
            }
            String val = valueFmt.apply(real());
            g.drawString(font, val, x + w - 2 - font.width(val), y + h / 2 - 4, COL_AMBER, false);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setFromTrack(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            setFromTrack(mouseX);
        }

        private void setFromTrack(double mouseX) {
            int labelW = label == null ? 0 : font.width(label) + 8;
            int valueW = 34;
            int trackX = getX() + labelW;
            int trackW = Math.max(20, getWidth() - labelW - valueW);
            double v = Mth.clamp((mouseX - trackX - 4.0) / (trackW - 8.0), 0.0, 1.0);
            if (v != this.value) {
                this.value = v;
                applyValue();
            }
        }
    }

    /** 战术按钮：暗底 + 1px 边框 + hover/选中态，accent 版琥珀底。 */
    private class TacButton extends Button {
        private final boolean accent;

        TacButton(int x, int y, int w, int h, Component label, OnPress onPress, boolean accent) {
            super(x, y, w, h, label, onPress, Button.DEFAULT_NARRATION);
            this.accent = accent;
        }

        TacButton(int x, int y, int w, int h, Component label, OnPress onPress) {
            this(x, y, w, h, label, onPress, false);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hover = isHovered();

            int bg = accent ? (hover ? 0xFFF2B655 : 0xFFE8A33D)
                    : (hover ? 0xF61A2029 : 0xF6141922);
            int border = accent ? 0xFF05070A : (hover ? COL_EDGE_HI : COL_EDGE);
            int textColor = accent ? 0xFF14181F : (hover ? COL_AMBER : COL_TEXT);

            g.fill(x, y, x + w, y + h, border);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
            g.drawCenteredString(font, getMessage(), x + w / 2, y + h / 2 - 4, textColor);
        }
    }

    /** 样式预设按钮：名字 + 四段色带 + 选中琥珀框。 */
    private class StyleButton extends Button {
        private final BarStyle style;

        StyleButton(int x, int y, int w, int h, BarStyle style) {
            super(x, y, w, h, style.displayName(), b -> {
                StaminaClientConfig.HUD_STYLE.set(style);
                changed();
            }, Button.DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean selected = BarStyle.current() == style;
            boolean hover = isHovered();

            int bg = selected ? COL_AMBER_BG : (hover ? 0xF61A2029 : 0xF6141922);
            int border = selected ? COL_AMBER : (hover ? COL_EDGE_HI : COL_EDGE);

            g.fill(x, y, x + w, y + h, border);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
            // 选中指示（左上角小方块）
            if (selected) {
                g.fill(x + 3, y + 3, x + 7, y + 7, COL_AMBER);
            }
            g.drawCenteredString(font, getMessage(), x + w / 2, y + 3,
                    selected ? COL_AMBER : COL_TEXT);
            // 四段配色带
            int stripY = y + h - 6;
            int stripX = x + 8;
            int stripW = w - 16;
            g.fill(stripX, stripY, stripX + stripW * 45 / 100, stripY + 3, withA(style.colorFull(), 0xFF));
            g.fill(stripX + stripW * 45 / 100, stripY, stripX + stripW * 75 / 100, stripY + 3, withA(style.colorMid(), 0xFF));
            g.fill(stripX + stripW * 75 / 100, stripY, stripX + stripW * 90 / 100, stripY + 3, withA(style.colorLow(), 0xFF));
            g.fill(stripX + stripW * 90 / 100, stripY, stripX + stripW, stripY + 3, withA(style.colorCrit(), 0xFF));
        }
    }

    /** LED 指示开关：亮 = 军绿，灭 = 灰。 */
    private class TacToggle extends Button {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        TacToggle(int x, int y, int w, int h, Component label,
                  BooleanSupplier getter, Consumer<Boolean> setter) {
            super(x, y, w, h, label, b -> {
            }, Button.DEFAULT_NARRATION);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void onPress() {
            setter.accept(!getter.getAsBoolean());
            changed();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean on = getter.getAsBoolean();
            boolean hover = isHovered();

            g.fill(x, y, x + w, y + h, hover ? COL_EDGE_HI : COL_EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF6141922);
            // LED
            int led = on ? COL_GREEN : 0xFF3A424E;
            g.fill(x + 4, y + h / 2 - 2, x + 9, y + h / 2 + 3, COL_OUTLINE);
            g.fill(x + 5, y + h / 2 - 1, x + 8, y + h / 2 + 2, led);
            if (on) {
                g.fill(x + 5, y + h / 2 - 1, x + 8, y + h / 2, 0xFFB8D49A); // LED 高光
            }
            // label + 状态字
            g.drawString(font, getMessage(), x + 13, y + h / 2 - 4, on ? COL_TEXT : COL_TEXT_DIM, false);
            Component stateC = on ? lbl("herra_stamina.screen.on") : lbl("herra_stamina.screen.off");
            int sw = font.width(stateC);
            g.drawString(font, stateC, x + w - sw - 4, y + h / 2 - 4, on ? COL_GREEN : COL_TEXT_DIM, false);
        }
    }
}
