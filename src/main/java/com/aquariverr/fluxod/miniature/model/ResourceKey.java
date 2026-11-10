package com.aquariverr.fluxod.miniature.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public final class ResourceKey {

    private static final int MAX_COMPONENT_DEPTH = 512;

    private final ResourceKind kind;
    private final String registryId;
    private final CompoundTag components;

    public ResourceKey(ResourceKind kind, String registryId) {
        this(kind, registryId, new CompoundTag());
    }

    // Components are the complete effective component values encoded with NbtOps, not a component patch.
    public ResourceKey(ResourceKind kind, String registryId, CompoundTag components) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.registryId = requireRegistryId(registryId);
        Objects.requireNonNull(components, "components");
        if (kind == ResourceKind.CHEMICAL && !components.isEmpty()) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    "Chemical identity is its registry ID and has no data components");
        }
        for (String componentId : components.getAllKeys()) {
            requireRegistryId(componentId);
        }
        requireValidTags(components, 0);
        requireComponentSize(components);
        // CompoundTag equality ignores key order while retaining numeric, list and array tag types.
        this.components = components.copy();
    }

    public ResourceKind kind() {
        return kind;
    }

    public String registryId() {
        return registryId;
    }

    public CompoundTag components() {
        return components.copy();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ResourceKey key
                && kind == key.kind && registryId.equals(key.registryId) && components.equals(key.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, registryId, components);
    }

    @Override
    public String toString() {
        return kind + ":" + registryId + components;
    }

    private static String requireRegistryId(String id) {
        Objects.requireNonNull(id, "registryId");
        if (id.indexOf(':') <= 0 || id.endsWith(":") || ResourceLocation.tryParse(id) == null) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                    "A resource ID must contain a valid explicit namespace and path");
        }
        return id;
    }

    private static void requireValidTags(Tag tag, int depth) {
        if (tag.getId() == Tag.TAG_END) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    "An end tag cannot be a component value");
        }
        if ((tag instanceof CompoundTag || tag instanceof ListTag) && depth >= MAX_COMPONENT_DEPTH) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    "Components exceed the NBT nesting limit");
        }
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                requireValidTags(compound.get(key), depth + 1);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                requireValidTags(child, depth + 1);
            }
        }
    }

    private static void requireComponentSize(CompoundTag components) {
        try {
            // NbtIo.write silently replaces oversized strings; identity validation must reject them.
            NbtIo.writeUnnamedTag(components, new DataOutputStream(new OutputStream() {
                private int bytes;

                @Override
                public void write(int value) {
                    addBytes(1);
                }

                @Override
                public void write(byte[] values, int offset, int length) {
                    Objects.checkFromIndexSize(offset, length, values.length);
                    addBytes(length);
                }

                private void addBytes(int count) {
                    if (count > FactoryLimits.MAX_COMPONENT_BYTES - bytes) {
                        throw new ModelValidationException(FactoryDiagnostic.Code.COMPONENT_LIMIT_EXCEEDED,
                                "Component NBT exceeds " + FactoryLimits.MAX_COMPONENT_BYTES + " bytes");
                    }
                    bytes += count;
                }
            }));
        } catch (IOException exception) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    "Component NBT cannot be encoded: " + exception.getMessage());
        }
    }
}
