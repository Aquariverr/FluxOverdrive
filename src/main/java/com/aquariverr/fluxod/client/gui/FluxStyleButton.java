package com.aquariverr.fluxod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class FluxStyleButton extends Button {

    private final IntSupplier accentColor;
    private final BooleanSupplier selected;
    private final FluxGuiStyle.ButtonStyle style;

    public FluxStyleButton(int x, int y, int width, int height, Component message, OnPress onPress,
                           IntSupplier accentColor, BooleanSupplier selected,
                           FluxGuiStyle.ButtonStyle style) {
        super(x, y, width, height, message, onPress, messageSupplier -> messageSupplier.get());
        this.accentColor = accentColor;
        this.selected = selected;
        this.style = style;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        FluxButtonSound.play(soundManager);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FluxGuiStyle.renderButton(graphics, Minecraft.getInstance().font,
                getX(), getY(), getWidth(), getHeight(), getMessage(),
                accentColor.getAsInt(), selected.getAsBoolean(), active, isHoveredOrFocused(), style);
    }
}
