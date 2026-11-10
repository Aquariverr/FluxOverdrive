package com.aquariverr.fluxod.entanglement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntanglementEndpointContractTest {

    @Test
    void mapsWorldAxesToStableDisplayLocations() {
        assertEquals(QuantumPluginLocation.UP,
                QuantumPluginLocation.fromRelativeOffset(0, 3, 0));
        assertEquals(QuantumPluginLocation.DOWN,
                QuantumPluginLocation.fromRelativeOffset(0, -6, 0));
        assertEquals(QuantumPluginLocation.EAST,
                QuantumPluginLocation.fromRelativeOffset(11, 0, 0));
        assertEquals(QuantumPluginLocation.SOUTH,
                QuantumPluginLocation.fromRelativeOffset(0, 0, 3));
        assertEquals(QuantumPluginLocation.WEST,
                QuantumPluginLocation.fromRelativeOffset(-6, 0, 0));
        assertEquals(QuantumPluginLocation.NORTH,
                QuantumPluginLocation.fromRelativeOffset(0, 0, -11));
    }

    @Test
    void sortsByRingThenUpDownEastSouthWestNorth() {
        List<QuantumPluginDisplayKey> keys = new ArrayList<>(List.of(
                key(2, QuantumPluginLocation.UP, "ring_two"),
                key(1, QuantumPluginLocation.NORTH, "north"),
                key(1, QuantumPluginLocation.WEST, "west"),
                key(1, QuantumPluginLocation.SOUTH, "south"),
                key(1, QuantumPluginLocation.EAST, "east"),
                key(1, QuantumPluginLocation.DOWN, "down"),
                key(1, QuantumPluginLocation.UP, "up")));

        keys.sort(null);

        assertEquals(List.of("up", "down", "east", "south", "west", "north", "ring_two"),
                keys.stream().map(QuantumPluginDisplayKey::localEndpointId).toList());
    }

    @Test
    void keepsInputAndOutputResourceAuthorizationIndependent() {
        int inputs = EntanglementResourceKind.ITEM.mask() | EntanglementResourceKind.CHEMICAL.mask();
        int outputs = EntanglementResourceKind.FLUID.mask();

        assertTrue(supports(inputs, outputs, EntanglementResourceKind.ITEM,
                EntanglementResourceDirection.INPUT));
        assertTrue(supports(inputs, outputs, EntanglementResourceKind.CHEMICAL,
                EntanglementResourceDirection.INPUT));
        assertFalse(supports(inputs, outputs, EntanglementResourceKind.FLUID,
                EntanglementResourceDirection.INPUT));
        assertTrue(supports(inputs, outputs, EntanglementResourceKind.FLUID,
                EntanglementResourceDirection.OUTPUT));
        assertFalse(supports(inputs, outputs, EntanglementResourceKind.ITEM,
                EntanglementResourceDirection.OUTPUT));
        assertFalse(supports(inputs, outputs, EntanglementResourceKind.CHEMICAL,
                EntanglementResourceDirection.OUTPUT));
    }

    @Test
    void resourceKindsUseIndependentSingleBitMasks() {
        int combined = 0;
        for (EntanglementResourceKind kind : EntanglementResourceKind.values()) {
            assertEquals(1, Integer.bitCount(kind.mask()));
            assertEquals(0, combined & kind.mask());
            combined |= kind.mask();
        }
        assertEquals(3, Integer.bitCount(combined));
    }

    private static QuantumPluginDisplayKey key(int ring, QuantumPluginLocation location, String localId) {
        return new QuantumPluginDisplayKey(ring, location, localId);
    }

    private static boolean supports(int inputs, int outputs, EntanglementResourceKind kind,
                                    EntanglementResourceDirection direction) {
        return EntanglementResourcePermissions.supports(inputs, outputs, kind, direction);
    }
}
