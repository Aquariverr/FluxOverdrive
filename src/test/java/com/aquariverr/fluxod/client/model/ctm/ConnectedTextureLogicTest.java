package com.aquariverr.fluxod.client.model.ctm;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectedTextureLogicTest {

    @Test
    void canonicalizesEveryMaskIntoExactlyFortySevenStates() {
        Set<Integer> normalized = new HashSet<>();
        int canonicalCount = 0;

        for (int mask = 0; mask < 256; mask++) {
            int result = ConnectedTextureMask.normalize(mask);
            normalized.add(result);
            assertEquals(result, ConnectedTextureMask.normalize(result));
            assertEquals(mask & 0x55, result & 0x55);
            if (ConnectedTextureMask.isCanonical(mask)) {
                canonicalCount++;
            }
        }

        assertEquals(47, normalized.size());
        assertEquals(47, canonicalCount);
    }

    @Test
    void removesDiagonalConnectionsWithoutBothEdges() {
        assertEquals(ConnectedTextureMask.TOP,
                ConnectedTextureMask.normalize(ConnectedTextureMask.TOP | ConnectedTextureMask.TOP_RIGHT));
        assertEquals(ConnectedTextureMask.TOP | ConnectedTextureMask.RIGHT,
                ConnectedTextureMask.normalize(ConnectedTextureMask.TOP
                        | ConnectedTextureMask.RIGHT
                        | ConnectedTextureMask.BOTTOM_RIGHT));
        assertEquals(ConnectedTextureMask.TOP | ConnectedTextureMask.TOP_RIGHT | ConnectedTextureMask.RIGHT,
                ConnectedTextureMask.normalize(ConnectedTextureMask.TOP
                        | ConnectedTextureMask.TOP_RIGHT
                        | ConnectedTextureMask.RIGHT));
    }

    @Test
    void packsOneUnsignedMaskPerFace() {
        long packed = 0;
        for (int face = 0; face < 6; face++) {
            packed |= (long) (0x80 + face) << (face * 8);
        }

        for (int face = 0; face < 6; face++) {
            assertEquals(0x80 + face, ConnectedTextureMask.unpack(packed, face));
        }
    }

    @Test
    void usesStableHexSpriteNames() {
        assertEquals("00", ConnectedTextureMask.spriteName(0));
        assertEquals("ff", ConnectedTextureMask.spriteName(0xFF));
        assertEquals("05", ConnectedTextureMask.spriteName(0x05));
    }
}
