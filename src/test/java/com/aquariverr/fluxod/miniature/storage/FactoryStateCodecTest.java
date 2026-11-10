package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.MachineStack;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryStateCodecTest {

    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    private static final ResourceKey RESOURCE = new ResourceKey(ResourceKind.ITEM, "test:resource");
    private static final Set<String> STATE_FIELDS = Set.of("format", "schemaVersion", "gridSize", "revision",
            "cells", "inputs", "outputs", "enabled", "redstoneMode", "lastPaidGameTime");

    @Test
    void freshStatePersistsTheCompleteEmptyThreeByThreeStorage() throws IOException {
        FactoryState fresh = FactoryState.fresh();
        CompoundTag saved = fresh.save();

        assertEquals(STATE_FIELDS, saved.getAllKeys());
        assertEquals(FactoryStateCodec.FORMAT, saved.getString("format"));
        assertEquals(FactoryStateCodec.SCHEMA_VERSION, saved.getInt("schemaVersion"));
        assertEquals(3, saved.getInt("gridSize"));
        assertEquals(0L, saved.getLong("revision"));
        assertEquals(9, saved.getList("cells", Tag.TAG_COMPOUND).size());
        for (Tag child : saved.getList("cells", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) child;
            assertEquals(Set.of("type"), cell.getAllKeys());
            assertEquals("empty", cell.getString("type"));
        }
        assertTrue(((ListTag) saved.get("inputs")).isEmpty());
        assertTrue(((ListTag) saved.get("outputs")).isEmpty());
        FactoryState restored = FactoryState.load(binaryRoundTrip(saved), REGISTRIES);
        assertEquals(FactoryState.Status.READY, restored.status());
        assertStorageEquals(fresh.storage().orElseThrow(), restored.storage().orElseThrow());
        assertEquals(saved, restored.save());
    }

    @Test
    void restoresEverySupportedGridSizeWithNoMachineRegistryLookup() {
        for (int size : new int[]{3, 5, 7, 9, 11}) {
            FactoryLayout layout = new FactoryLayout(size, FactoryLayout.empty(size).cells(), 9_876_543_210L);
            FactoryStorage storage = FactoryStorage.restore(layout, MachineInventory.empty(),
                    ResourceInventory.empty(), ResourceInventory.empty(), REGISTRIES);
            FactoryState state = FactoryState.of(storage, REGISTRIES);
            FactoryStorage restored = FactoryStateCodec.load(state.save(), REGISTRIES);

            assertEquals(size, restored.layout().size());
            assertEquals(size * size, restored.layout().cells().size());
            assertEquals(9_876_543_210L, restored.layout().revision());
            assertStorageEquals(storage, restored);
        }
    }

    @Test
    void preservesMarkersArrowsEveryDirectionExactQuantitiesAndConfigurationRevision() throws IOException {
        FactoryStorage storage = FactoryStorage.empty();
        for (GridDirection direction : GridDirection.values()) {
            int index = direction.ordinal() * 2;
            ResourceAmount amount = new ResourceAmount(RESOURCE,
                    FactoryLimits.MAX_ENCODED_QUANTITY.subtract(BigInteger.valueOf(index)));
            storage = storage.withConfiguration(new GridPosition(index % 3, index / 3),
                    new FactoryCell.Marker(amount, direction));
            storage = storage.withConfiguration(new GridPosition((index + 1) % 3, (index + 1) / 3),
                    new FactoryCell.Arrow(direction));
        }
        FactoryState state = FactoryState.of(storage, REGISTRIES);
        CompoundTag saved = binaryRoundTrip(state.save());
        FactoryStorage restored = FactoryStateCodec.load(saved, REGISTRIES);

        assertEquals(8L, restored.layout().revision());
        assertStorageEquals(storage, restored);
        assertEquals(32, saved.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getByteArray("amount").length);
        assertSame(storage, storage.withConfiguration(new GridPosition(0, 0), storage.layout().cellAt(new GridPosition(0, 0))));
    }

    @Test
    void inputsAndOutputsKeepIndependentQuantitiesForTheSameResource() {
        ResourceKey fluid = new ResourceKey(ResourceKind.FLUID, "test:resource");
        ResourceKey chemical = new ResourceKey(ResourceKind.CHEMICAL, "test:resource");
        FactoryStorage original = FactoryStorage.empty();
        FactoryStorage configured = original
                .withInputs(new ResourceInventory(Map.of(RESOURCE, FactoryLimits.MAX_INVENTORY_AMOUNT,
                        fluid, BigInteger.valueOf(1000))))
                .withOutputs(new ResourceInventory(Map.of(RESOURCE, BigInteger.valueOf(13),
                        chemical, BigInteger.valueOf(2000))));
        FactoryStorage restored = FactoryStateCodec.load(FactoryStateCodec.save(configured, REGISTRIES), REGISTRIES);

        assertTrue(original.inputs().amounts().isEmpty());
        assertTrue(original.outputs().amounts().isEmpty());
        assertStorageEquals(configured, restored);
        assertEquals(FactoryLimits.MAX_INVENTORY_AMOUNT, restored.inputs().amount(RESOURCE));
        assertEquals(BigInteger.valueOf(13), restored.outputs().amount(RESOURCE));
        assertEquals(BigInteger.ZERO, restored.outputs().amount(fluid));
        assertEquals(BigInteger.ZERO, restored.inputs().amount(chemical));
        assertEquals(0L, restored.layout().revision());
    }

    @Test
    void unknownFormatOrVersionIsPreservedWithoutExposingStorage() {
        for (String format : new String[]{"test:future_factory", FactoryStateCodec.FORMAT}) {
            CompoundTag saved = populatedState().save();
            saved.putString("format", format);
            if (format.equals(FactoryStateCodec.FORMAT)) saved.putInt("schemaVersion", 2);
            saved.putByteArray("future_payload", new byte[]{3, 4});
            assertThrows(IllegalArgumentException.class, () -> FactoryStateCodec.load(saved, REGISTRIES));
            FactoryState state = FactoryState.load(saved, REGISTRIES);

            assertEquals(FactoryState.Status.UNKNOWN_FORMAT, state.status());
            assertTrue(state.storage().isEmpty());
            assertEquals(saved, state.save());
            assertPreservedStateCopies(state, saved, "future_payload");
        }
    }

    @Test
    void legacyNbtIsNotAcceptedAsAValidLayoutAndRetainsAllOriginalFields() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("gridSize", 3);
        legacy.putLong("energy", 9_000_000_000L);
        legacy.putString("owner", "legacy-owner");
        legacy.putByteArray("legacy_payload", new byte[]{3, 4});
        CompoundTag machine = new CompoundTag();
        machine.putString("id", "minecraft:furnace");
        machine.putInt("Count", 64);
        ListTag machines = new ListTag();
        machines.add(machine);
        legacy.put("machines", machines);
        FactoryState state = FactoryState.load(legacy, REGISTRIES);

        assertEquals(FactoryState.Status.INVALID_STATE, state.status());
        assertTrue(state.storage().isEmpty());
        assertEquals(legacy, state.save());
        assertPreservedStateCopies(state, legacy, "legacy_payload");
        assertEquals(64, state.save().getList("machines", Tag.TAG_COMPOUND).getCompound(0).getInt("Count"));
        assertInvalid(new CompoundTag());
    }

    @Test
    void rejectsEveryMissingRootFieldAndAnyUnknownRootField() {
        for (String field : STATE_FIELDS) {
            assertCorruptionRejected(tag -> tag.remove(field));
        }
        assertCorruptionRejected(tag -> tag.putInt("unknown", 1));
    }

    @Test
    void rootTypesMustMatchExactlyIncludingNumericTagWidths() {
        Map<String, Tag> invalidTypes = Map.of("format", IntTag.valueOf(1), "schemaVersion", ByteTag.valueOf((byte) 1),
                "gridSize", StringTag.valueOf("3"), "revision", IntTag.valueOf(2),
                "cells", new CompoundTag(), "inputs", new CompoundTag(), "outputs", StringTag.valueOf("empty"));
        invalidTypes.forEach((field, invalidType) -> assertCorruptionRejected(tag -> tag.put(field, invalidType)));
    }

    @Test
    void rejectsInvalidGridSizesAndNegativeRevisions() {
        for (int size : new int[]{Integer.MIN_VALUE, -1, 0, 1, 2, 4, 6, 8, 10, 12, Integer.MAX_VALUE}) {
            assertCorruptionRejected(tag -> tag.putInt("gridSize", size));
        }
        assertCorruptionRejected(tag -> tag.putLong("revision", -1L));
    }

    @Test
    void cellListRequiresExactlyNSquaredCompoundEntries() {
        assertCorruptionRejected(tag -> cells(tag).remove(0));
        assertCorruptionRejected(tag -> cells(tag).add(cells(tag).getCompound(0).copy()));
        assertCorruptionRejected(tag -> tag.put("cells", new ListTag()));
        assertCorruptionRejected(tag -> {
            ListTag strings = new ListTag();
            for (int i = 0; i < 9; i++) strings.add(StringTag.valueOf("empty"));
            tag.put("cells", strings);
        });
    }

    @Test
    void rejectsUnknownCellKindsMissingTypesAndInvalidTypes() {
        assertCorruptionRejected(tag -> cells(tag).getCompound(2).putString("type", "legacy_machine"));
        assertCorruptionRejected(tag -> cells(tag).getCompound(2).remove("type"));
        assertCorruptionRejected(tag -> cells(tag).getCompound(2).putInt("type", 0));
        for (int index : new int[]{0, 1, 2}) {
            assertCorruptionRejected(tag -> cells(tag).getCompound(index).putString("unknown", "value"));
        }
    }

    @Test
    void markerAndArrowDirectionsAreRequiredAndStrict() {
        for (int index : new int[]{0}) {
            assertCorruptionRejected(tag -> cells(tag).getCompound(index).remove("direction"));
            assertCorruptionRejected(tag -> cells(tag).getCompound(index).putInt("direction", 0));
            for (String direction : new String[]{"right", "NORTH", "", " RIGHT"}) {
                assertCorruptionRejected(tag -> cells(tag).getCompound(index).putString("direction", direction));
            }
        }
        assertCorruptionRejected(tag -> cells(tag).getCompound(1).remove("weights"));
        for (int[] weights : new int[][]{{}, {1}, {0, 0, 0, 0}, {-1, 1, 0, 0}, {1000001, 0, 0, 0}})
            assertCorruptionRejected(tag -> cells(tag).getCompound(1).putIntArray("weights", weights));
        assertCorruptionRejected(tag -> cells(tag).getCompound(1).putString("weights", "right"));
    }

    @Test void weightedBranchesAndLegacyDirectionsSurviveSaveAndUpgrade() {
        var arrow = new FactoryCell.Arrow(Map.of(GridDirection.RIGHT, 1, GridDirection.UP, 3));
        var storage = FactoryStorage.empty().withConfiguration(new GridPosition(2, 1), arrow);
        var saved = FactoryStateCodec.save(storage, REGISTRIES);
        assertEquals(arrow, FactoryStateCodec.load(saved, REGISTRIES).layout().cellAt(new GridPosition(2, 1)));
        var upgraded = storage.upgrade();
        assertEquals(arrow, upgraded.layout().cellAt(new GridPosition(2, 1)));
        assertEquals(new FactoryCell.Arrow(), upgraded.layout().cellAt(new GridPosition(4, 1)));
        var legacy = cells(saved).getCompound(5);
        legacy.remove("weights"); legacy.putString("direction", "UP");
        assertEquals(new FactoryCell.Arrow(GridDirection.UP), FactoryStateCodec.load(saved, REGISTRIES).layout().cellAt(new GridPosition(2, 1)));
        legacy.putString("direction", "north");
        assertInvalid(saved);
    }

    @Test
    void markerResourceAndPositiveQuantityAreRequiredAndStrict() {
        for (String field : new String[]{"resource", "amount"}) {
            assertCorruptionRejected(tag -> cells(tag).getCompound(0).remove(field));
        }
        assertCorruptionRejected(tag -> cells(tag).getCompound(0).putString("resource", "test:resource"));
        assertCorruptionRejected(tag -> cells(tag).getCompound(0).getCompound("resource").putString("extra", "value"));
        assertCorruptionRejected(tag -> cells(tag).getCompound(0).putLong("amount", 1L));
        for (byte[] invalid : new byte[][]{new byte[]{0}, new byte[0], new byte[33]}) {
            assertCorruptionRejected(tag -> cells(tag).getCompound(0).putByteArray("amount", invalid));
        }
    }

    @Test
    void rejectsMalformedOrDuplicateInputAndOutputInventoryEntries() {
        for (String inventory : new String[]{"inputs", "outputs"}) {
            assertCorruptionRejected(tag -> {
                ListTag entries = (ListTag) tag.get(inventory);
                entries.add(entries.getCompound(0).copy());
            });
            assertCorruptionRejected(tag -> ((ListTag) tag.get(inventory)).getCompound(0)
                    .putByteArray("amount", new byte[]{0}));
            assertCorruptionRejected(tag -> ((ListTag) tag.get(inventory)).getCompound(0)
                    .putByteArray("amount", QuantityCodec.encode(FactoryLimits.MAX_INVENTORY_AMOUNT.add(BigInteger.ONE))));
            assertCorruptionRejected(tag -> {
                ListTag wrongElementType = new ListTag();
                wrongElementType.add(IntTag.valueOf(1));
                tag.put(inventory, wrongElementType);
            });
        }
    }

    @Test
    void stateLoadSaveAndCodecDoNotShareMutableNbtWithCallers() {
        FactoryState original = populatedState();
        CompoundTag pristine = original.save();
        CompoundTag encoded = original.save();
        FactoryState restored = FactoryState.load(encoded, REGISTRIES);
        FactoryStorage decoded = FactoryStateCodec.load(encoded, REGISTRIES);
        cells(encoded).getCompound(0).getCompound("resource").getCompound("components")
                .getCompound("test:payload").getByteArray("bytes")[0] = 99;
        cells(encoded).getCompound(0).putString("direction", "LEFT");
        ((ListTag) encoded.get("inputs")).getCompound(0).getByteArray("amount")[0] = 99;
        encoded.putLong("revision", 99L);

        assertNotEquals(pristine, encoded);
        assertEquals(pristine, original.save());
        assertEquals(pristine, restored.save());
        assertStorageEquals(original.storage().orElseThrow(), decoded);
        assertStorageEquals(original.storage().orElseThrow(), restored.storage().orElseThrow());
        CompoundTag exposed = restored.save();
        exposed.getList("cells", Tag.TAG_COMPOUND).clear();
        exposed.remove("inputs");
        assertEquals(pristine, restored.save());
        CompoundTag independentlySaved = FactoryStateCodec.save(decoded, REGISTRIES);
        independentlySaved.getList("cells", Tag.TAG_COMPOUND).clear();
        assertEquals(pristine, FactoryStateCodec.save(decoded, REGISTRIES));
    }

    @Test
    void rejectedConfigurationChangesAndLoadFailuresLeaveOriginalStateUntouched() {
        FactoryState state = populatedState();
        FactoryStorage storage = state.storage().orElseThrow();
        CompoundTag pristine = state.save();

        assertThrows(IllegalArgumentException.class, () -> storage.withConfiguration(new GridPosition(3, 0),
                new FactoryCell.Arrow()));
        assertThrows(IllegalArgumentException.class, () -> storage.withConfiguration(new GridPosition(2, 0),
                new FactoryCell.Machine(new MachineStack(RESOURCE, 1))));
        CompoundTag invalid = state.save();
        invalid.putLong("revision", -1L);
        FactoryState rejected = FactoryState.load(invalid, REGISTRIES);

        assertEquals(FactoryState.Status.INVALID_STATE, rejected.status());
        assertFalse(rejected.storage().isPresent());
        assertEquals(pristine, state.save());
        assertSame(storage, state.storage().orElseThrow());
        assertEquals(pristine, FactoryStateCodec.save(storage, REGISTRIES));
    }

    @Test
    void acceptsExactly512KiBOfCompleteNbtAndRejectsOneAdditionalByte() throws IOException {
        CompoundTag probe = new CompoundTag();
        probe.putByteArray("payload", new byte[0]);
        int overhead = encodedSize(probe);
        CompoundTag atLimit = new CompoundTag();
        atLimit.putByteArray("payload", new byte[FactoryStateCodec.MAX_STATE_BYTES - overhead]);
        CompoundTag aboveLimit = new CompoundTag();
        aboveLimit.putByteArray("payload", new byte[FactoryStateCodec.MAX_STATE_BYTES - overhead + 1]);

        assertEquals(512 * 1024, FactoryStateCodec.MAX_STATE_BYTES);
        assertEquals(FactoryStateCodec.MAX_STATE_BYTES, encodedSize(atLimit));
        assertEquals(FactoryStateCodec.MAX_STATE_BYTES + 1, encodedSize(aboveLimit));
        FactoryStateCodec.requireEncodable(atLimit);
        assertEquals(atLimit, binaryRoundTrip(atLimit));
        assertThrows(IllegalArgumentException.class, () -> FactoryStateCodec.requireEncodable(aboveLimit));
        assertEquals(FactoryStateCodec.MAX_STATE_BYTES + 1, encodedSize(aboveLimit));
    }

    @Test
    void completeStateDepthIncludesTheFourContainersAroundMarkerComponents() throws IOException {
        CompoundTag atLimit = nestedComponents(FactoryStateCodec.MAX_STATE_DEPTH);
        CompoundTag aboveLimit = nestedComponents(FactoryStateCodec.MAX_STATE_DEPTH + 1);
        FactoryStateCodec.requireEncodable(atLimit);
        assertEquals(atLimit, binaryRoundTrip(atLimit));
        assertThrows(IllegalArgumentException.class, () -> FactoryStateCodec.requireEncodable(aboveLimit));

        ResourceKey deepestPersistable = new ResourceKey(ResourceKind.ITEM, "test:deep",
                nestedComponents(FactoryStateCodec.MAX_STATE_DEPTH - 4));
        FactoryStorage accepted = FactoryStorage.empty().withConfiguration(new GridPosition(0, 0),
                new FactoryCell.Marker(deepestPersistable));
        CompoundTag saved = FactoryStateCodec.save(accepted, REGISTRIES);
        assertStorageEquals(accepted, FactoryStateCodec.load(binaryRoundTrip(saved), REGISTRIES));
        for (int componentDepth : new int[]{FactoryStateCodec.MAX_STATE_DEPTH - 3, FactoryStateCodec.MAX_STATE_DEPTH}) {
            ResourceKey validResource = new ResourceKey(ResourceKind.ITEM, "test:deep", nestedComponents(componentDepth));
            FactoryStorage rejected = FactoryStorage.empty().withConfiguration(new GridPosition(0, 0),
                    new FactoryCell.Marker(validResource));
            assertThrows(IllegalArgumentException.class, () -> FactoryState.of(rejected, REGISTRIES));
            assertEquals(new FactoryCell.Marker(validResource), rejected.layout().cellAt(new GridPosition(0, 0)));
        }
    }

    @Test
    void aggregateStateLimitRejectsIndividuallyLegalResourcesWithoutChangingStorage() throws IOException {
        FactoryState original = FactoryState.fresh();
        CompoundTag pristine = original.save();
        ResourceInventory inventory = oversizedInventory();
        for (ResourceKey resource : inventory.amounts().keySet()) {
            assertTrue(encodedSize(resource.components()) < FactoryLimits.MAX_COMPONENT_BYTES);
        }
        Map<ResourceKey, BigInteger> snapshot = Map.copyOf(inventory.amounts());
        FactoryStorage storage = original.storage().orElseThrow().withInputs(inventory);

        assertThrows(IllegalArgumentException.class, () -> FactoryState.of(storage, REGISTRIES));
        assertEquals(snapshot, inventory.amounts());
        assertSame(inventory, storage.inputs());
        assertEquals(9, storage.inputs().amounts().size());
        assertTrue(storage.outputs().amounts().isEmpty());
        assertEquals(pristine, original.save());
        assertTrue(original.storage().orElseThrow().inputs().amounts().isEmpty());
    }

    @Test
    void oversizedAndUnencodableLoadedStatesKeepRawDataAndExposeNoEditableStorage() throws IOException {
        CompoundTag oversized = FactoryState.fresh().save();
        oversized.put("inputs", ResourceNbtCodec.writeInventory(oversizedInventory()));
        assertTrue(encodedSize(oversized) > FactoryStateCodec.MAX_STATE_BYTES);
        assertInvalid(oversized);
        FactoryState preserved = FactoryState.load(oversized, REGISTRIES);
        CompoundTag snapshot = oversized.copy();
        ((ListTag) oversized.get("inputs")).getCompound(0).getCompound("resource")
                .getCompound("components").getByteArray("test:payload")[0] = 99;
        assertEquals(snapshot, preserved.save());
        assertEquals(FactoryState.Status.INVALID_STATE, preserved.status());
        assertTrue(preserved.storage().isEmpty());

        CompoundTag tooDeep = populatedState().save();
        cells(tooDeep).getCompound(0).getCompound("resource").put("components",
                nestedComponents(FactoryStateCodec.MAX_STATE_DEPTH));
        assertInvalid(tooDeep);
        CompoundTag invalidValue = populatedState().save();
        cells(invalidValue).getCompound(0).getCompound("resource").getCompound("components")
                .put("test:invalid", EndTag.INSTANCE);
        assertInvalid(invalidValue);
    }

    @Test
    void compactEncodingWithExcessiveNbtHeapIsRejectedBeforeStateCommit() throws IOException {
        Map<ResourceKey, BigInteger> entries = new LinkedHashMap<>();
        for (int resource = 0; resource < 3; resource++) {
            ListTag rows = new ListTag();
            for (int row = 0; row < 20_000; row++) rows.add(new CompoundTag());
            CompoundTag customData = new CompoundTag();
            customData.put("rows", rows);
            CompoundTag components = new CompoundTag();
            components.put("minecraft:custom_data", customData);
            entries.put(new ResourceKey(ResourceKind.ITEM, "test:resource_" + resource, components), BigInteger.ONE);
        }
        FactoryState original = FactoryState.fresh();
        CompoundTag pristine = original.save();
        ResourceInventory inputs = new ResourceInventory(entries);
        FactoryStorage storage = original.storage().orElseThrow().withInputs(inputs);
        CompoundTag raw = original.save();
        raw.put("inputs", ResourceNbtCodec.writeInventory(inputs));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(raw, new DataOutputStream(bytes));

        assertTrue(bytes.size() < FactoryStateCodec.MAX_STATE_BYTES);
        assertThrows(NbtAccounterException.class, () -> NbtIo.read(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), NbtAccounter.create(2L * 1024 * 1024)));
        assertThrows(IllegalArgumentException.class, () -> FactoryStateCodec.requireEncodable(raw));
        assertThrows(IllegalArgumentException.class, () -> FactoryState.of(storage, REGISTRIES));
        assertSame(inputs, storage.inputs());
        assertEquals(entries, storage.inputs().amounts());
        assertEquals(pristine, original.save());
        assertTrue(original.storage().orElseThrow().inputs().amounts().isEmpty());
    }

    private static FactoryState populatedState() {
        CompoundTag payload = new CompoundTag();
        payload.putByteArray("bytes", new byte[]{3, 4});
        payload.putString("name", "sample");
        CompoundTag components = new CompoundTag();
        components.put("test:payload", payload);
        ResourceKey resource = new ResourceKey(ResourceKind.ITEM, "test:resource", components);
        FactoryStorage storage = FactoryStorage.empty()
                .withConfiguration(new GridPosition(0, 0),
                        new FactoryCell.Marker(new ResourceAmount(resource, BigInteger.valueOf(1000)), GridDirection.DOWN))
                .withConfiguration(new GridPosition(1, 0), new FactoryCell.Arrow(GridDirection.UP))
                .withInputs(new ResourceInventory(Map.of(resource, BigInteger.valueOf(12))))
                .withOutputs(new ResourceInventory(Map.of(resource, BigInteger.valueOf(99))));
        return FactoryState.of(storage, REGISTRIES);
    }

    private static ListTag cells(CompoundTag tag) {
        return tag.getList("cells", Tag.TAG_COMPOUND);
    }

    private static ResourceInventory oversizedInventory() {
        Map<ResourceKey, BigInteger> entries = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            CompoundTag components = new CompoundTag();
            components.putByteArray("test:payload", new byte[60_000]);
            entries.put(new ResourceKey(ResourceKind.ITEM, "test:resource_" + i, components), BigInteger.ONE);
        }
        return new ResourceInventory(entries);
    }

    private static CompoundTag nestedComponents(int containerCount) {
        CompoundTag root = new CompoundTag();
        CompoundTag current = root;
        for (int i = 1; i < containerCount; i++) {
            CompoundTag child = new CompoundTag();
            current.put(i == 1 ? "test:payload" : "child", child);
            current = child;
        }
        current.putInt(containerCount == 1 ? "test:value" : "value", 7);
        return root;
    }

    private static int encodedSize(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(tag, new DataOutputStream(bytes));
        return bytes.size();
    }

    private static void assertCorruptionRejected(Consumer<CompoundTag> corrupt) {
        FactoryState original = populatedState();
        CompoundTag pristine = original.save();
        CompoundTag invalid = original.save();
        corrupt.accept(invalid);
        assertInvalid(invalid);
        assertEquals(pristine, original.save());
        assertEquals(pristine, FactoryStateCodec.save(original.storage().orElseThrow(), REGISTRIES));
    }

    private static void assertInvalid(CompoundTag invalid) {
        CompoundTag snapshot = invalid.copy();
        assertThrows(IllegalArgumentException.class, () -> FactoryStateCodec.load(invalid, REGISTRIES));
        FactoryState rejected = FactoryState.load(invalid, REGISTRIES);
        assertEquals(FactoryState.Status.INVALID_STATE, rejected.status());
        assertTrue(rejected.storage().isEmpty());
        assertEquals(snapshot, rejected.save());
        assertEquals(snapshot, invalid);
    }

    private static void assertStorageEquals(FactoryStorage expected, FactoryStorage actual) {
        assertEquals(expected.layout(), actual.layout());
        assertEquals(expected.machines().entries(), actual.machines().entries());
        assertEquals(expected.inputs(), actual.inputs());
        assertEquals(expected.outputs(), actual.outputs());
    }

    private static void assertPreservedStateCopies(FactoryState state, CompoundTag source, String payloadField) {
        CompoundTag snapshot = source.copy();
        source.getByteArray(payloadField)[0] = 99;
        source.putString("external", "changed");
        CompoundTag exposed = state.save();
        exposed.getByteArray(payloadField)[1] = 99;
        exposed.remove("cells");

        assertEquals(snapshot, state.save());
        assertArrayEquals(new byte[]{3, 4}, state.save().getByteArray(payloadField));
    }

    private static CompoundTag binaryRoundTrip(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(tag, new DataOutputStream(bytes));
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), NbtAccounter.unlimitedHeap());
    }
}
