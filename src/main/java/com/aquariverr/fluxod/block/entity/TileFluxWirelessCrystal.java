package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
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
import sonar.fluxnetworks.api.energy.IBlockEnergyConnector;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.device.FluxPlugHandler;
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
public abstract class TileFluxWirelessCrystal extends TileFluxConnector {

    private static final String WIRELESS_LINKS_TAG = "wirelessLinks";

    protected final List<LinkTarget> linkedTargets = new ArrayList<>();
    private final List<LinkTarget> linkedTargetsView = Collections.unmodifiableList(linkedTargets);

    protected TileFluxWirelessCrystal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
    public void updateSideTransfer(Direction dir, @Nullable BlockEntity neighbor) {
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
        return target != null && canTransferWith(target, side);
    }

    public String getInvalidLinkTargetMessageKey() {
        return "message.flux_overdrive.invalid_link_target";
    }

    protected abstract boolean canTransferWith(BlockEntity target, Direction side);

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

    protected final WirelessPointHandler createPointHandler() {
        WirelessPointHandler handler = new WirelessPointHandler();
        handler.setLimit(Config.feStorageTransfer);
        return handler;
    }

    protected final WirelessPlugHandler createPlugHandler() {
        WirelessPlugHandler handler = new WirelessPlugHandler();
        handler.setLimit(Config.feStorageTransfer);
        return handler;
    }

    @Override
    public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        ListTag links = new ListTag();
        for (LinkTarget target : linkedTargets) links.add(writeLinkToTag(target));
        tag.put(WIRELESS_LINKS_TAG, links);
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        linkedTargets.clear();
        readLinksFromList(tag);
    }

    protected static CompoundTag writeLinkToTag(LinkTarget target) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(target.pos().pos()));
        tag.putString("dimension", target.pos().dimension().location().toString());
        tag.putString("side", target.side().getName());
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
            if (target != null && seen.add(target.pos())) linkedTargets.add(target);
        }
    }

    @Nullable
    protected static LinkTarget readLinkFromTag(CompoundTag tag) {
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(null);
        if (pos == null) return null;
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimensionId == null) return null;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        Direction side = Direction.byName(tag.getString("side"));
        if (side == null) return null;
        return new LinkTarget(GlobalPos.of(dimension, pos), side);
    }

    public record LinkTarget(GlobalPos pos, Direction side) {
        @Nullable
        public BlockEntity getTarget(Level level) {
            if (level instanceof ServerLevel && pos.dimension().equals(level.dimension())) {
                BlockPos targetPos = pos.pos();
                if (level.isLoaded(targetPos)) return level.getBlockEntity(targetPos);
            }
            return null;
        }
    }

    protected final class WirelessPointHandler extends FluxConnectorHandler {

        private long desired;
        private int targetCursor;

        @Override
        public void onCycleStart() {
            desired = sendToWirelessTargets(getLimit(), true);
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
            return Math.max(desired - mBuffer, 0);
        }

        private long sendToWirelessTargets(long energy, boolean simulate) {
            long leftover = energy;
            List<LinkTarget> targets = getLinkedTargets();
            int size = targets.size();
            for (int offset = 0; offset < size; offset++) {
                if (leftover <= 0) return energy;
                LinkTarget link = targets.get((targetCursor + offset) % size);
                if (level == null || !link.pos().dimension().equals(level.dimension())) continue;

                BlockEntity target = link.getTarget(level);
                if (target == null) continue;
                IBlockEnergyConnector connector = EnergyUtils.getConnector(target, link.side());
                if (connector == null || !connector.canSendTo(target, link.side())) continue;

                long sent = Math.max(0, Math.min(leftover,
                        connector.sendTo(leftover, target, link.side(), simulate)));
                leftover -= sent;
            }
            return energy - leftover;
        }
    }

    protected final class WirelessPlugHandler extends FluxPlugHandler {

        private int targetCursor;

        @Override
        public void onCycleEnd() {
            super.onCycleEnd();
            int size = getLinkedTargets().size();
            if (size > 0) targetCursor = (targetCursor + 1) % size;
        }

        protected void pullFromTargets() {
            if (!getNetwork().isValid()) return;
            long networkLimiter = getNetwork().getBufferLimiter();
            List<LinkTarget> targets = getLinkedTargets();
            int size = targets.size();
            for (int offset = 0; offset < size; offset++) {
                LinkTarget link = targets.get((targetCursor + offset) % size);
                if (level == null || !link.pos().dimension().equals(level.dimension())) continue;

                long acceptedLimit = Math.max(0,
                        receive(Long.MAX_VALUE, link.side(), true, networkLimiter));
                if (acceptedLimit <= 0) return;

                BlockEntity target = link.getTarget(level);
                if (target == null) continue;
                long extracted = WirelessEnergyExtractor.extract(target, link.side(), acceptedLimit);
                if (extracted <= 0) continue;

                long accepted = receive(extracted, link.side(), false, networkLimiter);
                if (accepted != extracted) {
                    throw new IllegalStateException("Flux plug buffer accepted less energy than its simulation");
                }
            }
        }
    }
}
