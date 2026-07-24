package com.aquariverr.fluxod.block.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.device.SideTransfer;

import javax.annotation.Nonnull;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public class FEStorageHandler extends FluxConnectorHandler {

    private static final long SMALL_PRI_DIFF = 1000;
    static final byte BUFFER_UPDATE_PACKET = -2;

    private final Runnable onBufferChanged;
    private final LongSupplier externalTransferRemaining;
    private final LongConsumer recordExternalTransfer;
    private long mFEBuffer;
    private long mFECapacity;
    private long mDesired;
    private long mExternalChange;

    FEStorageHandler(long baseTransfer, long baseCapacity, Runnable onBufferChanged,
                     LongSupplier externalTransferRemaining, LongConsumer recordExternalTransfer) {
        this.onBufferChanged = onBufferChanged;
        this.externalTransferRemaining = externalTransferRemaining;
        this.recordExternalTransfer = recordExternalTransfer;
        mFECapacity = Math.max(0, baseCapacity);
        setLimit(baseTransfer);
    }

    void setFECapacity(long capacity) {
        mFECapacity = Math.max(0, capacity);
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
        mFEBuffer = Math.max(0, Math.min(energy, mFECapacity));
    }

    void addExternalChange(long delta) {
        mExternalChange = saturatedAdd(mExternalChange, delta);
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
        long previousBuffer = mFEBuffer;
        long space = mFECapacity - mFEBuffer;
        long toStore = Math.min(Math.min(Math.max(0, mBuffer), getLimit()), space);
        mFEBuffer += toStore;
        mBuffer -= toStore;

        long sent = sendToConsumers(Math.min(mFEBuffer, getLimit()), false);
        mFEBuffer -= sent;
        mChange = saturatedAdd(-sent, mExternalChange);
        mExternalChange = 0;
        if (mFEBuffer != previousBuffer) {
            onBufferChanged.run();
        }
    }

    @Override
    public void addToBuffer(long energy) {
        if (energy > 0) {
            mBuffer = saturatedAdd(mBuffer, energy);
        }
    }

    @Override
    public long getRequest() {
        long remaining = mFECapacity - mFEBuffer;
        long desiredGap = Math.max(mDesired - mFEBuffer, 0);
        long unfilled = Math.max(0, remaining - Math.min(remaining, Math.max(0, mBuffer)));
        return Math.min(getLimit(), saturatedAdd(unfilled, desiredGap));
    }

    @Override
    public int getPriority() {
        return super.getPriority() - (int) SMALL_PRI_DIFF;
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        setFECapacity(mFECapacity);
        setFEBuffer(mFEBuffer);
        setLimit(getRawLimit());
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_DROP) {
            mBuffer = 0;
            mExternalChange = 0;
        }
    }

    @Override
    public void writePacketBuffer(FriendlyByteBuf buffer, byte type) {
        if (type == BUFFER_UPDATE_PACKET) {
            buffer.writeLong(mFEBuffer);
        } else {
            super.writePacketBuffer(buffer, type);
        }
    }

    @Override
    public void readPacketBuffer(FriendlyByteBuf buffer, byte type) {
        if (type == BUFFER_UPDATE_PACKET) {
            setFEBuffer(buffer.readLong());
        } else {
            super.readPacketBuffer(buffer, type);
        }
    }

    private long sendToConsumers(long energy, boolean simulate) {
        long allowed = Math.min(energy, externalTransferRemaining.getAsLong());
        long leftover = allowed;
        for (SideTransfer transfer : mTransfers) {
            if (transfer != null) {
                long sent = Math.max(0, Math.min(leftover, transfer.send(leftover, simulate)));
                leftover -= sent;
                if (leftover <= 0) {
                    if (!simulate) recordExternalTransfer.accept(allowed);
                    return allowed;
                }
            }
        }
        long sent = allowed - leftover;
        if (!simulate && sent > 0) recordExternalTransfer.accept(sent);
        return sent;
    }

    private static long saturatedAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ignored) {
            return second >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }
}
