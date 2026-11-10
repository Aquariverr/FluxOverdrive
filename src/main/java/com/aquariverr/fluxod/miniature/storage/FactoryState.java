package com.aquariverr.fluxod.miniature.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.miniature.model.ResourceKind;

public final class FactoryState {

    private final FactoryStorage storage;
    private final CompoundTag saved;
    private final Status status;

    private FactoryState(FactoryStorage storage, CompoundTag saved, Status status) {
        this.storage = storage;
        this.saved = saved.copy();
        this.status = status;
    }

    public static FactoryState fresh() {
        return of(FactoryStorage.empty(), null);
    }

    public static FactoryState of(FactoryStorage storage, HolderLookup.Provider registries) {
        if (storage.batch() != null) FactoryStateCodec.save(storage.completionPreview(), registries);
        return new FactoryState(storage, FactoryStateCodec.save(storage, registries), Status.READY);
    }

    public static FactoryState load(CompoundTag saved, HolderLookup.Provider registries) {
        if (saved.contains("format", Tag.TAG_STRING) && saved.contains("schemaVersion", Tag.TAG_INT)
                && (!FactoryStateCodec.FORMAT.equals(saved.getString("format"))
                || saved.getInt("schemaVersion") != FactoryStateCodec.SCHEMA_VERSION)) {
            return new FactoryState(null, saved, Status.UNKNOWN_FORMAT);
        }
        try {
            return new FactoryState(FactoryStateCodec.load(saved, registries), saved, Status.READY);
        } catch (RuntimeException exception) {
            // Preserve the complete rejected state instead of turning unknown assets into an empty factory.
            return new FactoryState(null, saved, Status.INVALID_STATE);
        }
    }

    /** Keeps a chemical layout opaque when the optional registry is not installed. */
    public static FactoryState rejectUnsupportedChemicals(FactoryState state) {
        if (QuantumCoolingChemicalIntegration.isAvailable() || state.storage().isEmpty()) return state;
        FactoryStorage storage = state.storage().orElseThrow();
        boolean containsChemical = hasChemicals(storage.inputs()) || hasChemicals(storage.outputs())
                || storage.inputCapacities().keySet().stream().anyMatch(key -> key.kind() == ResourceKind.CHEMICAL)
                || storage.batch() != null && (hasChemicals(storage.batch().inputs()) || hasChemicals(storage.batch().outputs())
                || hasChemicals(storage.batch().catalysts()))
                || storage.layout().cells().stream().anyMatch(cell -> switch (cell) {
                    case com.aquariverr.fluxod.miniature.model.FactoryCell.Marker marker ->
                            marker.resource().resource().kind() == ResourceKind.CHEMICAL;
                    case com.aquariverr.fluxod.miniature.model.FactoryCell.Machine machine ->
                            machine.outputDirections().keySet().stream().anyMatch(key -> key.kind() == ResourceKind.CHEMICAL)
                                    || machine.recipe() != null && machine.recipe().bindings().stream()
                                    .anyMatch(binding -> binding.resource().kind() == ResourceKind.CHEMICAL);
                    default -> false;
                });
        return containsChemical ? invalid(state.save()) : state;
    }

    private static boolean hasChemicals(ResourceInventory inventory) {
        return inventory.amounts().keySet().stream().anyMatch(key -> key.kind() == ResourceKind.CHEMICAL);
    }

    private static FactoryState invalid(CompoundTag saved) {
        return new FactoryState(null, saved, Status.INVALID_STATE);
    }

    public Optional<FactoryStorage> storage() {
        return Optional.ofNullable(storage);
    }

    public CompoundTag save() {
        return saved.copy();
    }

    public Status status() {
        return status;
    }

    public enum Status {
        READY("ready"),
        UNKNOWN_FORMAT("unknown_format"),
        INVALID_STATE("invalid_state");

        private final String translationSuffix;

        Status(String translationSuffix) {
            this.translationSuffix = translationSuffix;
        }

        public String translationKey() {
            return "message.flux_overdrive.miniature_universe." + translationSuffix;
        }
    }
}
