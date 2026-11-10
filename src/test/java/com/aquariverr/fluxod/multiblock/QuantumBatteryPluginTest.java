package com.aquariverr.fluxod.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryPluginTest {

    @Test
    void tierOnePluginFitsEveryRing() {
        QuantumBatteryPlugin plugin = () -> 1;

        assertTrue(plugin.canInstallInRing(1));
        assertTrue(plugin.canInstallInRing(2));
        assertTrue(plugin.canInstallInRing(3));
    }

    @Test
    void higherTierPluginsRequireMatchingOrHigherRings() {
        QuantumBatteryPlugin tierTwo = () -> 2;
        QuantumBatteryPlugin tierThree = () -> 3;

        assertFalse(tierTwo.canInstallInRing(1));
        assertTrue(tierTwo.canInstallInRing(2));
        assertTrue(tierTwo.canInstallInRing(3));
        assertFalse(tierThree.canInstallInRing(1));
        assertFalse(tierThree.canInstallInRing(2));
        assertTrue(tierThree.canInstallInRing(3));
    }

    @Test
    void rejectsOutOfRangePluginAndRingTiers() {
        assertFalse(QuantumBatteryPlugin.canInstall(0, 1));
        assertFalse(QuantumBatteryPlugin.canInstall(4, 4));
        assertFalse(QuantumBatteryPlugin.canInstall(1, 0));
        assertFalse(QuantumBatteryPlugin.canInstall(1, 4));
    }
}
