package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.integration.mekanism.OptionalChemicalTank;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.menu.QuantumCoolingPluginMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

import static com.aquariverr.fluxod.block.entity.QuantumCoolingMath.ceilDiv;
import static com.aquariverr.fluxod.block.entity.QuantumCoolingMath.saturatingAdd;
import static com.aquariverr.fluxod.block.entity.QuantumCoolingMath.saturatingMultiply;

public final class TileQuantumAdvancedCoolingPlugin extends BlockEntity implements QuantumBatteryCoolingSource {

    private static final String FLUID_TANK_TAG = "FluidTank";
    private static final String CHEMICAL_TANK_TAG = "ChemicalTank";
    private static final String FLUID_CREDITS_TAG = "FluidCoolingCredits";
    private static final String CHEMICAL_CREDITS_TAG = "ChemicalCoolingCredits";
    private static final String FLUID_CHANNEL_PREFIX = "fluid:";
    private static final String CHEMICAL_CHANNEL_PREFIX = "chemical:";
    private static final long[] TARGETS = {
            QuantumCoolingMedia.TARGET_273_K,
            QuantumCoolingMedia.TARGET_263_K,
            QuantumCoolingMedia.TARGET_253_K
    };

    private final long[] fluidCoolingCredits = new long[TARGETS.length];
    private final long[] chemicalCoolingCredits = new long[TARGETS.length];
    private final QuantumCoolingTickBudget coolingTickBudget = new QuantumCoolingTickBudget();
    private final FluidTank fluidTank = new FluidTank(
            Math.max(1, Config.quantumCoolingFluidTankCapacity), QuantumCoolingMedia::isFluidCoolant) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler inputFluidHandler = new InputOnlyFluidHandler(fluidTank);
    private final OptionalChemicalTank chemicalTank = QuantumCoolingChemicalIntegration.create(
            Config.quantumCoolingChemicalTankCapacity, this::setChanged);

    public TileQuantumAdvancedCoolingPlugin(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_ADVANCED_COOLING_PLUGIN.get(), pos, state);
    }

    public FluidTank getFluidTank() {
        return fluidTank;
    }

    public IFluidHandler getInputFluidHandler() {
        return inputFluidHandler;
    }

    public OptionalChemicalTank getChemicalTank() {
        return chemicalTank;
    }

    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return;
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new QuantumCoolingPluginMenu(containerId, inventory, this),
                Component.translatable("screen.flux_overdrive.quantum_cooling.advanced_title"));
        serverPlayer.openMenu(provider, buffer -> QuantumCoolingPluginMenu.writeOpeningData(buffer, this));
    }

    public void openMenuFromEntanglement(ServerPlayer player,
                                         TileQuantumEntanglementController accessController,
                                         QuantumPluginEndpointKey endpointKey) {
        if (player.isSpectator()
                || !accessController.canProvideRemotePluginAccess(player, this, endpointKey)) {
            return;
        }
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new QuantumCoolingPluginMenu(
                        containerId, inventory, this, accessController, endpointKey),
                Component.translatable("screen.flux_overdrive.quantum_cooling.advanced_title"));
        player.openMenu(provider, buffer -> QuantumCoolingPluginMenu.writeRemoteOpeningData(
                buffer, this, accessController, endpointKey));
    }

    @Override
    public List<CoolingMedium> getCoolingMedia() {
        List<CoolingMedium> media = new ArrayList<>(TARGETS.length * 2);
        long fluidTarget = QuantumCoolingMedia.targetFor(fluidTank.getFluid());
        long chemicalTarget = chemicalTank.getTargetMilliKelvin();
        for (int i = 0; i < TARGETS.length; i++) {
            long fluidAvailable = QuantumCoolingConsumption.effectiveCooling(
                    storedFluidCoolingAvailable(i, fluidTarget));
            if (fluidAvailable > 0L) {
                media.add(new CoolingMedium(fluidChannel(TARGETS[i]), TARGETS[i], fluidAvailable));
            }

            long chemicalAvailable = QuantumCoolingConsumption.effectiveCooling(
                    storedChemicalCoolingAvailable(i, chemicalTarget));
            if (chemicalAvailable > 0L) {
                media.add(new CoolingMedium(chemicalChannel(TARGETS[i]), TARGETS[i], chemicalAvailable));
            }
        }
        return media;
    }

    @Override
    public long getMaxCoolingPerTickMilliKelvin() {
        return QuantumBatteryCoolingModel.milliKelvin(Config.quantumAdvancedCoolingRate);
    }

    @Override
    public long consumeCooling(String channelId, long requestedMilliKelvin) {
        if (requestedMilliKelvin <= 0L) return 0L;
        long requested = limitToTickBudget(requestedMilliKelvin);
        if (requested <= 0L) return 0L;

        long consumed;
        if (channelId.startsWith(FLUID_CHANNEL_PREFIX)) {
            consumed = consumeFluid(parseTarget(channelId, FLUID_CHANNEL_PREFIX), requested);
        } else if (channelId.startsWith(CHEMICAL_CHANNEL_PREFIX)) {
            consumed = consumeChemical(parseTarget(channelId, CHEMICAL_CHANNEL_PREFIX), requested);
        } else {
            return 0L;
        }
        recordTickConsumption(consumed);
        return consumed;
    }

    private long consumeFluid(long target, long requested) {
        int index = targetIndex(target);
        if (index < 0) return 0L;

        requested = Math.min(requested, QuantumCoolingConsumption.effectiveCooling(
                storedFluidCoolingAvailable(index, QuantumCoolingMedia.targetFor(fluidTank.getFluid()))));
        if (requested <= 0L) return 0L;

        long storedRequested = QuantumCoolingConsumption.storedCoolingFor(requested);
        long remaining = consumeCredit(fluidCoolingCredits, index, storedRequested);
        long coolingPerUnit = coolingPerFluidUnit();
        if (remaining > 0L && coolingPerUnit > 0L
                && QuantumCoolingMedia.targetFor(fluidTank.getFluid()) == target) {
            long unitsNeeded = ceilDiv(remaining, coolingPerUnit);
            int unitsToDrain = (int) Math.min(unitsNeeded, fluidTank.getFluidAmount());
            FluidStack drained = fluidTank.drain(unitsToDrain, IFluidHandler.FluidAction.EXECUTE);
            fluidCoolingCredits[index] = saturatingAdd(fluidCoolingCredits[index],
                    saturatingMultiply(drained.getAmount(), coolingPerUnit));
            remaining = consumeCredit(fluidCoolingCredits, index, remaining);
        }
        long consumed = QuantumCoolingConsumption.effectiveCooling(storedRequested - remaining);
        if (consumed > 0L) setChanged();
        return consumed;
    }

    private long consumeChemical(long target, long requested) {
        int index = targetIndex(target);
        if (index < 0) return 0L;

        requested = Math.min(requested, QuantumCoolingConsumption.effectiveCooling(
                storedChemicalCoolingAvailable(index, chemicalTank.getTargetMilliKelvin())));
        if (requested <= 0L) return 0L;

        long storedRequested = QuantumCoolingConsumption.storedCoolingFor(requested);
        long remaining = consumeCredit(chemicalCoolingCredits, index, storedRequested);
        long coolingPerUnit = coolingPerChemicalUnit();
        if (remaining > 0L && coolingPerUnit > 0L && chemicalTank.getTargetMilliKelvin() == target) {
            long unitsToExtract = Math.min(ceilDiv(remaining, coolingPerUnit), chemicalTank.getAmount());
            long extracted = chemicalTank.extract(unitsToExtract);
            chemicalCoolingCredits[index] = saturatingAdd(chemicalCoolingCredits[index],
                    saturatingMultiply(extracted, coolingPerUnit));
            remaining = consumeCredit(chemicalCoolingCredits, index, remaining);
        }
        long consumed = QuantumCoolingConsumption.effectiveCooling(storedRequested - remaining);
        if (consumed > 0L) setChanged();
        return consumed;
    }

    private static long consumeCredit(long[] credits, int index, long requested) {
        long consumed = Math.min(requested, credits[index]);
        credits[index] -= consumed;
        return requested - consumed;
    }

    private long limitToTickBudget(long requested) {
        long maximum = getMaxCoolingPerTickMilliKelvin();
        return level == null
                ? Math.min(requested, maximum)
                : coolingTickBudget.limit(level.getGameTime(), maximum, requested);
    }

    private void recordTickConsumption(long consumed) {
        if (level != null && consumed > 0L) {
            coolingTickBudget.record(level.getGameTime(), consumed);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(FLUID_TANK_TAG, fluidTank.writeToNBT(registries, new CompoundTag()));
        tag.put(CHEMICAL_TANK_TAG, chemicalTank.serializeNBT(registries));
        tag.putLongArray(FLUID_CREDITS_TAG, fluidCoolingCredits);
        tag.putLongArray(CHEMICAL_CREDITS_TAG, chemicalCoolingCredits);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(FLUID_TANK_TAG)) {
            fluidTank.readFromNBT(registries, tag.getCompound(FLUID_TANK_TAG));
        }
        if (tag.contains(CHEMICAL_TANK_TAG)) {
            chemicalTank.deserializeNBT(registries, tag.getCompound(CHEMICAL_TANK_TAG));
        }
        loadCredits(tag.getLongArray(FLUID_CREDITS_TAG), fluidCoolingCredits);
        loadCredits(tag.getLongArray(CHEMICAL_CREDITS_TAG), chemicalCoolingCredits);
    }

    private static long coolingPerFluidUnit() {
        return QuantumBatteryCoolingModel.milliKelvin(Config.quantumFluidCoolingPerMb);
    }

    private static long coolingPerChemicalUnit() {
        return QuantumBatteryCoolingModel.milliKelvin(Config.quantumChemicalCoolingPerUnit);
    }

    private long storedFluidCoolingAvailable(int targetIndex, long tankTarget) {
        long available = fluidCoolingCredits[targetIndex];
        if (tankTarget == TARGETS[targetIndex]) {
            available = saturatingAdd(available,
                    saturatingMultiply(fluidTank.getFluidAmount(), coolingPerFluidUnit()));
        }
        return available;
    }

    private long storedChemicalCoolingAvailable(int targetIndex, long tankTarget) {
        long available = chemicalCoolingCredits[targetIndex];
        if (tankTarget == TARGETS[targetIndex]) {
            available = saturatingAdd(available,
                    saturatingMultiply(chemicalTank.getAmount(), coolingPerChemicalUnit()));
        }
        return available;
    }

    private static String fluidChannel(long target) {
        return FLUID_CHANNEL_PREFIX + target;
    }

    private static String chemicalChannel(long target) {
        return CHEMICAL_CHANNEL_PREFIX + target;
    }

    private static long parseTarget(String channelId, String prefix) {
        try {
            return Long.parseLong(channelId.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return QuantumBatteryCoolingModel.NO_TARGET;
        }
    }

    private static int targetIndex(long target) {
        for (int i = 0; i < TARGETS.length; i++) {
            if (TARGETS[i] == target) return i;
        }
        return -1;
    }

    private static void loadCredits(long[] saved, long[] destination) {
        for (int i = 0; i < destination.length; i++) {
            destination[i] = i < saved.length ? Math.max(0L, saved[i]) : 0L;
        }
    }

    private record InputOnlyFluidHandler(IFluidHandler delegate) implements IFluidHandler {
        @Override
        public int getTanks() {
            return delegate.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return delegate.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return delegate.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return delegate.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return delegate.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
