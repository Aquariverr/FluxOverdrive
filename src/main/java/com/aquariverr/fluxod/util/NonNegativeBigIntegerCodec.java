package com.aquariverr.fluxod.util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

public final class NonNegativeBigIntegerCodec {

    public static final int DEFAULT_MAX_BYTES = 8192;
    private static final int MAX_SUPPORTED_BYTES = 1 << 20;

    public static BigInteger maximumValue(int maxBytes) {
        return BigInteger.ONE.shiftLeft(maximumBits(maxBytes)).subtract(BigInteger.ONE);
    }

    public static BigInteger clamp(BigInteger value, int maxBytes) {
        Objects.requireNonNull(value, "value");
        int maxBits = maximumBits(maxBytes);
        if (value.signum() <= 0) return BigInteger.ZERO;
        return value.bitLength() <= maxBits ? value : maximumValue(maxBytes);
    }

    public static byte[] encode(BigInteger value, int maxBytes) {
        return clamp(value, maxBytes).toByteArray();
    }

    public static BigInteger decode(byte[] encoded, int maxBytes) {
        Objects.requireNonNull(encoded, "encoded");
        requirePositiveLimit(maxBytes);
        if (encoded.length == 0 || encoded[0] < 0) return BigInteger.ZERO;
        if (encoded.length <= maxBytes) return new BigInteger(encoded);

        int firstNonZero = 0;
        while (firstNonZero < encoded.length && encoded[firstNonZero] == 0) firstNonZero++;
        if (firstNonZero == encoded.length) return BigInteger.ZERO;

        int magnitudeBytes = encoded.length - firstNonZero;
        int canonicalLength = magnitudeBytes + (encoded[firstNonZero] < 0 ? 1 : 0);
        if (canonicalLength > maxBytes) return maximumValue(maxBytes);
        return new BigInteger(1, Arrays.copyOfRange(encoded, firstNonZero, encoded.length));
    }

    private static void requirePositiveLimit(int maxBytes) {
        if (maxBytes <= 0 || maxBytes > MAX_SUPPORTED_BYTES) {
            throw new IllegalArgumentException("maxBytes must be between 1 and " + MAX_SUPPORTED_BYTES);
        }
    }

    private static int maximumBits(int maxBytes) {
        requirePositiveLimit(maxBytes);
        return Math.multiplyExact(maxBytes, Byte.SIZE) - 1;
    }

    private NonNegativeBigIntegerCodec() {}
}
