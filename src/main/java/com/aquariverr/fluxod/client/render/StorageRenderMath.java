package com.aquariverr.fluxod.client.render;

final class StorageRenderMath {

    static final float HEIGHT = 0.8125F;
    static final float CAP_CLEARANCE = 1F / 512F;

    private StorageRenderMath() {}

    static float calculateHeight(long energy, long capacity) {
        if (energy <= 0 || capacity <= 0) return 0;
        double ratio = Math.min((double) energy / capacity, 1.0D);
        return (float) (HEIGHT * ratio);
    }

    static boolean shouldRenderTop(float height) {
        return height > CAP_CLEARANCE && height < HEIGHT - CAP_CLEARANCE;
    }
}
