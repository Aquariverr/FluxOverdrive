package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumEnergyLedgerTest {

    @Test
    void capsInsertionsAtFiniteCapacity() {
        QuantumEnergyLedger ledger = new QuantumEnergyLedger();
        ledger.setCapacity(BigInteger.valueOf(100));

        assertEquals(BigInteger.valueOf(100), ledger.insert(BigInteger.valueOf(150)));
        assertEquals(BigInteger.valueOf(100), ledger.stored());
        assertEquals(0, ledger.spaceAfterPendingAsLong(100, 0, 0));
    }

    @Test
    void supportsValuesBeyondLongRangeWithoutCapacity() {
        QuantumEnergyLedger ledger = new QuantumEnergyLedger();
        ledger.setCapacity(null);
        BigInteger energy = BigInteger.TEN.pow(100);

        assertEquals(energy, ledger.insert(energy));
        assertEquals(energy, ledger.stored());
        assertEquals(6_000_000_000_000_000_000L,
                ledger.availableAsLong(6_000_000_000_000_000_000L));
        assertTrue(ledger.capacity().isEmpty());
    }

    @Test
    void preservesOverCapacityEnergyAfterDowngrade() {
        QuantumEnergyLedger ledger = new QuantumEnergyLedger();
        ledger.setCapacity(null);
        ledger.setStored(BigInteger.valueOf(200));

        ledger.setCapacity(BigInteger.valueOf(100));

        assertEquals(BigInteger.valueOf(200), ledger.stored());
        assertEquals(0, ledger.spaceAfterPendingAsLong(100, 0, 0));
        assertEquals(BigInteger.valueOf(150), ledger.extract(BigInteger.valueOf(150)));
        assertEquals(50, ledger.spaceAfterPendingAsLong(100, 0, 0));
    }

    @Test
    void pendingRemovalCreatesReceiveSpaceInSameCycle() {
        QuantumEnergyLedger ledger = new QuantumEnergyLedger();
        ledger.setCapacity(BigInteger.valueOf(100));
        ledger.setStored(BigInteger.valueOf(100));

        assertEquals(40, ledger.spaceAfterPendingAsLong(100, 0, 40));
        assertEquals(20, ledger.spaceAfterPendingAsLong(100, 20, 40));
    }

    @Test
    void unlimitedLedgerStillHonorsThePersistenceSafetyLimit() {
        QuantumEnergyLedger ledger = new QuantumEnergyLedger();
        ledger.setCapacity(null);
        ledger.setStored(QuantumEnergyCodec.MAX_ENERGY.subtract(BigInteger.ONE));

        assertEquals(BigInteger.ONE, ledger.insert(BigInteger.TEN));
        assertEquals(QuantumEnergyCodec.MAX_ENERGY, ledger.stored());
        assertEquals(0, ledger.spaceAfterPendingAsLong(100, 0, 0));
        assertEquals(1, ledger.spaceAfterPendingAsLong(100, 0, 1));
    }

    @Test
    void clampsLoadedEnergyAndFiniteCapacityToThePersistenceLimit() {
        QuantumEnergyLedger ledger = new QuantumEnergyLedger();
        BigInteger oversized = QuantumEnergyCodec.MAX_ENERGY.add(BigInteger.ONE);

        ledger.setStored(oversized);
        ledger.setCapacity(oversized);

        assertEquals(QuantumEnergyCodec.MAX_ENERGY, ledger.stored());
        assertEquals(QuantumEnergyCodec.MAX_ENERGY, ledger.capacity().orElseThrow());
    }
}
