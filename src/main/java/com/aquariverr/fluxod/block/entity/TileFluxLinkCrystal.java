package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.api.energy.IBlockEnergyConnector;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.device.TileFluxConnector;
import sonar.fluxnetworks.common.util.EnergyUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ParametersAreNonnullByDefault
public class TileFluxLinkCrystal extends TileFluxConnector implements IFluxPoint {

    private static final String WIRELESS_LINKS_TAG = "wirelessLinks";

    private final WirelessPointHandler mHandler = new WirelessPointHandler();
    protected final List<LinkTarget> linkedTargets = new ArrayList<>();
    private final List<LinkTarget> linkedTargetsView = Collections.unmodifiableList(linkedTargets);

    public TileFluxLinkCrystal(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_LINK_CRYSTAL.get(), pos, state);
        mHandler.setLimit(Config.feStorageTransfer);
    }

    protected TileFluxLinkCrystal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        mHandler.setLimit(Config.feStorageTransfer);
    }

    @Nonnull
    @Override
    public FluxDeviceType getDeviceType() {
        return FluxDeviceType.POINT;
    }

    @Nonnull
    @Override
    public FluxConnectorHandler getTransferHandler() {
        return mHandler;
    }

    @Nullable
    @Override
    public <T> T getEnergyCapability(BlockCapability<T, Direction> cap, @Nullable Direction side) {
        return null;
    }

    @Nonnull
    @Override
    public ItemStack getDisplayStack() {
        return new ItemStack(getBlockState().getBlock());
    }

    @Override
    protected void onFirstTick() {
        connect(sonar.fluxnetworks.common.connection.FluxNetworkData.getNetwork(getNetworkID()));
    }

    @Override
    public void updateSideTransfer(Direction dir, @javax.annotation.Nullable BlockEntity neighbor) {
    }

    public boolean addLink(BlockPos targetPos, Direction side) {
        ServerLevel serverLevel = getLinkLevel();
        if (serverLevel == null) return false;
        LinkTarget target = new LinkTarget(GlobalPos.of(serverLevel.dimension(), targetPos), side);
        int index = findManualLink(target.pos());
        if (index < 0 && getLinkedTargets().size() >= getMaxLinks()) return false;
        if (index >= 0 && linkedTargets.get(index).equals(target)) return false;

        if (index >= 0) {
            linkedTargets.set(index, target);
        } else {
            linkedTargets.add(target);
        }
        linksChanged();
        return true;
    }

    public boolean removeLink(BlockPos targetPos) {
        ServerLevel serverLevel = getLinkLevel();
        if (serverLevel == null) return false;
        boolean changed = removeManualLink(GlobalPos.of(serverLevel.dimension(), targetPos));
        if (changed) linksChanged();
        return changed;
    }

    public boolean isLinked(BlockPos targetPos) {
        return level != null && findManualLink(GlobalPos.of(level.dimension(), targetPos)) >= 0;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInRange(BlockPos targetPos) {
        return true;
    }

    public List<LinkTarget> getLinkedTargets() {
        return linkedTargetsView;
    }

    public boolean isValidLinkTarget(BlockPos targetPos, Direction side) {
        if (level == null || targetPos.equals(worldPosition) || !level.isLoaded(targetPos)) return false;
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) return false;
        IBlockEnergyConnector connector = EnergyUtils.getConnector(target, side);
        return connector != null && connector.canSendTo(target, side);
    }

    protected int getMaxLinks() {
        return (int) Config.maxCrystalLinks;
    }

    @Nullable
    protected ServerLevel getLinkLevel() {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    protected int findManualLink(GlobalPos targetPos) {
        for (int i = 0; i < linkedTargets.size(); i++) {
            if (linkedTargets.get(i).pos().equals(targetPos)) return i;
        }
        return -1;
    }

    protected boolean removeManualLink(GlobalPos targetPos) {
        return linkedTargets.removeIf(target -> target.pos().equals(targetPos));
    }

    protected void linksChanged() {
        ServerLevel serverLevel = getLinkLevel();
        if (serverLevel == null) return;
        setChanged();
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        ListTag links = new ListTag();
        for (LinkTarget d : linkedTargets) {
            links.add(writeLinkToTag(d));
        }
        tag.put(WIRELESS_LINKS_TAG, links);
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        linkedTargets.clear();
        readLinksFromList(tag);
    }

    protected static CompoundTag writeLinkToTag(LinkTarget d) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(d.pos().pos()));
        tag.putString("dimension", d.pos().dimension().location().toString());
        tag.putString("side", d.side().getName());
        return tag;
    }

    protected void readLinksFromList(CompoundTag root) {
        if (!root.contains(WIRELESS_LINKS_TAG)) return;
        linkedTargets.clear();
        Set<GlobalPos> seen = new HashSet<>();
        ListTag links = root.getList(WIRELESS_LINKS_TAG, Tag.TAG_COMPOUND);
        int entriesToRead = Math.min(links.size(), getMaxLinks());
        for (int i = 0; i < entriesToRead && linkedTargets.size() < getMaxLinks(); i++) {
            LinkTarget target = readLinkFromTag(links.getCompound(i));
            if (target != null && seen.add(target.pos())) {
                linkedTargets.add(target);
            }
        }
    }

    @Nullable
    protected static LinkTarget readLinkFromTag(CompoundTag tag) {
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(null);
        if (pos == null) return null;
        ResourceLocation dimId = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimId == null) return null;
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        Direction side = Direction.byName(tag.getString("side"));
        if (side == null) return null;
        return new LinkTarget(GlobalPos.of(dim, pos), side);
    }

    public record LinkTarget(GlobalPos pos, Direction side) {
        @Nullable
        public BlockEntity getTarget(Level level) {
            if (level instanceof ServerLevel && pos.dimension().equals(level.dimension())) {
                BlockPos p = pos.pos();
                if (level.isLoaded(p)) {
                    return level.getBlockEntity(p);
                }
            }
            return null;
        }
    }

    public class WirelessPointHandler extends FluxConnectorHandler {

        private long mDesired;
        private int targetCursor;

        @Override
        public void onCycleStart() {
            mDesired = sendToWirelessTargets(getLimit(), true);
        }

        @Override
        public void onCycleEnd() {
            mBuffer += mChange = -sendToWirelessTargets(Math.min(mBuffer, getLimit()), false);
            int size = getLinkedTargets().size();
            if (size > 0) targetCursor = (targetCursor + 1) % size;
        }

        @Override
        public void addToBuffer(long energy) {
            mBuffer += energy;
        }

        @Override
        public long getRequest() {
            return Math.max(mDesired - mBuffer, 0);
        }

        private long sendToWirelessTargets(long energy, boolean simulate) {
            long leftover = energy;
            List<LinkTarget> targets = getLinkedTargets();
            int size = targets.size();
            for (int offset = 0; offset < size; offset++) {
                if (leftover <= 0) return energy;
                LinkTarget device = targets.get((targetCursor + offset) % size);
                GlobalPos pos = device.pos();
                if (level == null || !pos.dimension().equals(level.dimension())) continue;

                BlockEntity target = device.getTarget(level);
                if (target == null) continue;

                IBlockEnergyConnector connector = EnergyUtils.getConnector(target, device.side());
                if (connector == null || !connector.canSendTo(target, device.side())) continue;

                long sent = Math.max(0, Math.min(leftover,
                        connector.sendTo(leftover, target, device.side(), simulate)));
                leftover -= sent;
            }
            return energy - leftover;
        }
    }
}
