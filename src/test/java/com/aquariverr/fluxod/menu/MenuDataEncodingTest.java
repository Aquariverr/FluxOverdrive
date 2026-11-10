package com.aquariverr.fluxod.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuDataEncodingTest {

    @Test
    void roundTripsValuesAcrossSignedShortPackets() {
        int[] values = {0, 32_767, 32_768, 65_535, 999_999, 4_000_000, 0xFFFFFF};
        for (int value : values) {
            int wireLow = (short) MenuDataEncoding.lowWord(value);
            int wireHigh = (short) MenuDataEncoding.highWord(value);
            assertEquals(value, MenuDataEncoding.combineWords(wireLow, wireHigh));
        }
    }
}
