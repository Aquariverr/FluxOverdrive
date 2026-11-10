package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceNbtCodecTest {

    private static final ResourceKey IRON = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot");

    @Test
    void keyRoundTripPreservesAllKindsAndCompleteComponents() {
        for (ResourceKind kind : ResourceKind.values()) {
            CompoundTag components = kind == ResourceKind.CHEMICAL ? new CompoundTag() : components();
            ResourceKey key = new ResourceKey(kind, "test:resource", components);
            CompoundTag encoded = ResourceNbtCodec.writeKey(key);

            assertEquals(Set.of("kind", "id", "components"), encoded.getAllKeys());
            assertEquals(kind.name(), encoded.getString("kind"));
            assertEquals("test:resource", encoded.getString("id"));
            assertEquals(components, encoded.getCompound("components"));
            assertEquals(key, ResourceNbtCodec.readKey(encoded));
        }
    }

    @Test
    void keyWriteAndReadDoNotShareMutableComponents() {
        ResourceKey original = new ResourceKey(ResourceKind.ITEM, "test:resource", components());
        CompoundTag encoded = ResourceNbtCodec.writeKey(original);
        ResourceKey decoded = ResourceNbtCodec.readKey(encoded);
        encoded.getCompound("components").getCompound("test:payload").getByteArray("bytes")[0] = 99;
        encoded.getCompound("components").putString("test:extra", "modified");

        assertEquals(original, decoded);
        assertEquals(3, original.components().getCompound("test:payload").getByteArray("bytes")[0]);
        assertNotEquals(encoded.getCompound("components"), decoded.components());
        decoded.components().getCompound("test:payload").putInt("level", 99);
        assertEquals(original, decoded);
    }

    @Test
    void inventoryRoundTripsThroughBinaryNbtWithoutLosingLargeAmountsOrVariants() throws IOException {
        Map<ResourceKey, BigInteger> entries = new LinkedHashMap<>();
        entries.put(IRON, FactoryLimits.MAX_INVENTORY_AMOUNT);
        entries.put(new ResourceKey(ResourceKind.ITEM, IRON.registryId(), components()), BigInteger.valueOf(128));
        entries.put(new ResourceKey(ResourceKind.FLUID, "minecraft:water", components()), new BigInteger("100000000000000000000"));
        entries.put(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen"), BigInteger.valueOf(1000));
        ResourceInventory inventory = new ResourceInventory(entries);
        CompoundTag root = new CompoundTag();
        root.put("inventory", ResourceNbtCodec.writeInventory(inventory));
        CompoundTag reloaded = binaryRoundTrip(root);

        assertEquals(inventory, ResourceNbtCodec.readInventory(reloaded.getList("inventory", Tag.TAG_COMPOUND)));
        assertArrayEquals(QuantityCodec.encode(FactoryLimits.MAX_INVENTORY_AMOUNT),
                reloaded.getList("inventory", Tag.TAG_COMPOUND).getCompound(0).getByteArray("amount"));
    }

    @Test
    void emptyInventoryHasAValidEmptyListEncoding() throws IOException {
        ListTag encoded = ResourceNbtCodec.writeInventory(ResourceInventory.empty());
        CompoundTag root = new CompoundTag();
        root.put("inventory", encoded);

        assertTrue(encoded.isEmpty());
        assertEquals(ResourceInventory.empty(), ResourceNbtCodec.readInventory(encoded));
        assertEquals(ResourceInventory.empty(), ResourceNbtCodec.readInventory(
                binaryRoundTrip(root).getList("inventory", Tag.TAG_COMPOUND)));
    }

    @Test
    void inventoryCodecCopiesKeysAndAmountArraysOnBothSides() {
        ResourceKey key = new ResourceKey(ResourceKind.FLUID, "test:resource", components());
        ResourceInventory original = new ResourceInventory(Map.of(key, BigInteger.valueOf(128)));
        ListTag encoded = ResourceNbtCodec.writeInventory(original);
        ResourceInventory decoded = ResourceNbtCodec.readInventory(encoded);
        encoded.getCompound(0).getByteArray("amount")[0] = 1;
        encoded.getCompound(0).getCompound("resource").getCompound("components")
                .getCompound("test:payload").putInt("level", 99);
        encoded.clear();

        assertEquals(original, decoded);
        assertEquals(BigInteger.valueOf(128), original.amount(key));
        assertEquals(BigInteger.valueOf(128), decoded.amount(key));
        assertEquals(7, key.components().getCompound("test:payload").getInt("level"));
    }

    @Test
    void rejectsEachMissingKeyFieldAndUnknownKeyFields() {
        for (String field : new String[]{"kind", "id", "components"}) {
            CompoundTag encoded = ResourceNbtCodec.writeKey(IRON);
            encoded.remove(field);
            assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readKey(encoded));
        }
        CompoundTag encoded = ResourceNbtCodec.writeKey(IRON);
        encoded.putString("legacy_id", "minecraft:iron_ingot");
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readKey(encoded));
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readKey(null));
    }

    @Test
    void rejectsIncorrectKeyFieldTypesWithoutDefaultCoercion() {
        for (String field : new String[]{"kind", "id", "components"}) {
            CompoundTag encoded = ResourceNbtCodec.writeKey(IRON);
            encoded.putInt(field, 1);
            assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readKey(encoded));
        }
        CompoundTag encoded = ResourceNbtCodec.writeKey(IRON);
        encoded.put("components", new ListTag());
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readKey(encoded));
    }

    @Test
    void rejectsUnknownResourceKindsAndInvalidRegistryIds() {
        for (String kind : new String[]{"item", "GAS", "", " ITEM"}) {
            CompoundTag encoded = ResourceNbtCodec.writeKey(IRON);
            encoded.putString("kind", kind);
            assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readKey(encoded));
        }
        for (String id : new String[]{"iron_ingot", "minecraft:", "Minecraft:iron_ingot", ""}) {
            CompoundTag encoded = ResourceNbtCodec.writeKey(IRON);
            encoded.putString("id", id);
            assertFailure(FactoryDiagnostic.Code.INVALID_RESOURCE_ID, () -> ResourceNbtCodec.readKey(encoded));
        }
    }

    @Test
    void rejectsInvalidComponentIdentitiesAndPayloads() {
        CompoundTag unnamespaced = new CompoundTag();
        unnamespaced.putInt("invalid", 1);
        assertInvalidComponents(ResourceKind.ITEM, unnamespaced, FactoryDiagnostic.Code.INVALID_RESOURCE_ID);
        CompoundTag chemical = new CompoundTag();
        chemical.putInt("test:purity", 99);
        assertInvalidComponents(ResourceKind.CHEMICAL, chemical, FactoryDiagnostic.Code.INVALID_COMPONENTS);
        CompoundTag endValue = new CompoundTag();
        endValue.put("test:invalid", EndTag.INSTANCE);
        assertInvalidComponents(ResourceKind.ITEM, endValue, FactoryDiagnostic.Code.INVALID_COMPONENTS);
        CompoundTag oversized = new CompoundTag();
        oversized.putByteArray("test:payload", new byte[FactoryLimits.MAX_COMPONENT_BYTES]);
        assertInvalidComponents(ResourceKind.FLUID, oversized, FactoryDiagnostic.Code.COMPONENT_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsNonCompoundInventoryListsInsteadOfReturningEmpty() {
        ListTag numbers = new ListTag();
        numbers.add(IntTag.valueOf(1));
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("invalid"));

        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(numbers));
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(strings));
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(null));
    }

    @Test
    void rejectsMissingUnknownAndMistypedInventoryEntryFields() {
        for (String field : new String[]{"resource", "amount"}) {
            ListTag encoded = oneEntry(BigInteger.ONE);
            encoded.getCompound(0).remove(field);
            assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(encoded));
        }
        ListTag unknownField = oneEntry(BigInteger.ONE);
        unknownField.getCompound(0).putInt("count", 1);
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(unknownField));
        ListTag wrongResource = oneEntry(BigInteger.ONE);
        wrongResource.getCompound(0).putString("resource", "minecraft:iron_ingot");
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(wrongResource));
        for (Tag wrongAmount : new Tag[]{IntTag.valueOf(1), StringTag.valueOf("1"), new CompoundTag(), new ListTag()}) {
            ListTag encoded = oneEntry(BigInteger.ONE);
            encoded.getCompound(0).put("amount", wrongAmount);
            assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(encoded));
        }
    }

    @Test
    void rejectsDuplicateExactResourcesEvenWhenComponentKeyOrderDiffers() {
        CompoundTag firstComponents = new CompoundTag();
        firstComponents.putInt("test:first", 1);
        firstComponents.putString("test:second", "value");
        CompoundTag secondComponents = new CompoundTag();
        secondComponents.putString("test:second", "value");
        secondComponents.putInt("test:first", 1);
        ResourceKey first = new ResourceKey(ResourceKind.ITEM, IRON.registryId(), firstComponents);
        ResourceKey second = new ResourceKey(ResourceKind.ITEM, IRON.registryId(), secondComponents);
        ListTag encoded = ResourceNbtCodec.writeInventory(new ResourceInventory(Map.of(first, BigInteger.ONE)));
        CompoundTag duplicate = encoded.getCompound(0).copy();
        duplicate.put("resource", ResourceNbtCodec.writeKey(second));
        encoded.add(duplicate);

        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.readInventory(encoded));
        assertEquals(2, encoded.size());
        assertArrayEquals(new byte[]{1}, encoded.getCompound(0).getByteArray("amount"));
    }

    @Test
    void acceptsExactly256EntriesAndRejects257WithoutTruncation() {
        Map<ResourceKey, BigInteger> entries = new LinkedHashMap<>();
        for (int i = 0; i < 256; i++) {
            entries.put(new ResourceKey(ResourceKind.ITEM, "test:resource_" + i), BigInteger.ONE);
        }
        ResourceInventory full = new ResourceInventory(entries);
        ListTag encoded = ResourceNbtCodec.writeInventory(full);

        assertEquals(full, ResourceNbtCodec.readInventory(encoded));
        CompoundTag extra = oneEntry(BigInteger.ONE).getCompound(0);
        encoded.add(extra);
        assertFailure(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                () -> ResourceNbtCodec.readInventory(encoded));
        assertEquals(257, encoded.size());
    }

    @Test
    void rejectsZeroOversizedAndEmptyQuantitiesWithoutChangingTheEncoding() {
        ListTag zero = oneEntry(BigInteger.ONE);
        zero.getCompound(0).putByteArray("amount", new byte[]{0});
        assertFailure(FactoryDiagnostic.Code.INVALID_QUANTITY, () -> ResourceNbtCodec.readInventory(zero));
        ListTag empty = oneEntry(BigInteger.ONE);
        empty.getCompound(0).putByteArray("amount", new byte[0]);
        assertFailure(FactoryDiagnostic.Code.INVALID_QUANTITY, () -> ResourceNbtCodec.readInventory(empty));
        ListTag aboveCapacity = oneEntry(BigInteger.ONE);
        byte[] aboveCapacityBytes = QuantityCodec.encode(FactoryLimits.MAX_INVENTORY_AMOUNT.add(BigInteger.ONE));
        aboveCapacity.getCompound(0).putByteArray("amount", aboveCapacityBytes);
        assertFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> ResourceNbtCodec.readInventory(aboveCapacity));
        assertArrayEquals(aboveCapacityBytes, aboveCapacity.getCompound(0).getByteArray("amount"));
        ListTag oversizedEncoding = oneEntry(BigInteger.ONE);
        oversizedEncoding.getCompound(0).putByteArray("amount", new byte[33]);
        assertFailure(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> ResourceNbtCodec.readInventory(oversizedEncoding));
        assertEquals(33, oversizedEncoding.getCompound(0).getByteArray("amount").length);
    }

    @Test
    void preservesTheUnsignedHighBitAndAcceptsBoundedLeadingZeros() {
        ListTag encoded = oneEntry(BigInteger.ONE);
        encoded.getCompound(0).putByteArray("amount", new byte[]{0, (byte) 128});
        ResourceInventory decoded = ResourceNbtCodec.readInventory(encoded);

        assertEquals(BigInteger.valueOf(128), decoded.amount(IRON));
        assertArrayEquals(new byte[]{(byte) 128}, ResourceNbtCodec.writeInventory(decoded).getCompound(0).getByteArray("amount"));
    }

    @Test
    void sharedTypeCheckRequiresTheExactNbtTypeAndMandatoryFields() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("value", (byte) 1);

        ResourceNbtCodec.requireFields(tag, Set.of("value"));
        ResourceNbtCodec.requireType(tag, "value", Tag.TAG_BYTE);
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.requireType(tag, "value", Tag.TAG_INT));
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.requireType(tag, "missing", Tag.TAG_INT));
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.requireFields(tag, Set.of("other")));
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.requireFields(null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ResourceNbtCodec.requireType(null, "value", Tag.TAG_BYTE));
    }

    private static ListTag oneEntry(BigInteger amount) {
        return ResourceNbtCodec.writeInventory(new ResourceInventory(Map.of(IRON, amount)));
    }

    private static CompoundTag components() {
        CompoundTag payload = new CompoundTag();
        payload.putInt("level", 7);
        payload.putByteArray("bytes", new byte[]{3, 4});
        ListTag values = new ListTag();
        values.add(IntTag.valueOf(8));
        payload.put("values", values);
        CompoundTag components = new CompoundTag();
        components.put("test:payload", payload);
        return components;
    }

    private static CompoundTag binaryRoundTrip(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(tag, new DataOutputStream(bytes));
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), NbtAccounter.unlimitedHeap());
    }

    private static void assertInvalidComponents(ResourceKind kind, CompoundTag components, FactoryDiagnostic.Code expected) {
        CompoundTag tag = ResourceNbtCodec.writeKey(new ResourceKey(kind, "test:resource"));
        tag.put("components", components);
        assertFailure(expected, () -> ResourceNbtCodec.readKey(tag));
    }

    private static void assertFailure(FactoryDiagnostic.Code expected, Runnable action) {
        ModelValidationException exception = assertThrows(ModelValidationException.class, action::run);
        assertEquals(expected, exception.diagnostic().code());
    }
}
