package com.aquariverr.fluxod.client.model.ctm;

import java.util.Locale;

final class ConnectedTextureMask {
    static final int TOP = 0x01;
    static final int TOP_RIGHT = 0x02;
    static final int RIGHT = 0x04;
    static final int BOTTOM_RIGHT = 0x08;
    static final int BOTTOM = 0x10;
    static final int BOTTOM_LEFT = 0x20;
    static final int LEFT = 0x40;
    static final int TOP_LEFT = 0x80;

    static int unpack(long packed, int faceIndex) {
        return (int) (packed >>> (faceIndex * 8)) & 0xFF;
    }

    static int normalize(int rawMask) {
        int mask = rawMask & (TOP | RIGHT | BOTTOM | LEFT);
        if (contains(rawMask, TOP | RIGHT | TOP_RIGHT)) {
            mask |= TOP_RIGHT;
        }
        if (contains(rawMask, RIGHT | BOTTOM | BOTTOM_RIGHT)) {
            mask |= BOTTOM_RIGHT;
        }
        if (contains(rawMask, BOTTOM | LEFT | BOTTOM_LEFT)) {
            mask |= BOTTOM_LEFT;
        }
        if (contains(rawMask, LEFT | TOP | TOP_LEFT)) {
            mask |= TOP_LEFT;
        }
        return mask;
    }

    static boolean isCanonical(int mask) {
        return normalize(mask) == mask;
    }

    static String spriteName(int mask) {
        return String.format(Locale.ROOT, "%02x", normalize(mask));
    }

    private static boolean contains(int mask, int bits) {
        return (mask & bits) == bits;
    }

    private ConnectedTextureMask() {}
}
