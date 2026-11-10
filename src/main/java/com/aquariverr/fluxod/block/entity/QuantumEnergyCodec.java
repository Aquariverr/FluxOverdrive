package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.util.NonNegativeBigIntegerCodec;

import java.math.BigInteger;

final class QuantumEnergyCodec {

    static final int MAX_ENERGY_BYTES = NonNegativeBigIntegerCodec.DEFAULT_MAX_BYTES;
    static final BigInteger MAX_ENERGY = NonNegativeBigIntegerCodec.maximumValue(MAX_ENERGY_BYTES);

    static byte[] encode(BigInteger energy) {
        return NonNegativeBigIntegerCodec.encode(energy, MAX_ENERGY_BYTES);
    }

    static BigInteger decode(byte[] encoded) {
        return NonNegativeBigIntegerCodec.decode(encoded, MAX_ENERGY_BYTES);
    }

    static BigInteger clamp(BigInteger energy) {
        return NonNegativeBigIntegerCodec.clamp(energy, MAX_ENERGY_BYTES);
    }

    private QuantumEnergyCodec() {}
}
