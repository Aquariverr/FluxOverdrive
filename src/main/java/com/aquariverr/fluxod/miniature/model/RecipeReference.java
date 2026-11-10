package com.aquariverr.fluxod.miniature.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public record RecipeReference(String adapterId, String recipeId, int width, int height, List<Binding> bindings) {
    public static final int MAX_ID_LENGTH = 256;
    public static final Comparator<RecipeReference> ORDER = Comparator.comparing(RecipeReference::adapterId)
            .thenComparing(RecipeReference::recipeId).thenComparing(RecipeReference::variantId);

    public RecipeReference {
        requireId(adapterId);
        requireId(recipeId);
        if (width < 1 || width > 3 || height < 1 || height > 3) throw new IllegalArgumentException("Invalid recipe dimensions");
        bindings = List.copyOf(bindings);
        if (bindings.isEmpty() || bindings.size() > width * height) throw new IllegalArgumentException("Invalid recipe bindings");
        int previous = -1;
        for (Binding binding : bindings) {
            if (binding.slot() <= previous || binding.slot() >= width * height) throw new IllegalArgumentException("Unordered recipe slots");
            previous = binding.slot();
        }
    }

    public String variantId() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            out.writeInt(width);
            out.writeInt(height);
            for (Binding binding : bindings) {
                out.writeInt(binding.slot());
                out.writeUTF(binding.resource().kind().name());
                out.writeUTF(binding.resource().registryId());
                writeCanonical(binding.resource().components(), out);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint recipe inputs", exception);
        }
    }

    // Compound iteration order is not part of resource identity, including nested components.
    public static void writeCanonical(Tag tag, DataOutputStream out) throws IOException {
        out.writeByte(tag.getId());
        if (tag instanceof CompoundTag compound) {
            out.writeInt(compound.size());
            for (String key : new TreeSet<>(compound.getAllKeys())) {
                out.writeUTF(key);
                writeCanonical(compound.get(key), out);
            }
        } else if (tag instanceof ListTag list) {
            out.writeByte(list.getElementType());
            out.writeInt(list.size());
            for (Tag value : list) writeCanonical(value, out);
        } else NbtIo.writeUnnamedTag(tag, out);
    }

    private static void requireId(String id) {
        if (id == null || id.length() > MAX_ID_LENGTH || id.indexOf(':') <= 0
                || id.endsWith(":") || ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("Invalid recipe ID");
    }

    public record Binding(int slot, ResourceKey resource) {
        public Binding {
            Objects.requireNonNull(resource, "resource");
            if (slot < 0 || slot >= 9) throw new IllegalArgumentException("Invalid recipe binding slot");
        }
    }
}
