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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.api.energy.IBlockEnergyConnector;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.util.EnergyUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ParametersAreNonnullByDefault
public class TileFluxAutoLinkCrystal extends TileFluxLinkCrystal implements IFluxPoint {

    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_CRYSTALS = new ConcurrentHashMap<>();

    public static void registerCrystal(ResourceKey<Level> dim, BlockPos pos) {
        ACTIVE_CRYSTALS.computeIfAbsent(dim, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(pos);
    }

    public static void unregisterCrystal(ResourceKey<Level> dim, BlockPos pos) {
        Set<BlockPos> set = ACTIVE_CRYSTALS.get(dim);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) {
                ACTIVE_CRYSTALS.remove(dim);
            }
        }
    }

    public static Set<BlockPos> getActiveCrystals(ResourceKey<Level> dim) {
        Set<BlockPos> set = ACTIVE_CRYSTALS.get(dim);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    private final AutoWirelessPointHandler autoHandler = new AutoWirelessPointHandler();
    private final List<LinkTarget> autoLinkedTargets = new ArrayList<>();
    private long lastScanTick = 0;

    public TileFluxAutoLinkCrystal(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_AUTO_LINK_CRYSTAL.get(), pos, state);
        autoHandler.setLimit(Config.feStorageTransfer);
        autoHandler.setCapacity(Config.feStorageCapacity);
    }

    @Nonnull
    @Override
    public FluxConnectorHandler getTransferHandler() {
        return autoHandler;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            registerCrystal(level.dimension(), worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            unregisterCrystal(level.dimension(), worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public List<LinkTarget> getLinkedTargets() {
        Set<BlockPos> seen = new HashSet<>();
        List<LinkTarget> all = new ArrayList<>();
        for (LinkTarget t : super.getLinkedTargets()) {
            all.add(t);
            seen.add(t.pos().pos());
        }
        for (LinkTarget t : autoLinkedTargets) {
            if (!seen.contains(t.pos().pos())) {
                all.add(t);
            }
        }
        return all;
    }

    @Override
    public void removeLink(BlockPos targetPos) {
        super.removeLink(targetPos);
        autoLinkedTargets.removeIf(d -> d.pos().pos().equals(targetPos));
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean isInRange(BlockPos targetPos) {
        int radius = (int) Config.autoLinkRadius;
        return Math.abs(targetPos.getX() - worldPosition.getX()) <= radius &&
                Math.abs(targetPos.getY() - worldPosition.getY()) <= radius &&
                Math.abs(targetPos.getZ() - worldPosition.getZ()) <= radius;
    }

    @Override
    public void addLink(BlockPos targetPos, Direction side) {
        if (level == null) return;
        if (!isInRange(targetPos)) return;
        super.addLink(targetPos, side);
    }

    public List<LinkTarget> getAutoLinkedTargets() {
        return autoLinkedTargets;
    }

    public void triggerScan() {
        if (level == null || level.isClientSide) return;

        long cooldown = Config.autoLinkScanCooldown;
        long gameTime = level.getGameTime();
        if (cooldown > 0 && gameTime - lastScanTick < cooldown) return;
        lastScanTick = gameTime;

        int radius = (int) Config.autoLinkRadius;

        Set<BlockPos> found = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos checkPos = worldPosition.offset(dx, dy, dz);
                    if (!level.isLoaded(checkPos)) continue;
                    Direction validSide = findValidEnergySide(level, checkPos);
                    if (validSide != null) {
                        found.add(checkPos);
                    }
                }
            }
        }

        for (BlockPos pos : found) {
            boolean alreadyLinked = false;
            for (LinkTarget t : super.getLinkedTargets()) {
                if (t.pos().pos().equals(pos)) {
                    alreadyLinked = true;
                    break;
                }
            }
            if (alreadyLinked) continue;
            boolean alreadyAutoLinked = false;
            for (LinkTarget t : autoLinkedTargets) {
                if (t.pos().pos().equals(pos)) {
                    alreadyAutoLinked = true;
                    break;
                }
            }
            if (!alreadyAutoLinked) {
                Direction side = findValidEnergySide(level, pos);
                if (side != null) {
                    autoLinkedTargets.add(new LinkTarget(GlobalPos.of(level.dimension(), pos), side));
                }
            }
        }

        autoLinkedTargets.removeIf(target -> !found.contains(target.pos().pos()) ||
                super.getLinkedTargets().stream().anyMatch(t -> t.pos().pos().equals(target.pos().pos())));

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void tryLinkNearbyBlock(BlockPos placedPos) {
        if (level == null || level.isClientSide) return;
        if (!level.isLoaded(placedPos)) return;
        if (placedPos.equals(worldPosition)) return;

        int radius = (int) Config.autoLinkRadius;
        if (Math.abs(placedPos.getX() - worldPosition.getX()) > radius) return;
        if (Math.abs(placedPos.getY() - worldPosition.getY()) > radius) return;
        if (Math.abs(placedPos.getZ() - worldPosition.getZ()) > radius) return;

        Direction side = findValidEnergySide(level, placedPos);
        if (side == null) return;

        for (LinkTarget t : super.getLinkedTargets()) {
            if (t.pos().pos().equals(placedPos)) return;
        }
        for (LinkTarget t : autoLinkedTargets) {
            if (t.pos().pos().equals(placedPos)) return;
        }

        autoLinkedTargets.add(new LinkTarget(GlobalPos.of(level.dimension(), placedPos), side));
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Nullable
    public static Direction findValidEnergySide(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        for (Direction dir : Direction.values()) {
            IBlockEnergyConnector connector = EnergyUtils.getConnector(be, dir);
            if (connector != null && connector.canSendTo(be, dir)) {
                return dir;
            }
        }
        return null;
    }

    @Override
    public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        ListTag autoLinks = new ListTag();
        for (LinkTarget d : autoLinkedTargets) {
            autoLinks.add(writeLinkToTag(d));
        }
        tag.put("autoWirelessLinks", autoLinks);
        tag.putLong("lastScanTick", lastScanTick);
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        autoLinkedTargets.clear();
        ListTag autoLinks = tag.getList("autoWirelessLinks", Tag.TAG_COMPOUND);
        for (int i = 0; i < autoLinks.size(); i++) {
            LinkTarget target = readLinkFromTag(autoLinks.getCompound(i));
            if (target != null) {
                autoLinkedTargets.add(target);
            }
        }
        lastScanTick = tag.getLong("lastScanTick");
    }

    private CompoundTag writeLinkToTag(LinkTarget d) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(d.pos().pos()));
        tag.putString("dimension", d.pos().dimension().location().toString());
        tag.putString("side", d.side().getName());
        return tag;
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

    public class AutoWirelessPointHandler extends FluxConnectorHandler {

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
            mDesired = sendToTargets(getLimit(), true);
        }

        @Override
        public void onCycleEnd() {
            mBuffer += mChange = -sendToTargets(Math.min(mBuffer, getLimit()), false);
        }

        @Override
        public void addToBuffer(long energy) {
            mBuffer += energy;
        }

        @Override
        public long getRequest() {
            return Math.max(mDesired - mBuffer, 0);
        }

        private long sendToTargets(long energy, boolean simulate) {
            long leftover = energy;
            List<LinkTarget> allTargets = getLinkedTargets();
            for (LinkTarget device : allTargets) {
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
