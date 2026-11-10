package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MachineInventory {

    private final Map<GridPosition, Entry> entries;

    public MachineInventory(Map<GridPosition, Entry> entries) {
        this.entries = Map.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public static MachineInventory empty() {
        return new MachineInventory(Map.of());
    }

    public Map<GridPosition, Entry> entries() {
        return entries;
    }

    public Optional<Entry> entry(GridPosition position) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(position, "position")));
    }

    public Insertion insert(GridPosition position, ItemStack offered, HolderLookup.Provider registries) {
        Objects.requireNonNull(position, "position");
        VanillaMachinePolicy.requireAccepted(offered);
        ResourceKey identity = FactoryItemResources.keyOf(offered, registries);
        Entry current = entries.get(position);
        if (current != null && !identity.equals(current.identity)) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE,
                    "A machine cell cannot mix different item variants");
        }
        int previous = current == null ? 0 : current.count;
        int inserted = Math.min(offered.getCount(), FactoryLimits.MAX_MACHINES_PER_CELL - previous);
        ItemSnapshot remainder = new ItemSnapshot(identity, offered.getCount() - inserted, registries);
        if (inserted == 0) {
            return new Insertion(this, remainder);
        }
        Map<GridPosition, Entry> updated = new HashMap<>(entries);
        updated.put(position, new Entry(identity, previous + inserted, registries));
        return new Insertion(new MachineInventory(updated), remainder);
    }

    public Extraction extract(GridPosition position, int requested) {
        Objects.requireNonNull(position, "position");
        if (requested <= 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT,
                    "Machine extraction requires a positive count");
        }
        Entry current = entries.get(position);
        if (current == null) {
            return new Extraction(this, ItemSnapshot.EMPTY);
        }
        int extracted = Math.min(Math.min(requested, current.count), current.prototype().getMaxStackSize());
        Map<GridPosition, Entry> updated = new HashMap<>(entries);
        if (extracted == current.count) {
            updated.remove(position);
        } else {
            updated.put(position, new Entry(current.identity, current.count - extracted, current.registries));
        }
        return new Extraction(new MachineInventory(updated), new ItemSnapshot(current.identity, extracted, current.registries));
    }

    public static final class Entry {
        private final ResourceKey identity;
        private final int count;
        private final HolderLookup.Provider registries;

        public Entry(ItemStack prototype, int count, HolderLookup.Provider registries) {
            VanillaMachinePolicy.requireAccepted(prototype);
            if (prototype.getCount() != 1 || count < 1 || count > FactoryLimits.MAX_MACHINES_PER_CELL) {
                throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT,
                        "A machine entry needs a one-item prototype and a count from 1 through 64");
            }
            this.registries = Objects.requireNonNull(registries, "registries");
            this.identity = FactoryItemResources.keyOf(prototype, registries);
            this.count = count;
        }

        private Entry(ResourceKey identity, int count, HolderLookup.Provider registries) {
            this.identity = identity;
            this.count = count;
            this.registries = registries;
        }

        public ItemStack prototype() {
            return FactoryItemResources.itemOf(identity, registries);
        }

        public int count() {
            return count;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Entry entry && count == entry.count && identity.equals(entry.identity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identity, count);
        }
    }

    public static final class Insertion {
        private final MachineInventory inventory;
        private final ItemSnapshot remainder;

        private Insertion(MachineInventory inventory, ItemSnapshot remainder) {
            this.inventory = inventory;
            this.remainder = remainder;
        }

        public MachineInventory inventory() {
            return inventory;
        }

        public ItemStack remainder() {
            return remainder.stack();
        }
    }

    public static final class Extraction {
        private final MachineInventory inventory;
        private final ItemSnapshot extracted;

        private Extraction(MachineInventory inventory, ItemSnapshot extracted) {
            this.inventory = inventory;
            this.extracted = extracted;
        }

        public MachineInventory inventory() {
            return inventory;
        }

        public ItemStack extracted() {
            return extracted.stack();
        }
    }

    // Only immutable encoded identities cross the inventory boundary; component graphs are rebuilt on access.
    private record ItemSnapshot(ResourceKey identity, int count, HolderLookup.Provider registries) {
        private static final ItemSnapshot EMPTY = new ItemSnapshot(null, 0, null);

        private ItemStack stack() {
            return count == 0 ? ItemStack.EMPTY : FactoryItemResources.itemOf(identity, registries).copyWithCount(count);
        }
    }
}
