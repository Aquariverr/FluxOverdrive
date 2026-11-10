package com.aquariverr.fluxod.client.gui;

public final class QuantumGuiLayout {

    public static final int REFERENCE_WIDTH = 430;
    public static final int REFERENCE_HEIGHT = 236;
    public static final int UI_MARGIN = 8;
    public static final float SCREEN_AXIS_FRACTION = 0.5F;

    public static float scale(int screenWidth, int screenHeight) {
        float targetWidth = Math.max(1, screenWidth - UI_MARGIN) * SCREEN_AXIS_FRACTION;
        float targetHeight = Math.max(1, screenHeight - UI_MARGIN) * SCREEN_AXIS_FRACTION;
        return Math.max(0.05F, Math.min(
                targetWidth / REFERENCE_WIDTH,
                targetHeight / REFERENCE_HEIGHT));
    }

    public static int centeredPosition(int axisSize, int contentSize, float scale) {
        return (int) Math.floor((axisSize / scale - contentSize) * 0.5F);
    }

    private QuantumGuiLayout() {}
}
