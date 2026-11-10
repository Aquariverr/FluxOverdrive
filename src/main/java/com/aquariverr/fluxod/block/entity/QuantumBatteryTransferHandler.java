package com.aquariverr.fluxod.block.entity;

import net.minecraft.nbt.CompoundTag;
import sonar.fluxnetworks.common.connection.TransferHandler;

import javax.annotation.Nonnull;

public final class QuantumBatteryTransferHandler extends TransferHandler {

    private static final int STORAGE_PRIORITY_OFFSET = 1_000_000;
    private static final String DATA_VERSION_TAG = "QuantumBatteryTransferVersion";
    private static final int DATA_VERSION = 2;

    private final TileQuantumBatteryController controller;
    private long added;
    private long removed;

    QuantumBatteryTransferHandler(TileQuantumBatteryController controller) {
        super(Long.MAX_VALUE);
        this.controller = controller;
    }

    @Override
    public void onCycleStart() {
        added = 0;
        removed = 0;
        mChange = 0;
        controller.refreshStabilityForCurrentTick();
        mBuffer = controller.isOperational()
                ? controller.getEnergyLedger().availableAsLong(getLimit())
                : 0;
    }

    @Override
    public void onCycleEnd() {
        if (!controller.isOperational()) {
            clearWindow();
            return;
        }
        if (added > 0 || removed > 0) {
            controller.applyNetworkTransfer(added, removed);
        }
        mChange = added - removed;
        mBuffer = controller.isOperational()
                ? controller.getEnergyLedger().availableAsLong(getLimit())
                : 0;
    }

    @Override
    public void addToBuffer(long energy) {
        if (energy <= 0 || !controller.isOperational()) return;
        added = saturatedAdd(added, energy);
        mBuffer = saturatedAdd(mBuffer, energy);
    }

    @Override
    public long removeFromBuffer(long energy) {
        if (energy <= 0 || !controller.isOperational()) return 0;
        long extracted = Math.min(Math.min(energy, mBuffer), remainingBudget());
        mBuffer -= extracted;
        removed += extracted;
        return extracted;
    }

    @Override
    public long getRequest() {
        if (!controller.isOperational()) return 0;
        long remaining = remainingBudget();
        return controller.getEnergyLedger().spaceAfterPendingAsLong(remaining, added, removed);
    }

    @Override
    public int getPriority() {
        return super.getPriority() - STORAGE_PRIORITY_OFFSET;
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        tag.putInt(DATA_VERSION_TAG, DATA_VERSION);
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        boolean legacyLimit = !tag.contains(DATA_VERSION_TAG);
        super.readCustomTag(tag, type);
        if (legacyLimit) setLimit(Long.MAX_VALUE);
        mBuffer = 0;
        added = 0;
        removed = 0;
    }

    void clearWindow() {
        mBuffer = 0;
        mChange = 0;
        added = 0;
        removed = 0;
    }

    private long remainingBudget() {
        long limit = getLimit();
        if (added >= limit || removed >= limit - added) return 0;
        return limit - added - removed;
    }

    private static long saturatedAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
