package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementResourcePort;
import com.aquariverr.fluxod.entanglement.EntanglementResourceDirection;
import com.aquariverr.fluxod.entanglement.EntanglementResourceKind;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.function.Function;

final class MekanismEntanglementChemicalHandler implements IChemicalHandler {

    private static final BlockCapability<IChemicalHandler, Direction> BLOCK_CAPABILITY =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(
                    "mekanism", "chemical_handler"), IChemicalHandler.class);

    private final TileQuantumEntanglementResourcePort port;

    MekanismEntanglementChemicalHandler(TileQuantumEntanglementResourcePort port) {
        this.port = port;
    }

    static void registerCapability(RegisterCapabilitiesEvent event,
                                   BlockEntityType<TileQuantumEntanglementResourcePort> blockEntityType) {
        event.registerBlockEntity(BLOCK_CAPABILITY, blockEntityType,
                (blockEntity, side) -> (IChemicalHandler) blockEntity.getChemicalCapability(side));
    }

    static void tickAutomaticTransfer(TileQuantumEntanglementResourcePort port,
                                      BlockPos neighborPos, Direction neighborSide) {
        Level level = port.getLevel();
        if (level == null || level.isClientSide) return;
        IChemicalHandler neighbor = level.getCapability(BLOCK_CAPABILITY, neighborPos, neighborSide);
        if (neighbor == null) return;

        EntanglementResourceDirection direction = port.getResourceDirection();
        if (direction == EntanglementResourceDirection.INPUT) {
            port.withResolvedCapability(EntanglementResourceKind.CHEMICAL, BLOCK_CAPABILITY,
                    remote -> transfer(remote, neighbor, Config.entanglementMaxChemicalTransfer), 0L);
        } else if (direction == EntanglementResourceDirection.OUTPUT) {
            port.withResolvedCapability(EntanglementResourceKind.CHEMICAL, BLOCK_CAPABILITY,
                    remote -> transfer(neighbor, remote, Config.entanglementMaxChemicalTransfer), 0L);
        }
    }

    private static long transfer(IChemicalHandler destination, IChemicalHandler source, long maxAmount) {
        ChemicalStack simulated = source.extractChemical(maxAmount, Action.SIMULATE);
        if (simulated.isEmpty()) return 0L;
        ChemicalStack simulatedRemainder = destination.insertChemical(simulated, Action.SIMULATE);
        long accepted = simulated.getAmount() - simulatedRemainder.getAmount();
        if (accepted <= 0L) return 0L;

        ChemicalStack extracted = source.extractChemical(accepted, Action.EXECUTE);
        if (extracted.isEmpty()) return 0L;
        ChemicalStack insertionRemainder = destination.insertChemical(extracted, Action.EXECUTE);
        long inserted = extracted.getAmount() - insertionRemainder.getAmount();
        if (!insertionRemainder.isEmpty()) {
            // Restore an unexpected execution-time remainder to avoid silently
            // discarding chemicals from a non-conforming handler.
            source.insertChemical(insertionRemainder, Action.EXECUTE);
        }
        return inserted;
    }

    @Override
    public int getChemicalTanks() {
        return withHandler(IChemicalHandler::getChemicalTanks, 0);
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return withHandler(delegate -> validTank(delegate, tank)
                ? delegate.getChemicalInTank(tank)
                : ChemicalStack.EMPTY, ChemicalStack.EMPTY);
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        // Direct mutation is not a transfer operation and is never exposed through the proxy.
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return withHandler(delegate -> validTank(delegate, tank)
                ? delegate.getChemicalTankCapacity(tank)
                : 0L, 0L);
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        if (!isInput()) return false;
        return withHandler(delegate -> validTank(delegate, tank) && delegate.isValid(tank, stack), false);
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (!isInput()) return stack;
        return withHandler(delegate -> validTank(delegate, tank)
                ? insertBounded(delegate, tank, stack, action)
                : stack, stack);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        if (!isOutput()) return ChemicalStack.EMPTY;
        return withHandler(delegate -> validTank(delegate, tank)
                ? delegate.extractChemical(tank,
                Math.min(Math.max(0L, amount), Config.entanglementMaxChemicalTransfer), action)
                : ChemicalStack.EMPTY, ChemicalStack.EMPTY);
    }

    @Override
    public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        if (!isInput()) return stack;
        return withHandler(delegate -> insertBounded(delegate, -1, stack, action), stack);
    }

    @Override
    public ChemicalStack extractChemical(long amount, Action action) {
        if (!isOutput()) return ChemicalStack.EMPTY;
        return withHandler(delegate -> delegate.extractChemical(
                Math.min(Math.max(0L, amount), Config.entanglementMaxChemicalTransfer), action),
                ChemicalStack.EMPTY);
    }

    @Override
    public ChemicalStack extractChemical(ChemicalStack stack, Action action) {
        if (!isOutput()) return ChemicalStack.EMPTY;
        return withHandler(delegate -> {
            long requested = Math.min(stack.getAmount(), Config.entanglementMaxChemicalTransfer);
            return requested <= 0L ? ChemicalStack.EMPTY
                    : delegate.extractChemical(stack.copyWithAmount(requested), action);
        }, ChemicalStack.EMPTY);
    }

    private boolean isInput() {
        return port.getResourceDirection() == EntanglementResourceDirection.INPUT;
    }

    private boolean isOutput() {
        return port.getResourceDirection() == EntanglementResourceDirection.OUTPUT;
    }

    private <R> R withHandler(Function<IChemicalHandler, R> operation, R unavailableResult) {
        return port.withResolvedCapability(EntanglementResourceKind.CHEMICAL, BLOCK_CAPABILITY,
                operation, unavailableResult);
    }

    private static ChemicalStack insertBounded(IChemicalHandler delegate, int tank,
                                               ChemicalStack stack, Action action) {
        long requested = Math.min(stack.getAmount(), Config.entanglementMaxChemicalTransfer);
        if (requested <= 0L) return stack;
        ChemicalStack bounded = stack.copyWithAmount(requested);
        ChemicalStack boundedRemainder = tank < 0
                ? delegate.insertChemical(bounded, action)
                : delegate.insertChemical(tank, bounded, action);
        long accepted = requested - boundedRemainder.getAmount();
        if (accepted <= 0L) return stack;
        long remaining = stack.getAmount() - accepted;
        return remaining <= 0L ? ChemicalStack.EMPTY : stack.copyWithAmount(remaining);
    }

    private static boolean validTank(IChemicalHandler handler, int tank) {
        return tank >= 0 && tank < handler.getChemicalTanks();
    }
}
