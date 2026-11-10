package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ResourceNbtCodec {

    private static final Set<String> KEY_FIELDS = Set.of("kind", "id", "components");
    private static final Set<String> INVENTORY_ENTRY_FIELDS = Set.of("resource", "amount");

    public static CompoundTag writeKey(ResourceKey resource) {
        Objects.requireNonNull(resource, "resource");
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", resource.kind().name());
        tag.putString("id", resource.registryId());
        tag.put("components", resource.components());
        return tag;
    }

    public static ResourceKey readKey(CompoundTag tag) {
        requireFields(tag, KEY_FIELDS);
        requireType(tag, "kind", Tag.TAG_STRING);
        requireType(tag, "id", Tag.TAG_STRING);
        requireType(tag, "components", Tag.TAG_COMPOUND);
        ResourceKind kind;
        try {
            kind = ResourceKind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown resource kind: " + tag.getString("kind"), exception);
        }
        return new ResourceKey(kind, tag.getString("id"), tag.getCompound("components"));
    }

    public static ListTag writeInventory(ResourceInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        ListTag list = new ListTag();
        inventory.amounts().forEach((resource, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("resource", writeKey(resource));
            entry.putByteArray("amount", QuantityCodec.encode(amount));
            list.add(entry);
        });
        return list;
    }

    public static ResourceInventory readInventory(ListTag list) {
        if (list == null) throw new IllegalArgumentException("Inventory list is missing");
        if (list.getElementType() != Tag.TAG_COMPOUND
                && !(list.isEmpty() && list.getElementType() == Tag.TAG_END)) {
            throw new IllegalArgumentException("Inventory list must contain compound tags");
        }
        if (list.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) {
            throw new ModelValidationException(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                    "Inventory exceeds " + FactoryLimits.MAX_BOUNDARY_RESOURCES + " entries");
        }
        Map<ResourceKey, BigInteger> amounts = new LinkedHashMap<>();
        for (Tag child : list) {
            if (!(child instanceof CompoundTag entry)) {
                throw new IllegalArgumentException("Inventory entry must be a compound tag");
            }
            requireFields(entry, INVENTORY_ENTRY_FIELDS);
            requireType(entry, "resource", Tag.TAG_COMPOUND);
            requireType(entry, "amount", Tag.TAG_BYTE_ARRAY);
            ResourceKey resource = readKey(entry.getCompound("resource"));
            if (amounts.containsKey(resource)) {
                throw new IllegalArgumentException("Duplicate inventory resource: " + resource);
            }
            BigInteger amount = QuantityCodec.decode(entry.getByteArray("amount"));
            FactoryLimits.requirePositiveQuantity(amount);
            FactoryLimits.requireInventoryQuantity(amount);
            amounts.put(resource, amount);
        }
        return new ResourceInventory(amounts);
    }

    public static void requireFields(CompoundTag tag, Set<String> fields) {
        if (tag == null) throw new IllegalArgumentException("Compound tag is missing");
        Objects.requireNonNull(fields, "fields");
        if (!tag.getAllKeys().equals(fields)) {
            Set<String> missing = new TreeSet<>(fields);
            missing.removeAll(tag.getAllKeys());
            Set<String> unknown = new TreeSet<>(tag.getAllKeys());
            unknown.removeAll(fields);
            throw new IllegalArgumentException("Invalid compound fields; missing=" + missing + ", unknown=" + unknown);
        }
    }

    public static void requireType(CompoundTag tag, String field, int expectedTagType) {
        if (tag == null) throw new IllegalArgumentException("Compound tag is missing");
        Tag value = tag.get(field);
        if (value == null || value.getId() != expectedTagType) {
            throw new IllegalArgumentException("Field '" + field + "' must have NBT type " + expectedTagType
                    + ", found " + (value == null ? "missing" : value.getId()));
        }
    }

    private ResourceNbtCodec() {}
}
