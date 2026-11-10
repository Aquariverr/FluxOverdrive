package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.fml.ModList;
import com.aquariverr.fluxod.integration.mekanism.MekanismMiniatureMarkerContents;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class FactoryEditing {

    public static Prepared prepare(FactoryState state, ItemStack carried,
                                   MiniatureFactoryCommandPayload command, HolderLookup.Provider registries) {
        FactoryStorage storage = state.storage().orElseThrow(() -> rejected(Result.STATE_UNAVAILABLE));
        if (command.action().isInventory()) return prepareInventory(state, storage, carried, command, registries);
        if (command.action() == MiniatureFactoryCommandPayload.Action.SET_INPUT_CAPACITY) {
            var resources = storage.inputResources();
            if (command.resourceIndex() < 0 || command.resourceIndex() >= resources.size()) throw rejected(Result.INVALID_POSITION);
            FactoryStorage updated = storage.withInputCapacity(resources.get(command.resourceIndex()), command.quantity());
            return new Prepared(updated == storage ? state : FactoryState.of(updated, registries), carried);
        }
        if (command.action().isControl()) {
            FactoryStorage updated = storage.withControls(command.action() == MiniatureFactoryCommandPayload.Action.TOGGLE_RUN
                    ? !storage.enabled() : storage.enabled(), command.action() == MiniatureFactoryCommandPayload.Action.CYCLE_REDSTONE
                    ? storage.redstoneMode().next() : storage.redstoneMode());
            return new Prepared(FactoryState.of(updated, registries), carried);
        }
        if (storage.batch() != null) throw rejected(Result.BATCH_LOCKED);
        GridPosition position = new GridPosition(command.x(), command.y());
        if (!storage.layout().contains(position)) throw rejected(Result.INVALID_POSITION);
        FactoryCell cell = storage.layout().cellAt(position);
        FactoryStorage updated;
        ItemStack cursor = carried;
        switch (command.action()) {
            case INSERT_MACHINE -> {
                int requested = machineQuantity(command);
                if (carried.isEmpty()) throw rejected(Result.INVALID_MACHINE);
                if (!(cell instanceof FactoryCell.Empty || cell instanceof FactoryCell.Machine)) {
                    throw rejected(Result.INVALID_CELL);
                }
                int offered = Math.min(carried.getCount(), requested);
                FactoryStorage.Insertion transfer = storage.insertMachine(position, carried.copyWithCount(offered), registries);
                int inserted = offered - transfer.remainder().getCount();
                if (inserted == 0) throw rejected(Result.INVALID_CELL);
                updated = transfer.storage();
                cursor = carried.copyWithCount(carried.getCount() - inserted);
            }
            case EXTRACT_MACHINE -> {
                int requested = machineQuantity(command);
                if (!(cell instanceof FactoryCell.Machine machine)) throw rejected(Result.INVALID_CELL);
                ItemStack prototype = FactoryItemResources.itemOf(machine.stack().variant(), registries);
                int capacity = prototype.getMaxStackSize();
                if (!carried.isEmpty()) {
                    if (!FactoryItemResources.keyOf(carried, registries).equals(machine.stack().variant())) {
                        throw rejected(Result.CURSOR_BLOCKED);
                    }
                    capacity -= carried.getCount();
                }
                if (capacity <= 0) throw rejected(Result.CURSOR_BLOCKED);
                FactoryStorage.Extraction transfer = storage.extractMachine(position, Math.min(requested, capacity), registries);
                updated = transfer.storage();
                cursor = transfer.extracted();
                cursor.setCount(cursor.getCount() + carried.getCount());
            }
            case SET_MARKER_FROM_CARRIED, SET_FLUID_MARKER_FROM_CARRIED, SET_CHEMICAL_MARKER_FROM_CARRIED -> {
                if (!(cell instanceof FactoryCell.Empty || cell instanceof FactoryCell.Marker)) throw rejected(Result.INVALID_CELL);
                if (carried.isEmpty()) throw rejected(Result.INVALID_COMPONENTS);
                ResourceKey resource;
                try {
                    resource = switch (command.action()) {
                        case SET_MARKER_FROM_CARRIED -> FactoryItemResources.keyOf(carried, registries);
                        case SET_FLUID_MARKER_FROM_CARRIED -> fluidMarker(carried, command.resourceIndex(), registries);
                        case SET_CHEMICAL_MARKER_FROM_CARRIED -> ModList.get().isLoaded("mekanism")
                                ? MekanismMiniatureMarkerContents.keyOf(carried.copy(), command.resourceIndex()) : null;
                        default -> throw rejected(Result.INVALID_COMMAND);
                    };
                    if (resource == null) throw rejected(Result.INVALID_COMPONENTS);
                } catch (IllegalArgumentException itemFailure) {
                    throw rejected(Result.INVALID_COMPONENTS);
                }
                updated = storage.withConfiguration(position, new FactoryCell.Marker(resource));
            }
            case SET_GHOST_MARKER -> {
                if (!(cell instanceof FactoryCell.Empty || cell instanceof FactoryCell.Marker)) throw rejected(Result.INVALID_CELL);
                ResourceKey resource = command.markerResource();
                try {
                    requireKnownResource(resource, registries);
                } catch (IllegalArgumentException exception) {
                    throw rejected(Result.INVALID_COMPONENTS);
                }
                // Ghosts configure a route only. Its quantity comes from the kind, never the client ingredient count.
                updated = storage.withConfiguration(position, new FactoryCell.Marker(resource));
            }
            case SET_MARKER_QUANTITY -> {
                if (!(cell instanceof FactoryCell.Marker marker)) throw rejected(Result.INVALID_CELL);
                updated = storage.withConfiguration(position, new FactoryCell.Marker(
                        new ResourceAmount(marker.resource().resource(), command.quantity()), marker.direction()));
            }
            case SET_DIRECTION -> {
                if (cell instanceof FactoryCell.Empty) throw rejected(Result.INVALID_CELL);
                updated = storage.withDirection(position, command.direction());
            }
            case ROTATE_DIRECTION -> {
                var direction = switch (cell) {
                    case FactoryCell.Marker marker -> marker.direction();
                    case FactoryCell.Machine machine -> machine.direction();
                    case FactoryCell.Arrow arrow -> arrow.direction();
                    case FactoryCell.Empty ignored -> throw rejected(Result.INVALID_CELL);
                };
                updated = cell instanceof FactoryCell.Arrow arrow ? storage.withConfiguration(position, arrow.rotated())
                        : storage.withDirection(position, direction.clockwise());
            }
            case CREATE_ARROW -> {
                if (!(cell instanceof FactoryCell.Empty)) throw rejected(Result.INVALID_CELL);
                updated = storage.withConfiguration(position, new FactoryCell.Arrow());
            }
            case ROTATE_ARROW -> {
                if (!(cell instanceof FactoryCell.Arrow arrow)) throw rejected(Result.INVALID_CELL);
                updated = storage.withConfiguration(position, arrow.rotated());
            }
            case SET_ARROW_WEIGHT -> {
                if (!(cell instanceof FactoryCell.Arrow arrow)) throw rejected(Result.INVALID_CELL);
                if (command.quantity().signum() == 0 && arrow.weights().size() == 1 && arrow.weight(command.direction()) > 0)
                    throw rejected(Result.INVALID_QUANTITY);
                updated = storage.withConfiguration(position, arrow.withWeight(command.direction(), command.quantity().intValueExact()));
            }
            case MARKER_TO_ARROW -> {
                if (!(cell instanceof FactoryCell.Marker marker)) throw rejected(Result.INVALID_CELL);
                updated = storage.withConfiguration(position, new FactoryCell.Arrow(marker.direction()));
            }
            case CLEAR_CONFIGURATION -> {
                if (cell instanceof FactoryCell.Machine) throw rejected(Result.INVALID_CELL);
                updated = storage.withConfiguration(position, FactoryCell.Empty.INSTANCE);
            }
            default -> throw rejected(Result.INVALID_COMMAND);
        }
        return new Prepared(updated == storage ? state : FactoryState.of(updated, registries), cursor);
    }

    public static void requireKnownResource(ResourceKey resource, HolderLookup.Provider registries) {
        boolean valid = switch (resource.kind()) {
            case ITEM -> FactoryItemResources.keyOf(FactoryItemResources.itemOf(resource, registries), registries).equals(resource);
            case FLUID -> FactoryFluidResources.keyOf(FactoryFluidResources.fluidOf(resource, registries), registries).equals(resource);
            case CHEMICAL -> ModList.get().isLoaded("mekanism") && ChemicalIdentity.isKnown(resource, registries);
        };
        if (!valid) throw new IllegalArgumentException("Unknown or noncanonical resource identity");
    }

    /** Loaded only after the optional mod guard, including on a dedicated server without Mekanism. */
    private static final class ChemicalIdentity {
        private static boolean isKnown(ResourceKey resource, HolderLookup.Provider registries) {
            if (!resource.components().isEmpty()) return false;
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", resource.registryId());
            tag.putLong("amount", 1);
            return mekanism.api.chemical.ChemicalStack.parse(registries, tag).filter(stack -> !stack.isEmpty()).isPresent();
        }
    }

    private static Prepared prepareInventory(FactoryState state, FactoryStorage storage, ItemStack carried,
                                             MiniatureFactoryCommandPayload command, HolderLookup.Provider registries) {
        if (command.quantity().bitLength() > 31) throw rejected(Result.INVALID_QUANTITY);
        int requested = command.quantity().intValueExact();
        FactoryStorage updated;
        ItemStack cursor;
        if (command.action() == MiniatureFactoryCommandPayload.Action.INSERT_INPUT) {
            if (carried.isEmpty()) throw rejected(Result.CURSOR_BLOCKED);
            ResourceKey resource = FactoryItemResources.keyOf(carried, registries);
            int offered = storage.inputCapacity(resource)
                    .min(BigInteger.valueOf(Math.min(requested, carried.getCount()))).intValueExact();
            if (offered <= 0) throw rejected(Result.STATE_TOO_LARGE);
            FactoryStorage.Insertion transfer = storage.insertInput(carried.copyWithCount(offered), registries);
            int inserted = offered - transfer.remainder().getCount();
            updated = transfer.storage();
            cursor = carried.copyWithCount(carried.getCount() - inserted);
        } else {
            ResourceInventory inventory = command.action() == MiniatureFactoryCommandPayload.Action.EXTRACT_OUTPUT
                    ? storage.outputs() : storage.inputs();
            if (command.resourceIndex() >= inventory.amounts().size()) throw rejected(Result.INVALID_POSITION);
            ResourceKey resource = inventory.amounts().keySet().stream().skip(command.resourceIndex()).findFirst().orElseThrow();
            if (resource.kind() != ResourceKind.ITEM) throw rejected(Result.INVALID_COMPONENTS);
            ItemStack prototype = FactoryItemResources.itemOf(resource, registries);
            int capacity = prototype.getMaxStackSize();
            if (!carried.isEmpty()) {
                if (!FactoryItemResources.keyOf(carried, registries).equals(resource)) throw rejected(Result.CURSOR_BLOCKED);
                capacity -= carried.getCount();
            }
            if (capacity <= 0) throw rejected(Result.CURSOR_BLOCKED);
            FactoryStorage.Extraction transfer = command.action() == MiniatureFactoryCommandPayload.Action.EXTRACT_OUTPUT
                    ? storage.extractOutput(resource, Math.min(requested, capacity), registries)
                    : storage.extractInput(resource, Math.min(requested, capacity), registries);
            updated = transfer.storage();
            cursor = transfer.extracted();
            cursor.setCount(cursor.getCount() + carried.getCount());
        }
        return new Prepared(updated == storage ? state : FactoryState.of(updated, registries), cursor);
    }

    private static int machineQuantity(MiniatureFactoryCommandPayload command) {
        if (command.quantity().signum() <= 0 || command.quantity().bitLength() > 7
                || command.quantity().intValue() > 64) throw rejected(Result.INVALID_QUANTITY);
        return command.quantity().intValueExact();
    }

    private static ResourceKey fluidMarker(ItemStack carried, int tank, HolderLookup.Provider registries) {
        // Read a detached stack: even a capability that initializes components cannot change the player's asset.
        IFluidHandler handler = Capabilities.FluidHandler.ITEM.getCapability(carried.copy(), null);
        if (handler == null || tank < 0 || tank >= handler.getTanks()) return null;
        FluidStack fluid = handler.getFluidInTank(tank);
        return fluid.isEmpty() ? null : FactoryFluidResources.keyOf(fluid, registries);
    }

    /** Client choices are hints only; prepare resolves the requested kind/tank again on the server cursor. */
    public static List<MarkerSource> markerSources(ItemStack carried, HolderLookup.Provider registries) {
        if (carried.isEmpty()) return List.of();
        List<MarkerSource> sources = new ArrayList<>();
        sources.add(new MarkerSource(MiniatureFactoryCommandPayload.Action.SET_MARKER_FROM_CARRIED, -1,
                FactoryItemResources.keyOf(carried, registries)));
        IFluidHandler handler = Capabilities.FluidHandler.ITEM.getCapability(carried.copy(), null);
        if (handler != null) {
            for (int tank = 0; tank < Math.min(handler.getTanks(), MiniatureFactoryCommandPayload.MAX_MARKER_TANKS); tank++) {
                FluidStack fluid = handler.getFluidInTank(tank);
                if (!fluid.isEmpty()) sources.add(new MarkerSource(
                        MiniatureFactoryCommandPayload.Action.SET_FLUID_MARKER_FROM_CARRIED, tank,
                        FactoryFluidResources.keyOf(fluid, registries)));
            }
        }
        if (ModList.get().isLoaded("mekanism")) sources.addAll(MekanismMiniatureMarkerContents.sources(carried.copy()));
        return List.copyOf(sources);
    }

    public record MarkerSource(MiniatureFactoryCommandPayload.Action action, int tank, ResourceKey resource) {}

    private static Rejected rejected(Result result) {
        return new Rejected(result);
    }

    public record Prepared(FactoryState state, ItemStack carried) {}

    public static final class Rejected extends IllegalArgumentException {
        private final Result result;

        public Rejected(Result result) {
            super(result.name());
            this.result = result;
        }

        public Result result() {
            return result;
        }
    }

    private FactoryEditing() {}
}
