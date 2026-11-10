package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.MachineStack;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.runtime.RunningBatch;
import com.aquariverr.fluxod.miniature.runtime.RedstoneMode;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;

public final class FactoryStorage {

    private final FactoryLayout layout;
    private final MachineInventory machines;
    private final ResourceInventory inputs;
    private final ResourceInventory outputs;
    private final RunningBatch batch;
    private final long lastPaidGameTime;
    private final boolean enabled;
    private final RedstoneMode redstoneMode;
    private final BigInteger configuredInputCapacity;
    private final Map<ResourceKey, BigInteger> inputCapacities;

    private FactoryStorage(FactoryLayout layout, MachineInventory machines,
                           ResourceInventory inputs, ResourceInventory outputs) {
        this(layout, machines, inputs, outputs, null, Long.MIN_VALUE, false, RedstoneMode.IGNORE,
                FactoryLimits.MAX_INVENTORY_AMOUNT, Map.of());
    }

    private FactoryStorage(FactoryLayout layout, MachineInventory machines, ResourceInventory inputs,
                           ResourceInventory outputs, RunningBatch batch, long lastPaidGameTime,
                           boolean enabled, RedstoneMode redstoneMode, BigInteger configuredInputCapacity,
                           Map<ResourceKey, BigInteger> inputCapacities) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.machines = Objects.requireNonNull(machines, "machines");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.outputs = Objects.requireNonNull(outputs, "outputs");
        this.batch = batch;
        this.lastPaidGameTime = lastPaidGameTime;
        this.enabled = enabled;
        this.redstoneMode = Objects.requireNonNull(redstoneMode);
        this.configuredInputCapacity = FactoryLimits.requireInventoryQuantity(
                FactoryLimits.requirePositiveQuantity(configuredInputCapacity));
        this.inputCapacities = new ResourceInventory(inputCapacities).amounts();
        if (batch != null) {
            if (batch.energyPerTick().compareTo(FactoryRules.energyPerTick(layout.machineCount())) > 0) {
                throw new IllegalArgumentException("Batch fee exceeds locked machines");
            }
            reservedOutputs(outputs, batch);
            returnedInputs(inputs, batch);
        }
    }

    public static FactoryStorage empty() {
        return new FactoryStorage(FactoryLayout.empty(3), MachineInventory.empty(),
                ResourceInventory.empty(), ResourceInventory.empty());
    }

    public static FactoryStorage restore(FactoryLayout layout, MachineInventory machines,
                                         ResourceInventory inputs, ResourceInventory outputs,
                                         HolderLookup.Provider registries) {
        for (GridPosition position : machines.entries().keySet()) {
            if (!layout.contains(position)) {
                throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_POSITION,
                        "Machine inventory contains an out-of-grid position");
            }
        }
        for (int y = 0; y < layout.size(); y++) {
            for (int x = 0; x < layout.size(); x++) {
                GridPosition position = new GridPosition(x, y);
                FactoryCell cell = layout.cellAt(position);
                MachineInventory.Entry entry = machines.entry(position).orElse(null);
                if (entry != null) {
                    VanillaMachinePolicy.requireAccepted(entry.prototype());
                    MachineStack expected = new MachineStack(
                            FactoryItemResources.keyOf(entry.prototype(), registries), entry.count());
                    if (!(cell instanceof FactoryCell.Machine machine) || !machine.stack().equals(expected)) {
                        throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE,
                                "Machine inventory and layout disagree");
                    }
                } else if (cell instanceof FactoryCell.Machine) {
                    throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE,
                            "A machine layout cell has no real machine inventory");
                }
            }
        }
        return new FactoryStorage(layout, machines, inputs, outputs);
    }

    public FactoryLayout layout() {
        return layout;
    }

    public MachineInventory machines() {
        return machines;
    }

    public ResourceInventory inputs() {
        return inputs;
    }

    public ResourceInventory outputs() {
        return outputs;
    }

    /** Available space excludes catalysts that must return when the active batch completes. */
    public BigInteger inputCapacity(ResourceKey resource) {
        ResourceInventory committed = batch == null ? inputs : returnedInputs(inputs, batch);
        if (!committed.amounts().containsKey(resource) && committed.amounts().size() == FactoryLimits.MAX_BOUNDARY_RESOURCES) {
            return BigInteger.ZERO;
        }
        return inputLimit(resource).subtract(committed.amount(resource)).max(BigInteger.ZERO);
    }

    /** Fallback inherited from older saves; new factories default to the inventory maximum. */
    public BigInteger configuredInputCapacity() { return configuredInputCapacity; }

    public Map<ResourceKey, BigInteger> inputCapacities() { return inputCapacities; }

    public BigInteger configuredInputCapacity(ResourceKey resource) {
        return inputCapacities.getOrDefault(Objects.requireNonNull(resource), configuredInputCapacity);
    }

    public BigInteger inputLimit(ResourceKey resource) {
        return configuredInputCapacity(resource).min(com.aquariverr.fluxod.Config.miniatureInputCapacity);
    }

    /** Keep configured resources addressable even when their inventory reaches zero. */
    public java.util.List<ResourceKey> inputResources() {
        var resources = new java.util.LinkedHashSet<>(inputs.amounts().keySet());
        resources.addAll(inputCapacities.keySet());
        return java.util.List.copyOf(resources);
    }

    public FactoryStorage withInputCapacities(Map<ResourceKey, BigInteger> capacities) {
        return inputCapacities.equals(capacities) ? this
                : new FactoryStorage(layout, machines, inputs, outputs, batch, lastPaidGameTime, enabled, redstoneMode,
                        configuredInputCapacity, capacities);
    }

    public FactoryStorage withInputCapacity(ResourceKey resource, BigInteger capacity) {
        Objects.requireNonNull(resource);
        FactoryLimits.requireInventoryQuantity(FactoryLimits.requirePositiveQuantity(capacity));
        var capacities = new LinkedHashMap<>(inputCapacities);
        if (capacity.equals(configuredInputCapacity)) capacities.remove(resource);
        else capacities.put(resource, capacity);
        return withInputCapacities(capacities);
    }

    public BigInteger inputLimit() {
        return configuredInputCapacity.min(com.aquariverr.fluxod.Config.miniatureInputCapacity);
    }

    /** Restore the legacy fallback without overwriting per-resource admission limits. */
    public FactoryStorage withInputCapacity(BigInteger capacity) {
        return configuredInputCapacity.equals(capacity) ? this
                : new FactoryStorage(layout, machines, inputs, outputs, batch, lastPaidGameTime, enabled, redstoneMode, capacity, inputCapacities);
    }

    public RunningBatch batch() { return batch; }
    public long lastPaidGameTime() { return lastPaidGameTime; }
    public boolean enabled() { return enabled; }
    public RedstoneMode redstoneMode() { return redstoneMode; }

    public FactoryStorage withControls(boolean enabled, RedstoneMode mode) {
        return this.enabled == enabled && redstoneMode == mode ? this
                : new FactoryStorage(layout, machines, inputs, outputs, batch, lastPaidGameTime, enabled, mode, configuredInputCapacity, inputCapacities);
    }

    public FactoryStorage upgrade() {
        requireEditable();
        return new FactoryStorage(layout.upgrade(), machines, inputs, outputs, null, lastPaidGameTime, enabled, redstoneMode, configuredInputCapacity, inputCapacities);
    }

    /** Only virtual marker quantities and recipe choices may change during automatic balancing. */
    public FactoryStorage withBalancedLayout(FactoryLayout balanced) {
        requireEditable();
        if (layout.equals(balanced)) return this;
        if (layout.size() != balanced.size() || layout.revision() == Long.MAX_VALUE || balanced.revision() != layout.revision() + 1)
            throw new IllegalArgumentException("Invalid balanced layout revision");
        for (int i = 0; i < layout.cells().size(); i++) {
            var before = layout.cells().get(i); var after = balanced.cells().get(i);
            boolean valid = switch (before) {
                case FactoryCell.Marker marker -> after instanceof FactoryCell.Marker next && marker.direction() == next.direction()
                        && marker.resource().resource().equals(next.resource().resource());
                case FactoryCell.Machine machine -> after instanceof FactoryCell.Machine next && machine.stack().equals(next.stack())
                        && machine.direction() == next.direction() && machine.outputDirections().equals(next.outputDirections())
                        && (machine.recipe() == null || machine.recipe().equals(next.recipe()));
                default -> before.equals(after);
            };
            if (!valid) throw new IllegalArgumentException("Automatic balancing changed physical assets or routing");
        }
        return new FactoryStorage(balanced, machines, inputs, outputs, null, lastPaidGameTime, enabled, redstoneMode, configuredInputCapacity, inputCapacities);
    }

    public FactoryStorage withBatch(RunningBatch batch, long gameTime) {
        return new FactoryStorage(layout, machines, inputs, outputs, batch, gameTime, enabled, redstoneMode, configuredInputCapacity, inputCapacities);
    }

    public FactoryStorage completeBatch() {
        if (batch != null && batch.paidTicks() != batch.requiredTicks()) throw new IllegalStateException("Batch is not fully paid");
        return completionPreview();
    }

    public FactoryStorage completionPreview() {
        if (batch == null) return this;
        return new FactoryStorage(layout, machines, returnedInputs(inputs, batch), reservedOutputs(outputs, batch), null,
                lastPaidGameTime, enabled, redstoneMode, configuredInputCapacity, inputCapacities);
    }

    private static ResourceInventory reservedOutputs(ResourceInventory outputs, RunningBatch batch) {
        return mergedInventory(outputs, batch.outputs());
    }

    private static ResourceInventory returnedInputs(ResourceInventory inputs, RunningBatch batch) {
        return mergedInventory(inputs, batch.catalysts());
    }

    private static ResourceInventory mergedInventory(ResourceInventory available, ResourceInventory reserved) {
        if (reserved.amounts().isEmpty()) return available;
        var merged = new java.util.LinkedHashMap<>(available.amounts());
        for (var entry : reserved.amounts().entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), BigInteger::add);
        }
        // Validate both summed quantities and the union of available/reserved resource kinds.
        return new ResourceInventory(merged);
    }

    private FactoryStorage updated(FactoryLayout layout, MachineInventory machines, ResourceInventory inputs, ResourceInventory outputs) {
        return new FactoryStorage(layout, machines, inputs, outputs, batch, lastPaidGameTime, enabled, redstoneMode, configuredInputCapacity, inputCapacities);
    }

    private void requireEditable() {
        if (batch != null) throw new IllegalStateException("Running or paused batch locks factory configuration");
    }

    public Insertion insertMachine(GridPosition position, ItemStack offered, HolderLookup.Provider registries) {
        requireEditable();
        FactoryCell cell = layout.cellAt(position);
        if (!(cell instanceof FactoryCell.Empty || cell instanceof FactoryCell.Machine)) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE,
                    "A machine cannot replace a marker or arrow");
        }
        MachineInventory.Insertion transfer = machines.insert(position, offered, registries);
        if (transfer.inventory() == machines) return new Insertion(this, transfer.remainder());
        FactoryLayout updated = layout.withCell(position, machineCell(transfer.inventory(), position, registries,
                cell instanceof FactoryCell.Machine machine ? machine.recipe() : null,
                cell instanceof FactoryCell.Machine machine ? machine.direction() : GridDirection.RIGHT,
                cell instanceof FactoryCell.Machine machine ? machine.outputDirections() : Map.of()));
        return new Insertion(updated(updated, transfer.inventory(), inputs, outputs), transfer.remainder());
    }

    public Extraction extractMachine(GridPosition position, int requested, HolderLookup.Provider registries) {
        requireEditable();
        FactoryCell cell = layout.cellAt(position);
        MachineInventory.Extraction transfer = machines.extract(position, requested);
        if (transfer.inventory() == machines) return new Extraction(this, transfer.extracted());
        FactoryLayout updated = layout.withCell(position, machineCell(transfer.inventory(), position, registries,
                cell instanceof FactoryCell.Machine machine ? machine.recipe() : null,
                cell instanceof FactoryCell.Machine machine ? machine.direction() : GridDirection.RIGHT,
                cell instanceof FactoryCell.Machine machine ? machine.outputDirections() : Map.of()));
        return new Extraction(updated(updated, transfer.inventory(), inputs, outputs), transfer.extracted());
    }

    public FactoryStorage withConfiguration(GridPosition position, FactoryCell cell) {
        requireEditable();
        if (cell instanceof FactoryCell.Machine || layout.cellAt(position) instanceof FactoryCell.Machine) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE,
                    "Machine assets must be changed through machine transfers");
        }
        FactoryLayout updated = layout.withCell(position, cell);
        return updated == layout ? this : updated(updated, machines, inputs, outputs);
    }

    public FactoryStorage withInputs(ResourceInventory updated) {
        return updated(layout, machines, updated, outputs);
    }

    public FactoryStorage withRecipe(GridPosition position, RecipeReference reference) {
        requireEditable();
        if (!(layout.cellAt(position) instanceof FactoryCell.Machine machine)) {
            throw new IllegalArgumentException("Recipe selection requires a real machine");
        }
        FactoryLayout updated = layout.withCell(position, new FactoryCell.Machine(machine.stack(), Objects.requireNonNull(reference), machine.direction(),
                reference.equals(machine.recipe()) ? machine.outputDirections() : Map.of()));
        return updated == layout ? this : updated(updated, machines, inputs, outputs);
    }

    public FactoryStorage withDirection(GridPosition position, GridDirection direction) {
        requireEditable();
        FactoryCell cell = switch (layout.cellAt(position)) {
            case FactoryCell.Machine machine -> machine.facing(direction);
            case FactoryCell.Marker marker -> new FactoryCell.Marker(marker.resource(), direction);
            case FactoryCell.Arrow ignored -> new FactoryCell.Arrow(direction);
            case FactoryCell.Empty ignored -> throw new IllegalArgumentException("An empty cell has no direction");
        };
        FactoryLayout updated = layout.withCell(position, cell);
        return updated == layout ? this : updated(updated, machines, inputs, outputs);
    }

    public FactoryStorage withOutputs(ResourceInventory updated) {
        return updated(layout, machines, inputs, updated);
    }

    public FactoryStorage withProductDirection(GridPosition position, com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe recipe,
                                                ResourceKey product, GridDirection direction) {
        requireEditable();
        if (!(layout.cellAt(position) instanceof FactoryCell.Machine machine)) throw new IllegalArgumentException("Not a machine");
        if (machine.recipe() != null && !machine.recipe().equals(recipe.reference())) throw new IllegalArgumentException("Recipe changed");
        var routes = new LinkedHashMap<ResourceKey, GridDirection>();
        for (var output : recipe.outputs()) routes.put(output.resource(), machine.outputDirection(output.resource()));
        if (!routes.containsKey(product)) throw new IllegalArgumentException("Resource is not a recipe product");
        routes.put(product, direction);
        FactoryLayout changed = layout.withCell(position, new FactoryCell.Machine(machine.stack(), recipe.reference(), machine.direction(), routes));
        return changed == layout ? this : updated(changed, machines, inputs, outputs);
    }

    public Insertion insertInput(ItemStack offered, HolderLookup.Provider registries) {
        Objects.requireNonNull(offered, "offered");
        if (offered.isEmpty()) return new Insertion(this, ItemStack.EMPTY);
        ResourceKey resource = FactoryItemResources.keyOf(offered, registries);
        int accepted = inputCapacity(resource).min(BigInteger.valueOf(offered.getCount())).intValueExact();
        if (accepted == 0) return new Insertion(this, offered.copy());
        ResourceInventory updated = inputs.insert(new ResourceAmount(resource, BigInteger.valueOf(accepted)));
        return new Insertion(withInputs(updated), offered.copyWithCount(offered.getCount() - accepted));
    }

    public FactoryStorage insertFluid(com.aquariverr.fluxod.miniature.model.ResourceAmount offered) {
        if (offered.resource().kind() != ResourceKind.FLUID) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                    "A fluid inventory can only contain fluid resources");
        }
        requireInputSpace(offered.resource(), offered.amount());
        return withInputs(inputs.insert(offered));
    }

    public FactoryStorage insertFluid(net.neoforged.neoforge.fluids.FluidStack offered,
                                      HolderLookup.Provider registries) {
        if (offered.isEmpty()) return this;
        ResourceKey resource = FactoryFluidResources.keyOf(offered, registries);
        return insertFluid(new ResourceAmount(resource, BigInteger.valueOf(offered.getAmount())));
    }

    public FactoryStorage insertChemical(ResourceKey resource, BigInteger amount) {
        if (resource.kind() != ResourceKind.CHEMICAL) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                    "A chemical inventory can only contain chemical resources");
        }
        requireInputSpace(resource, amount);
        return withInputs(inputs.insert(new ResourceAmount(resource, amount)));
    }

    private void requireInputSpace(ResourceKey resource, BigInteger amount) {
        if (amount.compareTo(inputCapacity(resource)) > 0)
            throw new IllegalArgumentException("Insufficient input cache space");
    }

    public FluidExtraction extractChemical(ResourceKey resource, long requested) {
        if (resource.kind() != ResourceKind.CHEMICAL || requested <= 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_QUANTITY,
                    "Chemical extraction must request a positive chemical amount");
        }
        ResourceInventory.Extraction transfer = outputs.extract(resource, BigInteger.valueOf(requested));
        return new FluidExtraction(withOutputs(transfer.inventory()), transfer.extracted());
    }

    public FluidExtraction extractFluid(ResourceKey resource, int requested) {
        if (resource.kind() != ResourceKind.FLUID) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                    "A fluid extraction requires a fluid resource");
        }
        if (requested <= 0) throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_QUANTITY,
                "Extraction must request a positive fluid amount");
        ResourceInventory.Extraction transfer = outputs.extract(resource, BigInteger.valueOf(requested));
        return new FluidExtraction(withOutputs(transfer.inventory()), transfer.extracted());
    }

    public Extraction extractInput(ResourceKey resource, int requested, HolderLookup.Provider registries) {
        return extractItem(resource, requested, registries, false);
    }

    public Extraction extractOutput(ResourceKey resource, int requested, HolderLookup.Provider registries) {
        return extractItem(resource, requested, registries, true);
    }

    private Extraction extractItem(ResourceKey resource, int requested, HolderLookup.Provider registries,
                                   boolean output) {
        if (requested <= 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_QUANTITY,
                    "Extraction must request a positive item count");
        }
        ResourceInventory inventory = output ? outputs : inputs;
        if (inventory.amount(resource).signum() == 0) return new Extraction(this, ItemStack.EMPTY);
        ItemStack item = FactoryItemResources.itemOf(resource, registries);
        BigInteger limit = BigInteger.valueOf(Math.min(requested, item.getMaxStackSize()));
        ResourceInventory.Extraction transfer = inventory.extract(resource, limit);
        item.setCount(transfer.extracted().intValueExact());
        return new Extraction(output ? withOutputs(transfer.inventory()) : withInputs(transfer.inventory()), item);
    }

    private static FactoryCell machineCell(MachineInventory inventory, GridPosition position,
                                           HolderLookup.Provider registries, RecipeReference reference, GridDirection direction, Map<ResourceKey, GridDirection> routes) {
        return inventory.entry(position)
                .<FactoryCell>map(entry -> new FactoryCell.Machine(new MachineStack(
                        FactoryItemResources.keyOf(entry.prototype(), registries), entry.count()), reference, direction, routes))
                .orElse(FactoryCell.Empty.INSTANCE);
    }

    public record Insertion(FactoryStorage storage, ItemStack remainder) {}

    public record Extraction(FactoryStorage storage, ItemStack extracted) {}

    public record FluidExtraction(FactoryStorage storage, BigInteger extracted) {}
}
