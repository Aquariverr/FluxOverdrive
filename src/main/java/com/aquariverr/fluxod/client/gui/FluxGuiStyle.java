package com.aquariverr.fluxod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public final class FluxGuiStyle {

    public static final int FALLBACK_ACCENT = 0xFF43D9FF;
    public static final int PANEL = 0xF00B0D11;
    public static final int PANEL_INNER = 0xE313151B;
    public static final int PANEL_RAISED = 0xE51B1D24;
    public static final int FRAME = 0xFF3B3D43;
    public static final int TEXT = 0xFFE5E5E7;
    public static final int MUTED = 0xFF96979B;
    public static final int GOLD = 0xFFFFD36A;

    public static int accent(int rgb) {
        int color = rgb & 0xFFFFFF;
        return color == 0 ? FALLBACK_ACCENT : 0xFF000000 | color;
    }

    public static void renderFrame(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x - 3, y - 3, right + 3, bottom + 3, 0x55000000);
        graphics.fill(x, y, right, bottom, FRAME);
        graphics.fillGradient(x + 2, y + 2, right - 2, bottom - 2,
                0xF0191B20, PANEL);
        graphics.fill(x + 4, y + 4, right - 4, y + 30, 0xF00D0F14);
        graphics.hLine(x + 3, right - 4, y + 1, withAlpha(accent, 210));
        graphics.hLine(x + 8, right - 9, y + 29, withAlpha(accent, 85));
        graphics.vLine(x + 1, y + 3, y + 15, withAlpha(accent, 150));
        graphics.vLine(right - 2, y + 3, y + 15, withAlpha(accent, 150));
        graphics.hLine(x + 3, x + 11, y + 4, withAlpha(TEXT, 100));
        graphics.hLine(right - 11, right - 4, y + 4, withAlpha(TEXT, 100));
        graphics.hLine(x + 3, x + 11, bottom - 2, withAlpha(TEXT, 70));
        graphics.hLine(right - 11, right - 4, bottom - 2, withAlpha(TEXT, 70));
    }

    public static void renderPanel(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        graphics.fill(x + 1, y + 1, x + width + 1, y + height + 1, 0x45000000);
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1,
                PANEL_RAISED, PANEL_INNER);
        graphics.hLine(x + 2, x + width - 3, y + 1, withAlpha(accent, 100));
        graphics.hLine(x + 2, x + width - 3, y + height - 2, withAlpha(FRAME, 180));
        graphics.vLine(x + 1, y + 3, y + height - 4, withAlpha(FRAME, 180));
        graphics.vLine(x + width - 2, y + 3, y + height - 4, withAlpha(FRAME, 180));
    }

    public static void renderButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                    Component message, int accent, boolean selected, boolean active,
                                    boolean highlighted, ButtonStyle style) {
        int borderColor;
        int fillColor;
        int textColor;
        if (!active && !selected) {
            borderColor = withAlpha(FRAME, 170);
            fillColor = 0xFF111318;
            textColor = withAlpha(MUTED, 150);
        } else if (selected) {
            borderColor = accent;
            fillColor = blendColor(PANEL_RAISED, accent, 0.20);
            textColor = accent;
        } else if (highlighted) {
            borderColor = accent;
            fillColor = blendColor(PANEL_RAISED, accent, 0.10);
            textColor = TEXT;
        } else {
            borderColor = FRAME;
            fillColor = PANEL_RAISED;
            textColor = TEXT;
        }

        graphics.fill(x, y, x + width, y + height, borderColor);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor);
        if (style == ButtonStyle.TAB && selected) {
            graphics.fill(x + 4, y + height - 3, x + width - 4, y + height - 1, accent);
        } else if (style != ButtonStyle.TAB && (selected || highlighted)) {
            graphics.fill(x + 1, y + 3, x + 3, y + height - 3, accent);
        }
        AbstractWidget.renderScrollingString(graphics, font, message,
                x + width / 2, x + 6, y, x + width - 6, y + height, textColor);
    }

    public static void renderInput(GuiGraphics graphics, int x, int y, int width, int height,
                                   int accent, boolean active, boolean focused, boolean hovered) {
        int border = !active ? withAlpha(FRAME, 170) : focused || hovered ? accent : FRAME;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                active ? 0xFF111318 : 0xFF0D0F13);
        graphics.hLine(x + 2, x + width - 3, y + 1, withAlpha(accent, focused ? 115 : 45));
        if (focused) graphics.fill(x + 1, y + 3, x + 3, y + height - 3, accent);
    }

    public static int blendColor(int from, int to, double amount) {
        double t = Math.clamp(amount, 0.0, 1.0);
        int a = (int) Math.round(((from >>> 24) & 0xFF) * (1.0 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) Math.round(((from >>> 16) & 0xFF) * (1.0 - t) + ((to >>> 16) & 0xFF) * t);
        int g = (int) Math.round(((from >>> 8) & 0xFF) * (1.0 - t) + ((to >>> 8) & 0xFF) * t);
        int b = (int) Math.round((from & 0xFF) * (1.0 - t) + (to & 0xFF) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int withAlpha(int color, int alpha) {
        return Math.clamp(alpha, 0, 255) << 24 | color & 0xFFFFFF;
    }

    public enum ButtonStyle {
        TAB,
        TIER,
        ACTION
    }

    private FluxGuiStyle() {}
}
