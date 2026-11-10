package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.MachineStack;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.runtime.RedstoneMode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec.requireFields;
import static com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec.requireType;

public final class FactoryStateCodec {

    public static final String FORMAT = "flux_overdrive:miniature_factory";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_STATE_BYTES = 512 * 1024;
    public static final long MAX_STATE_NBT_HEAP = 2L * 1024 * 1024;
    // Leave room for the enclosing block entity, item stack and inventory NBT containers.
    public static final int MAX_STATE_DEPTH = 480;

    public static CompoundTag save(FactoryStorage storage, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("format", FORMAT);
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        tag.putInt("gridSize", storage.layout().size());
        tag.putLong("revision", storage.layout().revision());
        ListTag cells = new ListTag();
        for (int index = 0; index < storage.layout().cells().size(); index++) {
            GridPosition position = new GridPosition(index % storage.layout().size(), index / storage.layout().size());
            FactoryCell cell = storage.layout().cells().get(index);
            CompoundTag encoded = new CompoundTag();
            switch (cell) {
                case FactoryCell.Empty ignored -> encoded.putString("type", "empty");
                case FactoryCell.Arrow arrow -> {
                    encoded.putString("type", "arrow");
                    encoded.putIntArray("weights", java.util.Arrays.stream(GridDirection.values()).mapToInt(arrow::weight).toArray());
                }
                case FactoryCell.Marker marker -> {
                    encoded.putString("type", "marker");
                    encoded.putString("direction", marker.direction().name());
                    encoded.put("resource", ResourceNbtCodec.writeKey(marker.resource().resource()));
                    encoded.putByteArray("amount", QuantityCodec.encode(marker.resource().amount()));
                }
                case FactoryCell.Machine machine -> {
                    MachineInventory.Entry entry = storage.machines().entry(position).orElseThrow();
                    encoded.putString("type", "machine");
                    encoded.putString("direction", machine.direction().name());
                    CompoundTag prototype = (CompoundTag) entry.prototype().save(registries);
                    prototype.putInt("count", 1);
                    if (!prototype.contains("components")) prototype.put("components", new CompoundTag());
                    encoded.put("prototype", prototype);
                    encoded.putInt("count", entry.count());
                    if (machine.recipe() != null) encoded.put("recipe", RecipeReferenceCodec.write(machine.recipe()));
                    if (!machine.outputDirections().isEmpty()) encoded.put("productRoutes", ProductRoutingCodec.write(machine.outputDirections()));
                }
            }
            cells.add(encoded);
        }
        tag.put("cells", cells);
        tag.put("inputs", ResourceNbtCodec.writeInventory(storage.inputs()));
        tag.put("outputs", ResourceNbtCodec.writeInventory(storage.outputs()));
        if (!storage.configuredInputCapacity().equals(com.aquariverr.fluxod.miniature.model.FactoryLimits.MAX_INVENTORY_AMOUNT))
            tag.putByteArray("inputCapacity", QuantityCodec.encode(storage.configuredInputCapacity()));
        if (!storage.inputCapacities().isEmpty())
            tag.put("inputCapacities", ResourceNbtCodec.writeInventory(new ResourceInventory(storage.inputCapacities())));
        tag.putBoolean("enabled", storage.enabled());
        tag.putString("redstoneMode", storage.redstoneMode().name());
        tag.putLong("lastPaidGameTime", storage.lastPaidGameTime());
        if (storage.batch() != null) tag.put("batch", RunningBatchCodec.write(storage.batch()));
        requireEncodable(tag);
        return tag;
    }

    public static FactoryStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        requireEncodable(tag);
        var fields = new java.util.HashSet<>(Set.of("format", "schemaVersion", "gridSize", "revision", "cells", "inputs", "outputs"));
        if (tag.contains("enabled")) fields.addAll(Set.of("enabled", "redstoneMode", "lastPaidGameTime"));
        if (tag.contains("batch")) fields.add("batch");
        if (tag.contains("inputCapacity")) fields.add("inputCapacity");
        if (tag.contains("inputCapacities")) fields.add("inputCapacities");
        requireFields(tag, fields);
        requireType(tag, "format", Tag.TAG_STRING);
        requireType(tag, "schemaVersion", Tag.TAG_INT);
        if (!FORMAT.equals(tag.getString("format")) || tag.getInt("schemaVersion") != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unknown miniature factory format or schema version");
        }
        requireType(tag, "gridSize", Tag.TAG_INT);
        requireType(tag, "revision", Tag.TAG_LONG);
        requireType(tag, "cells", Tag.TAG_LIST);
        requireType(tag, "inputs", Tag.TAG_LIST);
        requireType(tag, "outputs", Tag.TAG_LIST);
        int size = tag.getInt("gridSize");
        FactoryLayout.empty(size);
        ListTag savedCells = (ListTag) tag.get("cells");
        if (savedCells.getElementType() != Tag.TAG_COMPOUND || savedCells.size() != size * size) {
            throw new IllegalArgumentException("State must contain exactly N * N compound cells");
        }
        List<FactoryCell> cells = new ArrayList<>(savedCells.size());
        Map<GridPosition, MachineInventory.Entry> machines = new LinkedHashMap<>();
        for (int index = 0; index < savedCells.size(); index++) {
            CompoundTag cell = savedCells.getCompound(index);
            requireType(cell, "type", Tag.TAG_STRING);
            cells.add(switch (cell.getString("type")) {
                case "empty" -> {
                    requireFields(cell, Set.of("type"));
                    yield FactoryCell.Empty.INSTANCE;
                }
                case "arrow" -> {
                    if (!cell.contains("weights")) {
                        requireFields(cell, Set.of("type", "direction"));
                        yield new FactoryCell.Arrow(direction(cell));
                    }
                    requireFields(cell, Set.of("type", "weights"));
                    requireType(cell, "weights", Tag.TAG_INT_ARRAY);
                    int[] weights = cell.getIntArray("weights");
                    if (weights.length != GridDirection.values().length) throw new IllegalArgumentException("Invalid arrow weights");
                    var branches = new java.util.EnumMap<GridDirection, Integer>(GridDirection.class);
                    for (GridDirection direction : GridDirection.values()) {
                        if (weights[direction.ordinal()] != 0) branches.put(direction, weights[direction.ordinal()]);
                    }
                    yield new FactoryCell.Arrow(branches);
                }
                case "marker" -> {
                    requireFields(cell, Set.of("type", "direction", "resource", "amount"));
                    requireType(cell, "resource", Tag.TAG_COMPOUND);
                    requireType(cell, "amount", Tag.TAG_BYTE_ARRAY);
                    yield new FactoryCell.Marker(new ResourceAmount(ResourceNbtCodec.readKey(cell.getCompound("resource")),
                            QuantityCodec.decode(cell.getByteArray("amount"))), direction(cell));
                }
                case "machine" -> {
                    var machineFields = new java.util.HashSet<>(Set.of("type", "prototype", "count"));
                    if (cell.contains("recipe")) machineFields.add("recipe");
                    if (cell.contains("direction")) machineFields.add("direction");
                    if (cell.contains("productRoutes")) { machineFields.add("productRoutes"); requireType(cell, "productRoutes", Tag.TAG_LIST); }
                    requireFields(cell, machineFields);
                    if (cell.contains("recipe")) requireType(cell, "recipe", Tag.TAG_COMPOUND);
                    requireType(cell, "prototype", Tag.TAG_COMPOUND);
                    requireType(cell, "count", Tag.TAG_INT);
                    CompoundTag prototypeTag = cell.getCompound("prototype");
                    requireFields(prototypeTag, Set.of("id", "count", "components"));
                    requireType(prototypeTag, "id", Tag.TAG_STRING);
                    requireType(prototypeTag, "count", Tag.TAG_INT);
                    requireType(prototypeTag, "components", Tag.TAG_COMPOUND);
                    if (prototypeTag.getInt("count") != 1) {
                        throw new IllegalArgumentException("A machine prototype must contain exactly one item");
                    }
                    ItemStack prototype = ItemStack.STRICT_CODEC.parse(
                            registries.createSerializationContext(NbtOps.INSTANCE), prototypeTag).getOrThrow();
                    VanillaMachinePolicy.requireAccepted(prototype);
                    MachineInventory.Entry entry = new MachineInventory.Entry(prototype, cell.getInt("count"), registries);
                    machines.put(new GridPosition(index % size, index / size), entry);
                    yield new FactoryCell.Machine(new MachineStack(
                            FactoryItemResources.keyOf(prototype, registries), entry.count()),
                            cell.contains("recipe") ? RecipeReferenceCodec.read(cell.getCompound("recipe")) : null,
                            cell.contains("direction") ? direction(cell) : GridDirection.RIGHT,
                            cell.contains("productRoutes") ? ProductRoutingCodec.read((ListTag) cell.get("productRoutes")) : Map.of());
                }
                default -> throw new IllegalArgumentException("Unknown factory cell type");
            });
        }
        FactoryStorage storage = FactoryStorage.restore(new FactoryLayout(size, cells, tag.getLong("revision")),
                new MachineInventory(machines), ResourceNbtCodec.readInventory((ListTag) tag.get("inputs")),
                ResourceNbtCodec.readInventory((ListTag) tag.get("outputs")), registries);
        if (tag.contains("inputCapacity")) {
            requireType(tag, "inputCapacity", Tag.TAG_BYTE_ARRAY);
            storage = storage.withInputCapacity(QuantityCodec.decode(tag.getByteArray("inputCapacity")));
        }
        if (tag.contains("inputCapacities")) {
            requireType(tag, "inputCapacities", Tag.TAG_LIST);
            storage = storage.withInputCapacities(ResourceNbtCodec.readInventory((ListTag) tag.get("inputCapacities")).amounts());
        }
        if (tag.contains("enabled")) {
            requireType(tag, "enabled", Tag.TAG_BYTE);
            if (tag.getByte("enabled") < 0 || tag.getByte("enabled") > 1) throw new IllegalArgumentException("Invalid run switch");
            requireType(tag, "redstoneMode", Tag.TAG_STRING);
            requireType(tag, "lastPaidGameTime", Tag.TAG_LONG);
            storage = storage.withControls(tag.getBoolean("enabled"), RedstoneMode.valueOf(tag.getString("redstoneMode")))
                    .withBatch(null, tag.getLong("lastPaidGameTime"));
        }
        if (tag.contains("batch")) {
            if (!tag.contains("enabled")) throw new IllegalArgumentException("Batch is missing runtime state");
            requireType(tag, "batch", Tag.TAG_COMPOUND);
            storage = storage.withBatch(RunningBatchCodec.read(tag.getCompound("batch")), storage.lastPaidGameTime());
            save(storage.completionPreview(), registries);
        }
        return storage;
    }

    private static GridDirection direction(CompoundTag tag) {
        requireType(tag, "direction", Tag.TAG_STRING);
        return GridDirection.valueOf(tag.getString("direction"));
    }

    public static void requireEncodable(CompoundTag tag) {
        requireDepth(tag, 0);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream() {
                @Override
                public void write(int value) {
                    requireCapacity(1);
                    super.write(value);
                }

                @Override
                public void write(byte[] values, int offset, int length) {
                    java.util.Objects.checkFromIndexSize(offset, length, values.length);
                    requireCapacity(length);
                    super.write(values, offset, length);
                }

                private void requireCapacity(int added) {
                    if (added > MAX_STATE_BYTES - size()) {
                        throw new IllegalArgumentException("Factory state exceeds the 512 KiB encoded size limit");
                    }
                }
            };
            NbtIo.writeUnnamedTag(tag, new DataOutputStream(bytes));
            // Network NBT counts decoded allocations, which can greatly exceed the encoded size.
            NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                    NbtAccounter.create(MAX_STATE_NBT_HEAP));
        } catch (IOException | NbtAccounterException exception) {
            throw new IllegalArgumentException("Factory state cannot be encoded without data loss", exception);
        }
    }

    private static void requireDepth(Tag tag, int depth) {
        if (tag.getId() == Tag.TAG_END) throw new IllegalArgumentException("End tags are not state values");
        if (tag instanceof CompoundTag compound) {
            if (depth >= MAX_STATE_DEPTH) throw new IllegalArgumentException("Factory state is nested too deeply");
            for (String key : compound.getAllKeys()) requireDepth(compound.get(key), depth + 1);
        } else if (tag instanceof ListTag list) {
            if (depth >= MAX_STATE_DEPTH) throw new IllegalArgumentException("Factory state is nested too deeply");
            for (Tag child : list) requireDepth(child, depth + 1);
        }
    }

    private FactoryStateCodec() {}
}
