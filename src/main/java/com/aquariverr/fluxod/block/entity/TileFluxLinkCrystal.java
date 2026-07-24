package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.NotNull;
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
import java.util.List;

@ParametersAreNonnullByDefault
public class TileFluxLinkCrystal extends TileFluxConnector implements IFluxPoint {

    private final WirelessPointHandler mHandler = new WirelessPointHandler();
    private final List<LinkTarget> linkedTargets = new ArrayList<>();

    public TileFluxLinkCrystal(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_LINK_CRYSTAL.get(), pos, state);
        mHandler.setLimit(Config.feStorageTransfer);
        mHandler.setCapacity(Config.feStorageCapacity);
    }

    protected TileFluxLinkCrystal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        mHandler.setLimit(Config.feStorageTransfer);
        mHandler.setCapacity(Config.feStorageCapacity);
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

    public void addLink(BlockPos targetPos, Direction side) {
        if (level == null) return;
        removeLink(targetPos);
        linkedTargets.add(new LinkTarget(GlobalPos.of(level.dimension(), targetPos), side));
        if (!level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void removeLink(BlockPos targetPos) {
        linkedTargets.removeIf(d -> d.pos().pos().equals(targetPos));
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isLinked(BlockPos targetPos) {
        for (LinkTarget d : linkedTargets) {
            if (d.pos().pos().equals(targetPos)) return true;
        }
        return false;
    }

    public boolean isInRange(BlockPos targetPos) {
        return true;
    }

    public List<LinkTarget> getLinkedTargets() {
        return linkedTargets;
    }

    @Override
    public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        ListTag links = new ListTag();
        for (LinkTarget d : linkedTargets) {
            links.add(writeLinkToTag(d));
        }
        tag.put("wirelessLinks", links);
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        linkedTargets.clear();
        readLinksFromList(tag, "wirelessLinks");
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        ListTag links = new ListTag();
        for (LinkTarget d : linkedTargets) {
            links.add(writeLinkToTag(d));
        }
        if (!links.isEmpty()) {
            CompoundTag linkTag = new CompoundTag();
            linkTag.put("links", links);
            builder.set(FluxOdDataComponents.LINK_TARGETS, CustomData.of(linkTag));
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        CustomData data = input.get(FluxOdDataComponents.LINK_TARGETS);
        if (data != null) {
            readLinksFromList(data.copyTag(), "links");
        }
    }

    private CompoundTag writeLinkToTag(LinkTarget d) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(d.pos().pos()));
        tag.putString("dimension", d.pos().dimension().location().toString());
        tag.putString("side", d.side().getName());
        return tag;
    }

    private void readLinksFromList(CompoundTag root, String key) {
        if (!root.contains(key)) return;
        linkedTargets.clear();
        ListTag links = root.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            LinkTarget target = readLinkFromTag(links.getCompound(i));
            if (target != null) {
                linkedTargets.add(target);
            }
        }
    }

    @Nullable
    private LinkTarget readLinkFromTag(CompoundTag tag) {
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(null);
        if (pos == null) return null;
        ResourceLocation dimId = ResourceLocation.parse(tag.getString("dimension"));
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        Direction side = Direction.byName(tag.getString("side"));
        if (side == null) return null;
        return new LinkTarget(GlobalPos.of(dim, pos), side);
    }

    public record LinkTarget(GlobalPos pos, Direction side) {
        @Nullable
        public BlockEntity getTarget(Level level) {
            if (level instanceof ServerLevel && pos.dimension() == level.dimension()) {
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
        private long mCapacity;

        public void setCapacity(long capacity) {
            mCapacity = capacity;
        }

        public long getCapacity() {
            return mCapacity;
        }

        @Override
        public void onCycleStart() {
            mDesired = sendToWirelessTargets(getLimit(), true);
        }

        @Override
        public void onCycleEnd() {
            mBuffer += mChange = -sendToWirelessTargets(Math.min(mBuffer, getLimit()), false);
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
            for (LinkTarget device : linkedTargets) {
                if (leftover <= 0) return energy;
                GlobalPos pos = device.pos();
                if (pos == null) continue;
                if (level == null || pos.dimension() != level.dimension()) continue;

                BlockEntity target = device.getTarget(level);
                if (target == null) continue;

                IBlockEnergyConnector connector = EnergyUtils.getConnector(target, device.side());
                if (connector == null || !connector.canSendTo(target, device.side())) continue;

                long sent = connector.sendTo(leftover, target, device.side(), simulate);
                leftover -= sent;
            }
            return energy - leftover;
        }
    }
}