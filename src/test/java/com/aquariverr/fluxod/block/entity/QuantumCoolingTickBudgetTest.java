package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantumCoolingTickBudgetTest {

    @Test
    void sharesOneLimitAcrossAllCallsInTheSameGameTick() {
        QuantumCoolingTickBudget budget = new QuantumCoolingTickBudget();

        assertEquals(80_000L, budget.limit(42L, 80_000L, 100_000L));
        budget.record(42L, 60_000L);
        assertEquals(20_000L, budget.limit(42L, 80_000L, 100_000L));
        budget.record(42L, 20_000L);
        assertEquals(0L, budget.limit(42L, 80_000L, 1L));
    }

    @Test
    void resetsAtTheStartOfTheNextGameTick() {
        QuantumCoolingTickBudget budget = new QuantumCoolingTickBudget();
        budget.record(42L, 80_000L);

        assertEquals(80_000L, budget.limit(43L, 80_000L, 100_000L));
    }
}
