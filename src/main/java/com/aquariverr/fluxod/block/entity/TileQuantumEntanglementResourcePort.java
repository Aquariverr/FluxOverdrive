package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.entanglement.EntanglementResourceDirection;
import com.aquariverr.fluxod.entanglement.EntanglementResourceKind;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.block.QuantumEntanglementResourcePortBlock;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public final class TileQuantumEntanglementResourcePort extends BlockEntity {

    private static final ThreadLocal<Set<OperationKey>> ACTIVE_OPERATIONS = ThreadLocal.withInitial(
            HashSet::new);

    private final IItemHandler itemHandler = new ItemProxy();
    private final IFluidHandler fluidHandler = new FluidProxy();
    private final Object chemicalHandler = QuantumCoolingChemicalIntegration.createEntanglementProxy(this);

    public TileQuantumEntanglementResourcePort(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_ENTANGLEMENT_RESOURCE_PORT.get(), pos, state);
    }

    /**
     * Performs the active side of the port contract. Capability exposure remains
     * useful for pipes, but the port also initiates one bounded transfer each tick
     * through its outward-facing side.
     */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) return;
        EntanglementResourceDirection direction = getResourceDirection();
        if (direction == null) return;

        Direction facing = getBlockState().getValue(QuantumEntanglementResourcePortBlock.FACING);
        BlockPos neighborPos = worldPosition.relative(facing);
        if (!serverLevel.isLoaded(neighborPos)) return;
        Direction neighborSide = facing.getOpposite();

        tickItems(serverLevel, neighborPos, neighborSide, direction);
        tickFluids(serverLevel, neighborPos, neighborSide, direction);
        QuantumCoolingChemicalIntegration.tickEntanglementTransfer(this, neighborPos, neighborSide);
    }

    private void tickItems(ServerLevel serverLevel, BlockPos neighborPos, Direction neighborSide,
                           EntanglementResourceDirection direction) {
        IItemHandler neighbor = serverLevel.getCapability(
                Capabilities.ItemHandler.BLOCK, neighborPos, neighborSide);
        if (neighbor == null) return;
        if (direction == EntanglementResourceDirection.INPUT) {
            withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    remote -> transferItems(remote, neighbor, Config.entanglementMaxItemTransfer), 0);
        } else {
            withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    remote -> transferItems(neighbor, remote, Config.entanglementMaxItemTransfer), 0);
        }
    }

    private void tickFluids(ServerLevel serverLevel, BlockPos neighborPos, Direction neighborSide,
                            EntanglementResourceDirection direction) {
        IFluidHandler neighbor = serverLevel.getCapability(
                Capabilities.FluidHandler.BLOCK, neighborPos, neighborSide);
        if (neighbor == null) return;
        if (direction == EntanglementResourceDirection.INPUT) {
            withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    remote -> FluidUtil.tryFluidTransfer(remote, neighbor,
                            Config.entanglementMaxFluidTransfer, true), FluidStack.EMPTY);
        } else {
            withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    remote -> FluidUtil.tryFluidTransfer(neighbor, remote,
                            Config.entanglementMaxFluidTransfer, true), FluidStack.EMPTY);
        }
    }

    private static int transferItems(IItemHandler destination, IItemHandler source, int maxAmount) {
        int remaining = Math.max(0, maxAmount);
        int moved = 0;
        for (int slot = 0; slot < source.getSlots() && remaining > 0; slot++) {
            ItemStack simulated = source.extractItem(slot, remaining, true);
            if (simulated.isEmpty()) continue;
            ItemStack remainder = ItemHandlerHelper.insertItem(destination, simulated, true);
            int accepted = simulated.getCount() - remainder.getCount();
            if (accepted <= 0) continue;

            ItemStack extracted = source.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) continue;
            ItemStack insertionRemainder = ItemHandlerHelper.insertItem(destination, extracted, false);
            int inserted = extracted.getCount() - insertionRemainder.getCount();
            if (!insertionRemainder.isEmpty()) {
                // A compliant handler should match its simulation result. Try to
                // restore any unexpected remainder before proceeding.
                ItemHandlerHelper.insertItem(source, insertionRemainder, false);
            }
            moved += inserted;
            remaining -= inserted;
        }
        return moved;
    }

    @Nullable
    public IItemHandler getItemCapability(@Nullable Direction side) {
        return isRemoved() ? null : itemHandler;
    }

    @Nullable
    public IFluidHandler getFluidCapability(@Nullable Direction side) {
        return isRemoved() ? null : fluidHandler;
    }

    @Nullable
    public Object getChemicalCapability(@Nullable Direction side) {
        return isRemoved() ? null : chemicalHandler;
    }

    @Nullable
    public EntanglementResourceDirection getResourceDirection() {
        BlockState state = getBlockState();
        if (state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get())) {
            return EntanglementResourceDirection.INPUT;
        }
        if (state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get())) {
            return EntanglementResourceDirection.OUTPUT;
        }
        return null;
    }

    public <T, R> R withResolvedCapability(EntanglementResourceKind resourceKind,
                                           BlockCapability<T, Direction> capability,
                                           Function<T, R> operation, R unavailableResult) {
        EntanglementResourceDirection direction = getResourceDirection();
        if (direction == null || isRemoved()) return unavailableResult;
        OperationKey operationKey = new OperationKey(this, resourceKind);
        Set<OperationKey> active = ACTIVE_OPERATIONS.get();
        if (!active.add(operationKey)) return unavailableResult;
        try {
            TileQuantumEntanglementController controller = getController();
            if (controller == null) return unavailableResult;
            T delegate = controller.resolveSelectedCapability(resourceKind, direction, capability);
            return delegate == null ? unavailableResult : operation.apply(delegate);
        } finally {
            active.remove(operationKey);
            if (active.isEmpty()) ACTIVE_OPERATIONS.remove();
        }
    }

    @Nullable
    private TileQuantumEntanglementController getController() {
        return level == null
                ? null
                : TileQuantumEntanglementController.findUniqueControllerForPort(level, worldPosition);
    }

    private final class ItemProxy implements IItemHandler {
        @Override
        public int getSlots() {
            return withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    IItemHandler::getSlots, 0);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    delegate -> validSlot(delegate, slot) ? delegate.getStackInSlot(slot) : ItemStack.EMPTY,
                    ItemStack.EMPTY);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (getResourceDirection() != EntanglementResourceDirection.INPUT) return stack;
            return withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    delegate -> validSlot(delegate, slot)
                            ? boundedInsert(delegate, slot, stack, simulate)
                            : stack,
                    stack);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (getResourceDirection() != EntanglementResourceDirection.OUTPUT) return ItemStack.EMPTY;
            return withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    delegate -> validSlot(delegate, slot)
                            ? delegate.extractItem(slot,
                            Math.min(Math.max(0, amount), Config.entanglementMaxItemTransfer), simulate)
                            : ItemStack.EMPTY,
                    ItemStack.EMPTY);
        }

        @Override
        public int getSlotLimit(int slot) {
            return withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    delegate -> validSlot(delegate, slot) ? delegate.getSlotLimit(slot) : 0, 0);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (getResourceDirection() != EntanglementResourceDirection.INPUT) return false;
            return withResolvedCapability(EntanglementResourceKind.ITEM, Capabilities.ItemHandler.BLOCK,
                    delegate -> validSlot(delegate, slot) && delegate.isItemValid(slot, stack), false);
        }

        private boolean validSlot(IItemHandler delegate, int slot) {
            return slot >= 0 && slot < delegate.getSlots();
        }

        private ItemStack boundedInsert(IItemHandler delegate, int slot,
                                        ItemStack stack, boolean simulate) {
            int requested = Math.min(stack.getCount(), Config.entanglementMaxItemTransfer);
            if (requested <= 0) return stack;
            ItemStack bounded = stack.copyWithCount(requested);
            ItemStack boundedRemainder = delegate.insertItem(slot, bounded, simulate);
            int accepted = requested - boundedRemainder.getCount();
            if (accepted <= 0) return stack;
            ItemStack totalRemainder = stack.copy();
            totalRemainder.shrink(accepted);
            return totalRemainder;
        }
    }

    private final class FluidProxy implements IFluidHandler {
        @Override
        public int getTanks() {
            return withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    IFluidHandler::getTanks, 0);
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    delegate -> validTank(delegate, tank) ? delegate.getFluidInTank(tank) : FluidStack.EMPTY,
                    FluidStack.EMPTY);
        }

        @Override
        public int getTankCapacity(int tank) {
            return withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    delegate -> validTank(delegate, tank) ? delegate.getTankCapacity(tank) : 0, 0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (getResourceDirection() != EntanglementResourceDirection.INPUT) return false;
            return withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    delegate -> validTank(delegate, tank) && delegate.isFluidValid(tank, stack), false);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (getResourceDirection() != EntanglementResourceDirection.INPUT) return 0;
            return withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    delegate -> {
                        int requested = Math.min(resource.getAmount(), Config.entanglementMaxFluidTransfer);
                        return requested <= 0 ? 0
                                : delegate.fill(resource.copyWithAmount(requested), action);
                    }, 0);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (getResourceDirection() != EntanglementResourceDirection.OUTPUT) return FluidStack.EMPTY;
            return withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    delegate -> {
                        int requested = Math.min(resource.getAmount(), Config.entanglementMaxFluidTransfer);
                        return requested <= 0 ? FluidStack.EMPTY
                                : delegate.drain(resource.copyWithAmount(requested), action);
                    }, FluidStack.EMPTY);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (getResourceDirection() != EntanglementResourceDirection.OUTPUT) return FluidStack.EMPTY;
            return withResolvedCapability(EntanglementResourceKind.FLUID, Capabilities.FluidHandler.BLOCK,
                    delegate -> delegate.drain(
                            Math.min(Math.max(0, maxDrain), Config.entanglementMaxFluidTransfer), action),
                    FluidStack.EMPTY);
        }

        private boolean validTank(IFluidHandler delegate, int tank) {
            return tank >= 0 && tank < delegate.getTanks();
        }
    }

    private record OperationKey(TileQuantumEntanglementResourcePort port,
                                EntanglementResourceKind resourceKind) {}
}
