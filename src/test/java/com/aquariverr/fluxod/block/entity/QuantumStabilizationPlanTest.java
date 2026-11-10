package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumStabilizationPlanTest {

    @Test
    void combinesFourAdvancedInterfacesForOneGigafePerTick() {
        List<Long> storedEnergy = List.of(
                250_000_000L, 250_000_000L, 250_000_000L, 250_000_000L);

        assertEquals(storedEnergy, QuantumStabilizationPlan.create(
                storedEnergy, BigInteger.valueOf(1_000_000_000L)).orElseThrow());
    }

    @Test
    void sharesTheCombinedRingCostAcrossAllInterfaces() {
        assertEquals(List.of(
                        5_000_000L,
                        250_000_000L,
                        250_000_000L,
                        250_000_000L,
                        250_000_000L,
                        6_000_000L),
                QuantumStabilizationPlan.createForRings(
                        List.of(
                                List.of(5_000_000L),
                                List.of(250_000_000L),
                                List.of(250_000_000L, 250_000_000L, 250_000_000L,
                                        250_000_000L)),
                        List.of(1_000_000L, 10_000_000L, 1_000_000_000L)).orElseThrow());
    }

    @Test
    void requiresAnInterfaceOnEveryActiveRing() {
        assertTrue(QuantumStabilizationPlan.createForRings(
                List.of(List.of(2_000_000_000L), List.of(), List.of(250_000_000L)),
                List.of(1_000_000L, 10_000_000L, 1_000_000_000L)).isEmpty());
        assertTrue(QuantumStabilizationPlan.createForRings(
                List.of(List.of(2_000_000_000L), List.of(250_000_000L), List.of()),
                List.of(1_000_000L, 10_000_000L, 1_000_000_000L)).isEmpty());
    }

    @Test
    void zeroCostStillRequiresAnInterfaceOnEveryRing() {
        assertTrue(QuantumStabilizationPlan.createForRings(
                List.of(List.of(0L), List.of()), List.of(0L, 0L)).isEmpty());
    }

    @Test
    void producesNoPlanWhenCombinedEnergyIsInsufficient() {
        List<Long> storedEnergy = List.of(
                5_000_000L,
                250_000_000L,
                250_000_000L,
                250_000_000L,
                250_000_000L);

        assertTrue(QuantumStabilizationPlan.create(
                storedEnergy, BigInteger.valueOf(1_011_000_000L)).isEmpty());
    }

    @Test
    void energyShortageDoesNotFallBackToALowerRingTier() {
        assertTrue(QuantumStabilizationPlan.createForRings(
                List.of(
                        List.of(5_000_000L),
                        List.of(250_000_000L),
                        List.of(250_000_000L)),
                List.of(1_000_000L, 10_000_000L, 1_000_000_000L)).isEmpty());
    }

    @Test
    void supportsCombinedCostsBeyondLongRange() {
        BigInteger required = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        assertEquals(List.of(Long.MAX_VALUE, 1L), QuantumStabilizationPlan.create(
                List.of(Long.MAX_VALUE, Long.MAX_VALUE), required).orElseThrow());
    }

    @Test
    void zeroCostStillReturnsAPlanForTheInstalledInterface() {
        assertEquals(List.of(0L), QuantumStabilizationPlan.create(
                List.of(0L), BigInteger.ZERO).orElseThrow());
    }
}
