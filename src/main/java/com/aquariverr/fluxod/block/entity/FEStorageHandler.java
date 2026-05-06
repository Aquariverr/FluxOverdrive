package com.aquariverr.fluxod.block.entity;

import net.minecraft.nbt.CompoundTag;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.device.SideTransfer;

import javax.annotation.Nonnull;

public class FEStorageHandler extends FluxConnectorHandler {

    private static final long SMALL_PRI_DIFF = 1000;

    private long mFEBuffer;
    private long mFECapacity;
    private long mDesired;
    private long mExternalChange;

    FEStorageHandler(long baseTransfer, long baseCapacity) {
        setLimit(baseTransfer);
        mFECapacity = baseCapacity;
    }

    void setFECapacity(long capacity) {
        mFECapacity = capacity;
        if (mFEBuffer > mFECapacity) {
            mFEBuffer = mFECapacity;
        }
    }

    long getFEBuffer() {
        return mFEBuffer;
    }

    long getFECapacity() {
        return mFECapacity;
    }

    void setFEBuffer(long energy) {
        mFEBuffer = Math.min(energy, mFECapacity);
    }

    void addExternalChange(long delta) {
        mExternalChange += delta;
    }

    void clearNetworkBuffer() {
        mBuffer = 0;
    }

    @Override
    public void onCycleStart() {
        super.onCycleStart();
        mDesired = sendToConsumers(Math.min(mFEBuffer, getLimit()), true);
    }

    @Override
    public void onCycleEnd() {
        long space = mFECapacity - mFEBuffer;
        long toStore = Math.min(mBuffer, space);
        mFEBuffer += toStore;
        mBuffer -= toStore;

        long sent = sendToConsumers(Math.min(mFEBuffer, getLimit()), false);
        mFEBuffer -= sent;
        mChange = -sent + mExternalChange;
        mExternalChange = 0;
    }

    @Override
    public void addToBuffer(long energy) {
        mBuffer += energy;
    }

    @Override
    public long getRequest() {
        long remaining = mFECapacity - mFEBuffer;
        long desiredGap = Math.max(mDesired - mFEBuffer, 0);
        return Math.max(0, remaining - mBuffer + desiredGap);
    }

    @Override
    public int getPriority() {
        return super.getPriority() - (int) SMALL_PRI_DIFF;
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_DROP) {
            mBuffer = 0;
            mExternalChange = 0;
        }
    }

    private long sendToConsumers(long energy, boolean simulate) {
        long leftover = energy;
        for (SideTransfer transfer : mTransfers) {
            if (transfer != null) {
                leftover -= transfer.send(leftover, simulate);
                if (leftover <= 0) {
                    return energy;
                }
            }
        }
        return energy - leftover;
    }
}
