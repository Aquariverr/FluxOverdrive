package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.math.BigInteger;
import java.util.List;

/** Capability bridge that exposes input and committed output fluids in bounded int-sized chunks. */
public final class FactoryFluidHandler implements IFluidHandler {
    private static final int INPUT_TANKS = FactoryLimits.MAX_BOUNDARY_RESOURCES;
    private final TileMiniatureUniverse owner;

    public FactoryFluidHandler(TileMiniatureUniverse owner) { this.owner = owner; }

    @Override public int getTanks() { return INPUT_TANKS * 2; }
    @Override public int getTankCapacity(int tank) {
        if (!validTank(tank)) return 0;
        FactoryStorage storage = storage();
        if (storage == null) return 0;
        ResourceKey key = resource(storage, tank);
        return tank < INPUT_TANKS ? (key == null ? storage.inputLimit() : storage.inputLimit(key))
                .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact() : Integer.MAX_VALUE;
    }

    @Override public FluidStack getFluidInTank(int tank) {
        FactoryStorage storage = storage();
        ResourceKey key = storage == null ? null : resource(storage, tank);
        if (key == null) return FluidStack.EMPTY;
        try {
            FluidStack stack = FactoryFluidResources.fluidOf(key, owner.getLevel().registryAccess());
            BigInteger amount = (tank < INPUT_TANKS ? storage.inputs() : storage.outputs()).amount(key);
            return stack.copyWithAmount(amount.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact());
        } catch (IllegalArgumentException exception) {
            return FluidStack.EMPTY;
        }
    }

    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        FactoryStorage storage = storage();
        if (storage == null || tank < 0 || tank >= INPUT_TANKS || stack == null || stack.isEmpty()) return false;
        try {
            ResourceKey key = FactoryFluidResources.keyOf(stack, owner.getLevel().registryAccess());
            ResourceKey present = resource(storage, tank);
            return (present == null || present.equals(key)) && FactoryAutomation.accepts(owner, key);
        } catch (IllegalArgumentException exception) { return false; }
    }

    @Override public int fill(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return 0;
        FactoryStorage storage = storage();
        if (storage == null) return 0;
        try {
            ResourceKey key = FactoryFluidResources.keyOf(resource, owner.getLevel().registryAccess());
            if (!FactoryAutomation.accepts(owner, key)) return 0;
            BigInteger available = storage.inputCapacity(key);
            int accepted = available.min(BigInteger.valueOf(resource.getAmount())).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
            if (accepted > 0) owner.insertFluid(resource.copyWithAmount(accepted), action.simulate());
            return accepted;
        } catch (IllegalArgumentException | IllegalStateException exception) { return 0; }
    }

    @Override public FluidStack drain(FluidStack request, FluidAction action) {
        if (request == null || request.isEmpty() || storage() == null) return FluidStack.EMPTY;
        try {
            ResourceKey key = FactoryFluidResources.keyOf(request, owner.getLevel().registryAccess());
            return drainKey(key, request.getAmount(), action);
        } catch (IllegalArgumentException | IllegalStateException exception) { return FluidStack.EMPTY; }
    }

    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        FactoryStorage storage = storage();
        if (storage == null) return FluidStack.EMPTY;
        for (ResourceKey key : storage.outputs().amounts().keySet()) {
            if (key.kind() == ResourceKind.FLUID) {
                try { return drainKey(key, maxDrain, action); }
                catch (IllegalArgumentException | IllegalStateException exception) { return FluidStack.EMPTY; }
            }
        }
        return FluidStack.EMPTY;
    }

    private FluidStack drainKey(ResourceKey key, int requested, FluidAction action) {
        FactoryStorage storage = storage();
        if (storage == null || key.kind() != ResourceKind.FLUID || requested <= 0) return FluidStack.EMPTY;
        BigInteger amount = storage.outputs().amount(key).min(BigInteger.valueOf(requested));
        if (amount.signum() == 0) return FluidStack.EMPTY;
        FluidStack result = FactoryFluidResources.fluidOf(key, owner.getLevel().registryAccess())
                .copyWithAmount(amount.intValueExact());
        owner.extractFluid(key, amount.intValueExact(), action.simulate());
        return result;
    }

    private ResourceKey resource(FactoryStorage storage, int tank) {
        if (!validTank(tank)) return null;
        List<ResourceKey> resources = (tank < INPUT_TANKS ? storage.inputs() : storage.outputs()).amounts().keySet()
                .stream().filter(key -> key.kind() == ResourceKind.FLUID).toList();
        int index = tank % INPUT_TANKS;
        return index < resources.size() ? resources.get(index) : null;
    }

    private boolean validTank(int tank) { return tank >= 0 && tank < getTanks(); }

    private FactoryStorage storage() {
        return FactoryAutomation.storage(owner);
    }
}
