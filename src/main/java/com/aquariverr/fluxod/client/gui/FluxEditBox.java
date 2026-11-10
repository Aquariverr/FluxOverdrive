package com.aquariverr.fluxod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

public final class FluxEditBox extends EditBox {

    private final IntSupplier accentColor;

    public FluxEditBox(Font font, int x, int y, int width, int height, Component message,
                       IntSupplier accentColor) {
        super(font, x, y, width, height, message);
        this.accentColor = accentColor;
        setBordered(false);
        setTextColor(FluxGuiStyle.TEXT);
        setTextColorUneditable(FluxGuiStyle.MUTED);
        setTextShadow(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int originalX = getX();
        int originalY = getY();
        int originalWidth = width;
        int originalHeight = height;
        FluxGuiStyle.renderInput(graphics, originalX, originalY, originalWidth, originalHeight,
                accentColor.getAsInt(), active, isFocused(), isHoveredOrFocused());

        setX(originalX + 4);
        setY(originalY + (originalHeight - 8) / 2);
        width = originalWidth - 8;
        height = 8;
        try {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        } finally {
            setX(originalX);
            setY(originalY);
            width = originalWidth;
            height = originalHeight;
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - 4.0, mouseY);
    }
}
