package com.aquariverr.fluxod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaturatedLongMathTest {

    @Test
    void addsValuesBelowTheLimit() {
        assertEquals(42, SaturatedLongMath.addNonNegative(19, 23));
    }

    @Test
    void saturatesOverflowAndInvalidNegativeValues() {
        assertEquals(Long.MAX_VALUE, SaturatedLongMath.addNonNegative(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, SaturatedLongMath.addNonNegative(10, -1));
    }

    @Test
    void convertsNegativeChangesWithoutOverflow() {
        assertEquals(12, SaturatedLongMath.magnitudeOfNegative(-12));
        assertEquals(0, SaturatedLongMath.magnitudeOfNegative(12));
        assertEquals(Long.MAX_VALUE, SaturatedLongMath.magnitudeOfNegative(Long.MIN_VALUE));
    }
}
