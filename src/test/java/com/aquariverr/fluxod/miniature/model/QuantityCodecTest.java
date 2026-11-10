package com.aquariverr.fluxod.miniature.model;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityCodecTest {

    @Test
    void encodesSmallUnsignedValuesInTheShortestBigEndianForm() {
        assertArrayEquals(new byte[]{0}, QuantityCodec.encode(BigInteger.ZERO));
        assertArrayEquals(new byte[]{1}, QuantityCodec.encode(BigInteger.ONE));
        assertArrayEquals(new byte[]{127}, QuantityCodec.encode(BigInteger.valueOf(127)));
        assertArrayEquals(new byte[]{(byte) 128}, QuantityCodec.encode(BigInteger.valueOf(128)));
        assertArrayEquals(new byte[]{(byte) 255}, QuantityCodec.encode(BigInteger.valueOf(255)));
        assertArrayEquals(new byte[]{1, 0}, QuantityCodec.encode(BigInteger.valueOf(256)));
    }

    @Test
    void decodesTheHighBitAsUnsigned() {
        assertEquals(BigInteger.valueOf(128), QuantityCodec.decode(new byte[]{(byte) 128}));
        assertEquals(BigInteger.valueOf(255), QuantityCodec.decode(new byte[]{(byte) 255}));
        assertEquals(BigInteger.valueOf(256), QuantityCodec.decode(new byte[]{1, 0}));

        byte[] highBit = new byte[32];
        highBit[0] = (byte) 128;
        BigInteger expected = BigInteger.ONE.shiftLeft(255);
        assertEquals(expected, QuantityCodec.decode(highBit));
        assertArrayEquals(highBit, QuantityCodec.encode(expected));
    }

    @Test
    void preservesTheFull256BitUnsignedRange() {
        byte[] maximumBytes = new byte[32];
        Arrays.fill(maximumBytes, (byte) 255);
        BigInteger maximum = new BigInteger(
                "115792089237316195423570985008687907853269984665640564039457584007913129639935");

        assertEquals(maximum, FactoryLimits.MAX_ENCODED_QUANTITY);
        assertEquals(maximum, QuantityCodec.decode(maximumBytes));
        assertArrayEquals(maximumBytes, QuantityCodec.encode(maximum));
        assertQuantityFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> QuantityCodec.encode(maximum.add(BigInteger.ONE)));
    }

    @Test
    void roundTripsEachByteBoundaryWithoutSignPadding() {
        for (int length = 1; length <= 32; length++) {
            byte[] magnitude = new byte[length];
            Arrays.fill(magnitude, (byte) 255);
            BigInteger quantity = QuantityCodec.decode(magnitude);

            assertArrayEquals(magnitude, QuantityCodec.encode(quantity));
        }
    }

    @Test
    void acceptsLeadingZerosWithinTheEncodedLengthLimit() {
        assertEquals(BigInteger.ONE, QuantityCodec.decode(new byte[]{0, 0, 1}));
        assertEquals(BigInteger.ZERO, QuantityCodec.decode(new byte[32]));
        assertArrayEquals(new byte[]{(byte) 128},
                QuantityCodec.encode(QuantityCodec.decode(new byte[]{0, 0, (byte) 128})));
    }

    @Test
    void rejectsOversizedEncodingsEvenWhenLeadingZerosCouldBeRemoved() {
        byte[] paddedOne = new byte[33];
        paddedOne[32] = 1;

        assertQuantityFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> QuantityCodec.decode(paddedOne));
        assertQuantityFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> QuantityCodec.decode(new byte[33]));
    }

    @Test
    void rejectsMissingNegativeAndEmptyQuantities() {
        assertQuantityFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> QuantityCodec.encode(null));
        assertQuantityFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> QuantityCodec.encode(BigInteger.valueOf(-1)));
        assertQuantityFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> QuantityCodec.decode(null));
        assertQuantityFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> QuantityCodec.decode(new byte[0]));
    }

    @Test
    void validatesPositiveQuantitiesWithoutChangingAcceptedValues() {
        BigInteger positive = BigInteger.valueOf(17);
        assertSame(positive, FactoryLimits.requireQuantity(positive));
        assertSame(positive, FactoryLimits.requirePositiveQuantity(positive));
        assertSame(FactoryLimits.MAX_ENCODED_QUANTITY,
                FactoryLimits.requirePositiveQuantity(FactoryLimits.MAX_ENCODED_QUANTITY));
        assertSame(BigInteger.ZERO, FactoryLimits.requireQuantity(BigInteger.ZERO));
        assertQuantityFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> FactoryLimits.requirePositiveQuantity(BigInteger.ZERO));
        assertQuantityFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> FactoryLimits.requirePositiveQuantity(BigInteger.valueOf(-1)));
    }

    @Test
    void keepsInventoryCapacitySeparateFromTheEncodingLimit() {
        BigInteger inventoryMaximum = new BigInteger("1000000000000000000000000000000");
        BigInteger aboveInventoryMaximum = inventoryMaximum.add(BigInteger.ONE);
        assertEquals(inventoryMaximum, FactoryLimits.MAX_INVENTORY_AMOUNT);
        assertSame(BigInteger.ZERO, FactoryLimits.requireInventoryQuantity(BigInteger.ZERO));
        assertSame(inventoryMaximum, FactoryLimits.requireInventoryQuantity(inventoryMaximum));
        assertSame(aboveInventoryMaximum, FactoryLimits.requireQuantity(aboveInventoryMaximum));
        assertEquals(aboveInventoryMaximum,
                QuantityCodec.decode(QuantityCodec.encode(aboveInventoryMaximum)));
        assertQuantityFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> FactoryLimits.requireInventoryQuantity(aboveInventoryMaximum));
        assertQuantityFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> FactoryLimits.requireInventoryQuantity(BigInteger.valueOf(-1)));
    }

    @Test
    void doesNotMutateOrRetainEncodedArrays() {
        byte[] source = new byte[]{0, (byte) 128};
        byte[] original = source.clone();
        BigInteger decoded = QuantityCodec.decode(source);
        assertArrayEquals(original, source);
        source[1] = 1;
        assertEquals(BigInteger.valueOf(128), decoded);

        byte[] encoded = QuantityCodec.encode(decoded);
        encoded[0] = 0;
        assertEquals(BigInteger.valueOf(128), decoded);
        assertArrayEquals(new byte[]{(byte) 128}, QuantityCodec.encode(decoded));

        byte[] oversized = new byte[33];
        oversized[0] = 1;
        byte[] oversizedOriginal = oversized.clone();
        assertQuantityFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> QuantityCodec.decode(oversized));
        assertArrayEquals(oversizedOriginal, oversized);
    }

    private static void assertQuantityFailure(FactoryDiagnostic.Code expected, Runnable action) {
        ModelValidationException exception = assertThrows(ModelValidationException.class, action::run);
        assertEquals(expected, exception.diagnostic().code());
    }
}
