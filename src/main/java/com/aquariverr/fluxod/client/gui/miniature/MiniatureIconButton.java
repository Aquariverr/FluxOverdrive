package com.aquariverr.fluxod.client.gui.miniature;

import com.aquariverr.fluxod.client.gui.FluxGuiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class MiniatureIconButton extends Button {
    private String symbol;
    private final BooleanSupplier selected;

    public MiniatureIconButton(int x, int y, int width, int height, String symbol, Component name,
                               OnPress action, BooleanSupplier selected) {
        super(x, y, width, height, name, action, DEFAULT_NARRATION);
        this.symbol = symbol;
        this.selected = selected;
        setTooltip(Tooltip.create(name));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = active && (isHoveredOrFocused() || selected.getAsBoolean());
        graphics.fill(getX(), getY(), getRight(), getBottom(),
                highlighted ? FluxGuiStyle.FALLBACK_ACCENT : FluxGuiStyle.FRAME);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, FluxGuiStyle.PANEL_RAISED);
        graphics.drawCenteredString(Minecraft.getInstance().font, symbol, getX() + width / 2,
                getY() + (height - 8) / 2, active ? FluxGuiStyle.TEXT : FluxGuiStyle.MUTED);
    }

    public void setSymbol(String symbol) { this.symbol = symbol; }
}
