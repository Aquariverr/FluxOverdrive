package com.aquariverr.fluxod.mixin;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.util.ExactNetworkStatistics;
import com.aquariverr.fluxod.util.NetworkEnergyTotals;
import com.aquariverr.fluxod.util.NonNegativeBigIntegerCodec;
import com.aquariverr.fluxod.util.SaturatedLongMath;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.NetworkStatistics;
import sonar.fluxnetworks.common.device.FluxStorageHandler;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import java.util.List;

@Mixin(value = NetworkStatistics.class, remap = false)
public abstract class NetworkStatisticsMixin implements ExactNetworkStatistics {

    @Unique
    private static final int FLUXOD$NETWORK_ENERGY_BYTES = NonNegativeBigIntegerCodec.DEFAULT_MAX_BYTES + Long.BYTES;
    @Unique
    private static final String FLUXOD$EXACT_STORED_TAG = "flux_overdrive:exact_stored";
    @Unique
    private static final String FLUXOD$FINITE_CAPACITY_TAG = "flux_overdrive:finite_capacity";
    @Unique
    private static final String FLUXOD$CAPACITY_STATUS_TAG = "flux_overdrive:capacity_status";

    @Unique
    private NetworkEnergyTotals fluxod$energyTotals = NetworkEnergyTotals.builder().build();

    @Shadow
    @Final
    private FluxNetwork network;
    @Shadow
    private long energyChange5;
    @Shadow
    private long energyInput4;
    @Shadow
    private long energyOutput4;
    @Shadow
    private long runningTotalNano;
    @Shadow
    public int fluxPlugCount;
    @Shadow
    public int fluxPointCount;
    @Shadow
    public int fluxControllerCount;
    @Shadow
    public int fluxStorageCount;
    @Shadow
    public long energyInput;
    @Shadow
    public long energyOutput;
    @Shadow
    public LongList energyChange;
    @Shadow
    public long totalBuffer;
    @Shadow
    public long totalEnergy;
    @Shadow
    public int averageTickMicro;

    @Inject(method = "weakTick", at = @At("HEAD"), cancellable = true)
    private void fluxod$accumulateTransferSafely(CallbackInfo callback) {
        for (TileFluxDevice device : network.getLogicalDevices(FluxNetwork.PLUG)) {
            if (!device.getDeviceType().isStorage()) {
                energyInput4 = SaturatedLongMath.addNonNegative(
                        energyInput4, Math.max(0, device.getTransferChange()));
            }
        }
        for (TileFluxDevice device : network.getLogicalDevices(FluxNetwork.POINT)) {
            if (!device.getDeviceType().isStorage()) {
                energyOutput4 = SaturatedLongMath.addNonNegative(
                        energyOutput4, SaturatedLongMath.magnitudeOfNegative(device.getTransferChange()));
            }
        }
        callback.cancel();
    }

    @Inject(method = "weakerTick", at = @At("HEAD"), cancellable = true)
    private void fluxod$collectTotalsSafely(CallbackInfo callback) {
        totalBuffer = 0;
        totalEnergy = 0;
        List<TileFluxDevice> allDevices = network.getLogicalDevices(FluxNetwork.ANY);
        for (TileFluxDevice device : allDevices) {
            if (!device.getDeviceType().isStorage()) {
                totalBuffer = SaturatedLongMath.addNonNegative(totalBuffer, device.getTransferBuffer());
            }
        }

        List<TileFluxDevice> storages = network.getLogicalDevices(FluxNetwork.STORAGE);
        NetworkEnergyTotals.Builder energyTotals = NetworkEnergyTotals.builder();
        for (TileFluxDevice storage : storages) {
            if (storage instanceof TileQuantumBatteryController battery) {
                if (!battery.isOperational()) continue;
                battery.getCapacity().ifPresentOrElse(
                        capacity -> energyTotals.addFinite(battery.getStoredEnergy(), capacity),
                        () -> energyTotals.addUnlimited(battery.getStoredEnergy()));
                continue;
            }

            long stored = Math.max(0, storage.getTransferBuffer());
            if (storage.getTransferHandler() instanceof FluxStorageHandler storageHandler) {
                energyTotals.addFinite(stored, Math.max(0, storageHandler.getMaxEnergyStorage()));
            } else {
                energyTotals.addUnknown(stored);
            }
        }
        fluxod$energyTotals = energyTotals.build();
        totalEnergy = fluxod$energyTotals.storedAsSaturatedLong();

        fluxControllerCount = network.getLogicalDevices(FluxNetwork.CONTROLLER).size();
        fluxStorageCount = storages.size();
        fluxPlugCount = Math.max(0, network.getLogicalDevices(FluxNetwork.PLUG).size() - fluxStorageCount);
        fluxPointCount = Math.max(0, network.getLogicalDevices(FluxNetwork.POINT).size()
                - fluxStorageCount - fluxControllerCount);
        energyInput = energyInput4 == Long.MAX_VALUE ? Long.MAX_VALUE : energyInput4 / 4;
        energyOutput = energyOutput4 == Long.MAX_VALUE ? Long.MAX_VALUE : energyOutput4 / 4;
        energyInput4 = 0;
        energyOutput4 = 0;
        energyChange5 = SaturatedLongMath.addNonNegative(
                energyChange5, Math.max(energyInput, energyOutput));
        averageTickMicro = runningTotalNano < 0
                ? Integer.MAX_VALUE
                : (int) Math.min(runningTotalNano / 20_000L, Integer.MAX_VALUE);
        runningTotalNano = 0;
        callback.cancel();
    }

    @Inject(method = "weakestTick", at = @At("HEAD"), cancellable = true)
    private void fluxod$updateChartSafely(CallbackInfo callback) {
        for (int index = 1; index < energyChange.size(); index++) {
            energyChange.set(index - 1, energyChange.getLong(index));
        }
        long average = energyChange5 == Long.MAX_VALUE ? Long.MAX_VALUE : energyChange5 / 5;
        energyChange.set(energyChange.size() - 1, average);
        energyChange5 = 0;
        callback.cancel();
    }

    @Inject(method = "readNBT", at = @At("TAIL"))
    private void fluxod$sanitizeReceivedStatistics(CompoundTag tag, CallbackInfo callback) {
        if (energyInput < 0) energyInput = Long.MAX_VALUE;
        if (energyOutput < 0) energyOutput = Long.MAX_VALUE;
        if (totalBuffer < 0) totalBuffer = Long.MAX_VALUE;
        if (totalEnergy < 0) totalEnergy = Long.MAX_VALUE;
        if (averageTickMicro < 0) averageTickMicro = Integer.MAX_VALUE;
        for (int index = 0; index < energyChange.size(); index++) {
            if (energyChange.getLong(index) < 0) energyChange.set(index, Long.MAX_VALUE);
        }

        if (tag.contains(FLUXOD$EXACT_STORED_TAG, Tag.TAG_BYTE_ARRAY)
                && tag.contains(FLUXOD$FINITE_CAPACITY_TAG, Tag.TAG_BYTE_ARRAY)
                && tag.contains(FLUXOD$CAPACITY_STATUS_TAG, Tag.TAG_ANY_NUMERIC)) {
            fluxod$energyTotals = new NetworkEnergyTotals(
                    NonNegativeBigIntegerCodec.decode(tag.getByteArray(FLUXOD$EXACT_STORED_TAG),
                            FLUXOD$NETWORK_ENERGY_BYTES),
                    NonNegativeBigIntegerCodec.decode(tag.getByteArray(FLUXOD$FINITE_CAPACITY_TAG),
                            FLUXOD$NETWORK_ENERGY_BYTES),
                    NetworkEnergyTotals.CapacityStatus.byId(tag.getByte(FLUXOD$CAPACITY_STATUS_TAG)));
        } else {
            fluxod$energyTotals = NetworkEnergyTotals.fallback(totalEnergy);
        }
    }

    @Inject(method = "writeNBT", at = @At("TAIL"))
    private void fluxod$writeExactEnergy(CompoundTag tag, CallbackInfo callback) {
        tag.putByteArray(FLUXOD$EXACT_STORED_TAG,
                NonNegativeBigIntegerCodec.encode(fluxod$energyTotals.stored(), FLUXOD$NETWORK_ENERGY_BYTES));
        tag.putByteArray(FLUXOD$FINITE_CAPACITY_TAG,
                NonNegativeBigIntegerCodec.encode(fluxod$energyTotals.finiteCapacity(),
                        FLUXOD$NETWORK_ENERGY_BYTES));
        tag.putByte(FLUXOD$CAPACITY_STATUS_TAG, (byte) fluxod$energyTotals.capacityStatus().id());
    }

    @Override
    public NetworkEnergyTotals fluxod$getEnergyTotals() {
        return fluxod$energyTotals;
    }
}
