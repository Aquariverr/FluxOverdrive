package com.aquariverr.fluxod.entanglement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntanglementRoutePolicyTest {

    @Test
    void keepsLegacyConstraintAggregationWhenNothingIsSelected() {
        assertEquals(EntanglementRoutePolicy.Mode.CONSTRAINT_GROUP,
                EntanglementRoutePolicy.resolve(false, false));
    }

    @Test
    void selectingAnyConstraintEndpointUsesTheConstraintGroup() {
        assertEquals(EntanglementRoutePolicy.Mode.CONSTRAINT_GROUP,
                EntanglementRoutePolicy.resolve(true, true));
    }

    @Test
    void selectingANonConstraintEndpointUsesOnlyThatPlugin() {
        assertEquals(EntanglementRoutePolicy.Mode.SINGLE_PLUGIN,
                EntanglementRoutePolicy.resolve(true, false));
    }

    @Test
    void automationFollowsThePortDirection() {
        assertEquals(true, EntanglementRoutePolicy.automationEnabled(
                EntanglementResourceDirection.INPUT, true, false));
        assertEquals(false, EntanglementRoutePolicy.automationEnabled(
                EntanglementResourceDirection.INPUT, false, true));
        assertEquals(true, EntanglementRoutePolicy.automationEnabled(
                EntanglementResourceDirection.OUTPUT, false, true));
        assertEquals(false, EntanglementRoutePolicy.automationEnabled(
                EntanglementResourceDirection.OUTPUT, true, false));
    }
}
