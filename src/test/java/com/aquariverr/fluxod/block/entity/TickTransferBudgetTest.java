package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickTransferBudgetTest {

    @Test
    void sharesOneBudgetAcrossTransfersInTheSameTick() {
        TickTransferBudget budget = new TickTransferBudget();

        assertEquals(100, budget.remaining(20, 100));
        budget.record(20, 40);
        assertEquals(60, budget.remaining(20, 100));
        budget.record(20, 60);
        assertEquals(0, budget.remaining(20, 100));
    }

    @Test
    void resetsOnTheNextTick() {
        TickTransferBudget budget = new TickTransferBudget();

        budget.record(20, 100);

        assertEquals(0, budget.remaining(20, 100));
        assertEquals(100, budget.remaining(21, 100));
    }

    @Test
    void clampsNegativeLimitsAndOverflow() {
        TickTransferBudget budget = new TickTransferBudget();

        assertEquals(0, budget.remaining(20, -1));
        budget.record(20, Long.MAX_VALUE);
        budget.record(20, 1);
        assertEquals(0, budget.remaining(20, Long.MAX_VALUE));
    }
}
