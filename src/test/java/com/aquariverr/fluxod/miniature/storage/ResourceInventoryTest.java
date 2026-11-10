package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceInventoryTest {

    private static final ResourceKey IRON = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot");
    private static final ResourceKey WATER = new ResourceKey(ResourceKind.FLUID, "minecraft:water");
    private static final ResourceKey OXYGEN = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen");

    @Test
    void emptyInventoryHasNoEntriesAndReportsZeroForAbsentResources() {
        ResourceInventory inventory = ResourceInventory.empty();

        assertTrue(inventory.amounts().isEmpty());
        assertEquals(BigInteger.ZERO, inventory.amount(IRON));
        assertEquals(ResourceInventory.empty(), inventory);
    }

    @Test
    void constructorAndAccessorPreventChangesToTheSnapshot() {
        Map<ResourceKey, BigInteger> source = new LinkedHashMap<>();
        source.put(IRON, BigInteger.valueOf(12));
        ResourceInventory inventory = new ResourceInventory(source);
        source.put(IRON, BigInteger.valueOf(99));
        source.put(WATER, BigInteger.ONE);

        assertEquals(Map.of(IRON, BigInteger.valueOf(12)), inventory.amounts());
        assertThrows(UnsupportedOperationException.class, () -> inventory.amounts().put(IRON, BigInteger.ONE));
        assertThrows(UnsupportedOperationException.class, () -> inventory.amounts().entrySet().iterator().next()
                .setValue(BigInteger.ONE));
        assertThrows(UnsupportedOperationException.class, () -> inventory.amounts().clear());
    }

    @Test
    void insertsAllKindsAndMergesOnlyTheSameExactResource() {
        ResourceInventory empty = ResourceInventory.empty();
        ResourceInventory first = empty.insert(new ResourceAmount(IRON, BigInteger.valueOf(2)));
        ResourceInventory inventory = first.insert(new ResourceAmount(IRON, BigInteger.valueOf(3)))
                .insert(new ResourceAmount(WATER, BigInteger.valueOf(1000)))
                .insert(new ResourceAmount(OXYGEN, BigInteger.valueOf(2000)));

        assertTrue(empty.amounts().isEmpty());
        assertEquals(BigInteger.valueOf(2), first.amount(IRON));
        assertEquals(Map.of(IRON, BigInteger.valueOf(5), WATER, BigInteger.valueOf(1000),
                OXYGEN, BigInteger.valueOf(2000)), inventory.amounts());
    }

    @Test
    void sameRegistryIdWithDifferentComponentsUsesIndependentEntries() {
        for (ResourceKind kind : new ResourceKind[]{ResourceKind.ITEM, ResourceKind.FLUID}) {
            CompoundTag firstComponents = new CompoundTag();
            firstComponents.putInt("test:temperature", 300);
            CompoundTag secondComponents = new CompoundTag();
            secondComponents.putInt("test:temperature", 301);
            ResourceKey first = new ResourceKey(kind, "test:resource", firstComponents);
            ResourceKey second = new ResourceKey(kind, "test:resource", secondComponents);
            ResourceInventory inventory = ResourceInventory.empty()
                    .insert(new ResourceAmount(first, BigInteger.valueOf(2)))
                    .insert(new ResourceAmount(second, BigInteger.valueOf(3)));

            assertEquals(2, inventory.amounts().size());
            assertEquals(BigInteger.valueOf(2), inventory.amount(first));
            assertEquals(BigInteger.valueOf(3), inventory.amount(second));
            assertEquals(BigInteger.valueOf(3), inventory.extract(first, BigInteger.ONE).inventory().amount(second));
        }
    }

    @Test
    void acceptsExactlyTenToTheThirtiethAndRejectsAnAdditionalUnitWithoutMutation() {
        BigInteger maximum = FactoryLimits.MAX_INVENTORY_AMOUNT;
        ResourceInventory initial = new ResourceInventory(Map.of(IRON, maximum.subtract(BigInteger.ONE)));
        ResourceInventory full = initial.insert(new ResourceAmount(IRON, BigInteger.ONE));

        assertEquals(maximum, full.amount(IRON));
        assertEquals(maximum.subtract(BigInteger.ONE), initial.amount(IRON));
        assertFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> full.insert(new ResourceAmount(IRON, BigInteger.ONE)));
        assertFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> new ResourceInventory(Map.of(IRON, maximum.add(BigInteger.ONE))));
        assertFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> ResourceInventory.empty().insert(new ResourceAmount(IRON, maximum.add(BigInteger.ONE))));
        assertEquals(Map.of(IRON, maximum), full.amounts());
    }

    @Test
    void accepts256KindsButRejectsA257thWithoutPartialStorage() {
        Map<ResourceKey, BigInteger> entries = entries(256);
        ResourceInventory full = new ResourceInventory(entries);
        ResourceKey additional = new ResourceKey(ResourceKind.ITEM, "test:resource_256");

        assertEquals(256, full.amounts().size());
        assertFailure(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                () -> full.insert(new ResourceAmount(additional, BigInteger.ONE)));
        entries.put(additional, BigInteger.ONE);
        assertFailure(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED, () -> new ResourceInventory(entries));
        assertEquals(256, full.amounts().size());
        assertEquals(BigInteger.ZERO, full.amount(additional));
        ResourceKey existing = full.amounts().keySet().iterator().next();
        assertEquals(BigInteger.valueOf(2), full.insert(new ResourceAmount(existing, BigInteger.ONE)).amount(existing));
    }

    @Test
    void variantDifferencesCountTowardsTheResourceLimit() {
        Map<ResourceKey, BigInteger> entries = new LinkedHashMap<>();
        for (int i = 0; i < 257; i++) {
            CompoundTag components = new CompoundTag();
            components.putInt("test:variant", i);
            entries.put(new ResourceKey(ResourceKind.ITEM, "test:resource", components), BigInteger.ONE);
        }

        assertFailure(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED, () -> new ResourceInventory(entries));
    }

    @Test
    void partialExtractionConservesAmountsBeyondTheLongRange() {
        BigInteger stored = new BigInteger("999999999999999999999999999999");
        BigInteger requested = new BigInteger("123456789012345678901234567890");
        ResourceInventory initial = new ResourceInventory(Map.of(IRON, stored, WATER, BigInteger.valueOf(1000)));
        ResourceInventory.Extraction extraction = initial.extract(IRON, requested);

        assertEquals(requested, extraction.extracted());
        assertEquals(stored.subtract(requested), extraction.inventory().amount(IRON));
        assertEquals(stored, extraction.inventory().amount(IRON).add(extraction.extracted()));
        assertEquals(initial, extraction.inventory().insert(new ResourceAmount(IRON, extraction.extracted())));
        assertEquals(stored, initial.amount(IRON));
        assertEquals(BigInteger.valueOf(1000), extraction.inventory().amount(WATER));
    }

    @Test
    void extractionAtOrAboveTheStoredAmountRemovesTheEntry() {
        ResourceInventory initial = new ResourceInventory(Map.of(IRON, BigInteger.valueOf(5)));
        for (BigInteger requested : new BigInteger[]{BigInteger.valueOf(5), FactoryLimits.MAX_ENCODED_QUANTITY}) {
            ResourceInventory.Extraction extraction = initial.extract(IRON, requested);

            assertEquals(BigInteger.valueOf(5), extraction.extracted());
            assertEquals(BigInteger.ZERO, extraction.inventory().amount(IRON));
            assertFalse(extraction.inventory().amounts().containsKey(IRON));
            assertTrue(extraction.inventory().amounts().isEmpty());
            assertEquals(BigInteger.valueOf(5), initial.amount(IRON));
        }
    }

    @Test
    void clearingAnEntryMakesRoomForAnotherResourceKind() {
        ResourceInventory initial = new ResourceInventory(entries(256));
        ResourceKey removed = initial.amounts().keySet().iterator().next();
        ResourceInventory emptied = initial.extract(removed, BigInteger.ONE).inventory();
        ResourceInventory refilled = emptied.insert(new ResourceAmount(IRON, BigInteger.ONE));

        assertEquals(255, emptied.amounts().size());
        assertEquals(256, refilled.amounts().size());
        assertEquals(BigInteger.ONE, refilled.amount(IRON));
        assertEquals(BigInteger.ZERO, refilled.amount(removed));
        assertEquals(256, initial.amounts().size());
    }

    @Test
    void extractingAnAbsentResourceIsAZeroResultWithoutChangingTheSnapshot() {
        ResourceInventory inventory = new ResourceInventory(Map.of(IRON, BigInteger.valueOf(5)));
        ResourceInventory.Extraction extraction = inventory.extract(WATER, BigInteger.ONE);

        assertSame(inventory, extraction.inventory());
        assertEquals(BigInteger.ZERO, extraction.extracted());
        assertEquals(Map.of(IRON, BigInteger.valueOf(5)), inventory.amounts());
    }

    @Test
    void rejectsInvalidStoredAmountsAndExtractionRequests() {
        ResourceInventory initial = new ResourceInventory(Map.of(IRON, BigInteger.valueOf(5)));
        for (BigInteger invalid : new BigInteger[]{null, BigInteger.ZERO, BigInteger.valueOf(-1)}) {
            Map<ResourceKey, BigInteger> entries = new LinkedHashMap<>();
            entries.put(IRON, invalid);
            assertFailure(FactoryDiagnostic.Code.INVALID_QUANTITY, () -> new ResourceInventory(entries));
            assertFailure(FactoryDiagnostic.Code.INVALID_QUANTITY, () -> initial.extract(IRON, invalid));
        }
        assertFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> initial.extract(IRON, FactoryLimits.MAX_ENCODED_QUANTITY.add(BigInteger.ONE)));
        assertEquals(BigInteger.valueOf(5), initial.amount(IRON));
    }

    @Test
    void valueEqualityDoesNotDependOnMapInsertionOrder() {
        Map<ResourceKey, BigInteger> first = new LinkedHashMap<>();
        first.put(IRON, BigInteger.valueOf(3));
        first.put(WATER, BigInteger.valueOf(1000));
        Map<ResourceKey, BigInteger> second = new LinkedHashMap<>();
        second.put(WATER, BigInteger.valueOf(1000));
        second.put(IRON, BigInteger.valueOf(3));

        assertEquals(new ResourceInventory(first), new ResourceInventory(second));
        assertEquals(new ResourceInventory(first).hashCode(), new ResourceInventory(second).hashCode());
        assertNotEquals(new ResourceInventory(first), ResourceInventory.empty());
    }

    private static Map<ResourceKey, BigInteger> entries(int count) {
        Map<ResourceKey, BigInteger> entries = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            entries.put(new ResourceKey(ResourceKind.ITEM, "test:resource_" + i), BigInteger.ONE);
        }
        return entries;
    }

    private static void assertFailure(FactoryDiagnostic.Code expected, Runnable action) {
        ModelValidationException exception = assertThrows(ModelValidationException.class, action::run);
        assertEquals(expected, exception.diagnostic().code());
    }
}
