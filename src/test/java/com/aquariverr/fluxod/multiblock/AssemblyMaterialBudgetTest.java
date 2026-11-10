package com.aquariverr.fluxod.multiblock;

import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssemblyMaterialBudgetTest {

    @Test
    void reservesEachMaterialOnlyUpToItsAvailableCount() {
        Object casing = new Object();
        Object glass = new Object();
        Map<Object, Integer> available = new IdentityHashMap<>();
        available.put(casing, 2);
        available.put(glass, 1);
        AssemblyMaterialBudget<Object> budget = new AssemblyMaterialBudget<>(available);

        assertTrue(budget.reserve(casing));
        assertTrue(budget.reserve(glass));
        assertTrue(budget.reserve(casing));
        assertFalse(budget.reserve(casing));
        assertFalse(budget.reserve(glass));
        assertFalse(budget.reserve(new Object()));
    }
}
