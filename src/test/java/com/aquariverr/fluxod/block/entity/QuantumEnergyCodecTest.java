package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantumEnergyCodecTest {

    @Test
    void roundTripsEnergyBeyondLongRange() {
        BigInteger energy = BigInteger.TEN.pow(1000);

        assertEquals(energy, QuantumEnergyCodec.decode(QuantumEnergyCodec.encode(energy)));
    }

    @Test
    void rejectsNegativeAndEmptyOversizedValues() {
        assertEquals(BigInteger.ZERO, QuantumEnergyCodec.decode(BigInteger.valueOf(-1).toByteArray()));
        assertEquals(BigInteger.ZERO, QuantumEnergyCodec.decode(new byte[QuantumEnergyCodec.MAX_ENERGY_BYTES + 1]));
    }

    @Test
    void migratesOversizedPositiveValuesToThePersistenceLimit() {
        byte[] oversized = new byte[QuantumEnergyCodec.MAX_ENERGY_BYTES + 1];
        oversized[0] = 1;

        assertEquals(QuantumEnergyCodec.MAX_ENERGY, QuantumEnergyCodec.decode(oversized));
    }

    @Test
    void clampsEncodedValuesToThePersistenceLimit() {
        BigInteger oversized = QuantumEnergyCodec.MAX_ENERGY.add(BigInteger.ONE);

        byte[] encoded = QuantumEnergyCodec.encode(oversized);

        assertEquals(QuantumEnergyCodec.MAX_ENERGY, QuantumEnergyCodec.decode(encoded));
        assertEquals(QuantumEnergyCodec.MAX_ENERGY_BYTES, encoded.length);
    }

    @Test
    void acceptsTheLargestPersistableValue() {
        assertEquals(QuantumEnergyCodec.MAX_ENERGY,
                QuantumEnergyCodec.decode(QuantumEnergyCodec.encode(QuantumEnergyCodec.MAX_ENERGY)));
    }
}
