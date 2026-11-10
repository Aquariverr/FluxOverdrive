package com.aquariverr.fluxod.miniature.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceKeyTest {

    @Test
    void itemAndFluidComponentsRemainDistinctWhenAmountsAreMerged() {
        for (ResourceKind kind : new ResourceKind[]{ResourceKind.ITEM, ResourceKind.FLUID}) {
            CompoundTag firstComponents = new CompoundTag();
            firstComponents.putInt("test:temperature", 300);
            CompoundTag secondComponents = new CompoundTag();
            secondComponents.putInt("test:temperature", 301);
            ResourceKey first = new ResourceKey(kind, "test:resource", firstComponents);
            ResourceKey second = new ResourceKey(kind, "test:resource", secondComponents);
            Map<ResourceKey, BigInteger> totals = new HashMap<>();
            totals.merge(first, BigInteger.valueOf(2), BigInteger::add);
            totals.merge(second, BigInteger.valueOf(3), BigInteger::add);

            assertNotEquals(first, second);
            assertEquals(2, totals.size());
            assertEquals(BigInteger.valueOf(2), totals.get(first));
            assertEquals(BigInteger.valueOf(3), totals.get(second));
        }
    }

    @Test
    void resourceKindAndRegistryIdArePartOfIdentity() {
        ResourceKey item = new ResourceKey(ResourceKind.ITEM, "test:resource");
        ResourceKey fluid = new ResourceKey(ResourceKind.FLUID, "test:resource");
        ResourceKey chemical = new ResourceKey(ResourceKind.CHEMICAL, "test:resource");

        assertNotEquals(item, fluid);
        assertNotEquals(item, chemical);
        assertNotEquals(fluid, chemical);
        assertNotEquals(item, new ResourceKey(ResourceKind.ITEM, "test:other"));
    }

    @Test
    void componentKeyOrderIncludingNestedCompoundsDoesNotChangeIdentity() {
        CompoundTag firstNested = new CompoundTag();
        firstNested.putInt("level", 7);
        firstNested.putString("name", "sample");
        CompoundTag first = new CompoundTag();
        first.put("test:payload", firstNested);
        first.putLong("test:energy", 4_000_000_000L);

        CompoundTag secondNested = new CompoundTag();
        secondNested.putString("name", "sample");
        secondNested.putInt("level", 7);
        CompoundTag second = new CompoundTag();
        second.putLong("test:energy", 4_000_000_000L);
        second.put("test:payload", secondNested);

        for (ResourceKind kind : new ResourceKind[]{ResourceKind.ITEM, ResourceKind.FLUID}) {
            ResourceKey firstKey = new ResourceKey(kind, "test:resource", first);
            ResourceKey secondKey = new ResourceKey(kind, "test:resource", second);

            assertEquals(firstKey, secondKey);
            assertEquals(firstKey.hashCode(), secondKey.hashCode());
            assertEquals("found", Map.of(firstKey, "found").get(secondKey));
        }
    }

    @Test
    void equalNumericValuesRetainTheirNbtTypes() {
        CompoundTag byteComponents = new CompoundTag();
        byteComponents.putByte("test:value", (byte) 1);
        CompoundTag intComponents = new CompoundTag();
        intComponents.putInt("test:value", 1);
        CompoundTag longComponents = new CompoundTag();
        longComponents.putLong("test:value", 1L);
        ResourceKey byteKey = new ResourceKey(ResourceKind.ITEM, "test:resource", byteComponents);
        ResourceKey intKey = new ResourceKey(ResourceKind.ITEM, "test:resource", intComponents);
        ResourceKey longKey = new ResourceKey(ResourceKind.ITEM, "test:resource", longComponents);

        assertNotEquals(byteKey, intKey);
        assertNotEquals(byteKey, longKey);
        assertNotEquals(intKey, longKey);
    }

    @Test
    void listOrderAndArrayTypesRemainPartOfComponentIdentity() {
        ListTag firstList = new ListTag();
        firstList.add(IntTag.valueOf(1));
        firstList.add(IntTag.valueOf(2));
        ListTag secondList = new ListTag();
        secondList.add(IntTag.valueOf(2));
        secondList.add(IntTag.valueOf(1));
        CompoundTag first = new CompoundTag();
        first.put("test:values", firstList);
        CompoundTag second = new CompoundTag();
        second.put("test:values", secondList);
        assertNotEquals(new ResourceKey(ResourceKind.ITEM, "test:resource", first),
                new ResourceKey(ResourceKind.ITEM, "test:resource", second));

        first.putByteArray("test:values", new byte[]{1, 2});
        second.putIntArray("test:values", new int[]{1, 2});
        assertNotEquals(new ResourceKey(ResourceKind.ITEM, "test:resource", first),
                new ResourceKey(ResourceKind.ITEM, "test:resource", second));
    }

    @Test
    void constructorCopiesNestedCompoundsListsAndArrays() {
        CompoundTag original = mutableComponents();
        ResourceKey key = new ResourceKey(ResourceKind.ITEM, "test:resource", original);
        ResourceKey snapshot = new ResourceKey(ResourceKind.ITEM, "test:resource", original.copy());
        int originalHash = key.hashCode();

        changeNestedComponents(original);
        original.putString("test:extra", "added");

        assertEquals(snapshot, key);
        assertEquals(originalHash, key.hashCode());
        assertEquals(snapshot.components(), key.components());
        assertNotEquals(original, key.components());
    }

    @Test
    void componentsAccessorReturnsAnIndependentDeepCopy() {
        ResourceKey key = new ResourceKey(ResourceKind.FLUID, "test:resource", mutableComponents());
        CompoundTag snapshot = key.components();
        CompoundTag exposed = key.components();
        int originalHash = key.hashCode();

        changeNestedComponents(exposed);
        exposed.remove("test:payload");

        assertEquals(snapshot, key.components());
        assertEquals(originalHash, key.hashCode());
        assertNotEquals(exposed, key.components());
    }

    @Test
    void acceptsExactly64KiBOfEncodedComponentsAndRejectsOneMoreByte() throws IOException {
        CompoundTag probe = new CompoundTag();
        probe.putByteArray("test:payload", new byte[0]);
        int overhead = encodedSize(probe);
        CompoundTag atLimit = new CompoundTag();
        atLimit.putByteArray("test:payload", new byte[65_536 - overhead]);
        CompoundTag aboveLimit = new CompoundTag();
        aboveLimit.putByteArray("test:payload", new byte[65_537 - overhead]);

        assertEquals(65_536, encodedSize(atLimit));
        assertEquals(65_537, encodedSize(aboveLimit));
        assertEquals(atLimit, new ResourceKey(ResourceKind.ITEM, "test:resource", atLimit).components());
        assertModelFailure(FactoryDiagnostic.Code.COMPONENT_LIMIT_EXCEEDED,
                () -> new ResourceKey(ResourceKind.ITEM, "test:resource", aboveLimit));
    }

    @Test
    void rejectsStringsThatExceedTheNbtModifiedUtfLengthLimit() {
        for (String value : new String[]{"a".repeat(70_000), "\u0800".repeat(22_000)}) {
            CompoundTag components = new CompoundTag();
            components.putString("test:payload", value);

            assertModelFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    () -> new ResourceKey(ResourceKind.ITEM, "test:resource", components));
        }
    }

    @Test
    void acceptsExplicitNamespacedRegistryIds() {
        for (String id : new String[]{"minecraft:iron_ingot", "test_mod-1.2:machine/path.part_2"}) {
            assertEquals(id, new ResourceKey(ResourceKind.ITEM, id).registryId());
        }
    }

    @Test
    void rejectsInvalidAndImplicitRegistryIds() {
        for (String id : new String[]{"", "iron_ingot", ":iron_ingot", "minecraft:",
                "Minecraft:iron_ingot", "minecraft:Iron_ingot", "mine craft:iron_ingot",
                "minecraft:iron ingot", "minecraft:iron:ingot", "test:path\\part"}) {
            assertModelFailure(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                    () -> new ResourceKey(ResourceKind.ITEM, id));
        }
    }

    @Test
    void componentIdsAreExplicitlyNamespacedButNestedPayloadKeysAreNotRegistryIds() {
        CompoundTag invalidComponents = new CompoundTag();
        invalidComponents.putInt("unnamespaced", 1);
        assertModelFailure(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                () -> new ResourceKey(ResourceKind.ITEM, "test:resource", invalidComponents));

        CompoundTag payload = new CompoundTag();
        payload.putInt("local value", 1);
        CompoundTag validComponents = new CompoundTag();
        validComponents.put("test:payload", payload);
        assertEquals(validComponents,
                new ResourceKey(ResourceKind.ITEM, "test:resource", validComponents).components());
    }

    @Test
    void chemicalsUseOnlyTheirRegistryIdentity() {
        ResourceKey chemical = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen");
        assertEquals(chemical, new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen", new CompoundTag()));
        assertEquals(new CompoundTag(), chemical.components());
        CompoundTag components = new CompoundTag();
        components.putInt("test:purity", 99);

        assertModelFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen", components));
    }

    @Test
    void roundTrips512ContainersWithAPrimitiveLeafAndRejectsOneMoreContainer() throws IOException {
        CompoundTag atLimit = nestedComponents(512);
        assertEquals(atLimit, new ResourceKey(ResourceKind.ITEM, "test:resource", atLimit).components());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(atLimit, new DataOutputStream(bytes));
        CompoundTag decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                NbtAccounter.unlimitedHeap());
        assertEquals(atLimit, decoded);
        CompoundTag aboveLimit = nestedComponents(513);

        assertModelFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> new ResourceKey(ResourceKind.ITEM, "test:resource", aboveLimit));
    }

    @Test
    void rejectsEndTagsAsDirectOrNestedComponentValues() {
        CompoundTag direct = new CompoundTag();
        direct.put("test:payload", EndTag.INSTANCE);
        assertModelFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> new ResourceKey(ResourceKind.ITEM, "test:resource", direct));

        CompoundTag nested = new CompoundTag();
        nested.put("leaf", EndTag.INSTANCE);
        CompoundTag components = new CompoundTag();
        components.put("test:payload", nested);
        assertModelFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> new ResourceKey(ResourceKind.ITEM, "test:resource", components));
    }

    @Test
    void resourceAmountsRequirePositiveExactQuantities() {
        ResourceKey item = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot");
        BigInteger beyondLong = new BigInteger("100000000000000000000");
        ResourceAmount amount = new ResourceAmount(item, beyondLong);

        assertSame(item, amount.resource());
        assertEquals(beyondLong, amount.amount());
        assertModelFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> new ResourceAmount(item, BigInteger.ZERO));
        assertModelFailure(FactoryDiagnostic.Code.INVALID_QUANTITY,
                () -> new ResourceAmount(item, BigInteger.valueOf(-1)));
        assertModelFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> new ResourceAmount(item, FactoryLimits.MAX_ENCODED_QUANTITY.add(BigInteger.ONE)));
    }

    @Test
    void defaultMarkersUseOneItemOrOneThousandNativeResourceUnits() {
        ResourceKey item = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot");
        ResourceKey fluid = new ResourceKey(ResourceKind.FLUID, "minecraft:water");
        ResourceKey chemical = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen");

        assertEquals(new ResourceAmount(item, BigInteger.ONE), ResourceAmount.defaultMarker(item));
        assertEquals(new ResourceAmount(fluid, BigInteger.valueOf(1000)), ResourceAmount.defaultMarker(fluid));
        assertEquals(new ResourceAmount(chemical, BigInteger.valueOf(1000)), ResourceAmount.defaultMarker(chemical));
    }

    private static CompoundTag mutableComponents() {
        CompoundTag nested = new CompoundTag();
        nested.putInt("value", 7);
        nested.putByteArray("bytes", new byte[]{3, 4});
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(8));
        nested.put("list", list);
        CompoundTag components = new CompoundTag();
        components.put("test:payload", nested);
        return components;
    }

    private static void changeNestedComponents(CompoundTag components) {
        CompoundTag nested = components.getCompound("test:payload");
        nested.putInt("value", 99);
        nested.getByteArray("bytes")[0] = 99;
        nested.getList("list", Tag.TAG_INT).set(0, IntTag.valueOf(99));
    }

    private static CompoundTag nestedComponents(int containerCount) {
        CompoundTag root = new CompoundTag();
        CompoundTag current = root;
        for (int level = 1; level < containerCount; level++) {
            CompoundTag child = new CompoundTag();
            current.put(level == 1 ? "test:payload" : "child", child);
            current = child;
        }
        current.putInt("leaf", 7);
        return root;
    }

    private static int encodedSize(CompoundTag components) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(components, new DataOutputStream(bytes));
        return bytes.size();
    }

    private static void assertModelFailure(FactoryDiagnostic.Code expected, Runnable action) {
        ModelValidationException exception = assertThrows(ModelValidationException.class, action::run);
        assertEquals(expected, exception.diagnostic().code());
    }
}
