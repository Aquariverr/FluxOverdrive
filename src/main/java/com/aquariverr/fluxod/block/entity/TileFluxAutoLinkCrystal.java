package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.api.energy.IBlockEnergyConnector;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.util.EnergyUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ParametersAreNonnullByDefault
public class TileFluxAutoLinkCrystal extends TileFluxLinkCrystal implements IFluxPoint {

    private static final Map<ResourceKey<Level>, Map<Long, Set<BlockPos>>> ACTIVE_CRYSTALS =
            new ConcurrentHashMap<>();

    public static void registerCrystal(ResourceKey<Level> dimension, BlockPos pos) {
        ACTIVE_CRYSTALS.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey(pos), ignored -> ConcurrentHashMap.newKeySet())
                .add(pos.immutable());
    }

    public static void unregisterCrystal(ResourceKey<Level> dimension, BlockPos pos) {
        Map<Long, Set<BlockPos>> chunks = ACTIVE_CRYSTALS.get(dimension);
        if (chunks == null) return;
        long key = chunkKey(pos);
        Set<BlockPos> positions = chunks.get(key);
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) chunks.remove(key);
        }
        if (chunks.isEmpty()) ACTIVE_CRYSTALS.remove(dimension);
    }

    public static Collection<BlockPos> getActiveCrystalsNear(ResourceKey<Level> dimension, BlockPos pos, int radius) {
        Map<Long, Set<BlockPos>> chunks = ACTIVE_CRYSTALS.get(dimension);
        if (chunks == null) return List.of();

        int minChunkX = Math.floorDiv(pos.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(pos.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(pos.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(pos.getZ() + radius, 16);
        List<BlockPos> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Set<BlockPos> positions = chunks.get(chunkKey(chunkX, chunkZ));
                if (positions != null) result.addAll(positions);
            }
        }
        return result;
    }

    private static long chunkKey(BlockPos pos) {
        return chunkKey(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    private final AutoWirelessPointHandler autoHandler = new AutoWirelessPointHandler();
    private final List<LinkTarget> autoLinkedTargets = new ArrayList<>();
    private final List<LinkTarget> autoLinkedTargetsView = Collections.unmodifiableList(autoLinkedTargets);
    private List<LinkTarget> combinedTargets = List.of();
    private boolean combinedTargetsDirty = true;
    private long lastScanTick = Long.MIN_VALUE;

    public TileFluxAutoLinkCrystal(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_AUTO_LINK_CRYSTAL.get(), pos, state);
        autoHandler.setLimit(Config.feStorageTransfer);
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
        if (combinedTargetsDirty) {
            Map<GlobalPos, LinkTarget> targets = new LinkedHashMap<>();
            for (LinkTarget target : linkedTargets) targets.put(target.pos(), target);
            for (LinkTarget target : autoLinkedTargets) targets.putIfAbsent(target.pos(), target);
            combinedTargets = List.copyOf(targets.values());
            combinedTargetsDirty = false;
        }
        return combinedTargets;
    }

    @Override
    public boolean removeLink(BlockPos targetPos) {
        if (!canModifyLinks()) return false;
        GlobalPos target = GlobalPos.of(level.dimension(), targetPos);
        boolean changed = removeManualLink(target);
        changed |= autoLinkedTargets.removeIf(link -> link.pos().equals(target));
        if (changed) linksChanged();
        return changed;
    }

    @Override
    public boolean isLinked(BlockPos targetPos) {
        if (level == null) return false;
        GlobalPos target = GlobalPos.of(level.dimension(), targetPos);
        return findManualLink(target) >= 0 || findAutoLink(target) >= 0;
    }

    @Override
    public boolean isInRange(BlockPos targetPos) {
        int radius = (int) Config.autoLinkRadius;
        return Math.abs(targetPos.getX() - worldPosition.getX()) <= radius
                && Math.abs(targetPos.getY() - worldPosition.getY()) <= radius
                && Math.abs(targetPos.getZ() - worldPosition.getZ()) <= radius;
    }

    @Override
    public boolean addLink(BlockPos targetPos, Direction side) {
        if (!canModifyLinks() || !isInRange(targetPos)) return false;
        GlobalPos globalPos = GlobalPos.of(level.dimension(), targetPos);
        int manualIndex = findManualLink(globalPos);
        int autoIndex = findAutoLink(globalPos);
        if (manualIndex < 0 && autoIndex < 0 && getLinkedTargets().size() >= getMaxLinks()) return false;

        LinkTarget target = new LinkTarget(globalPos, side);
        if (manualIndex >= 0 && linkedTargets.get(manualIndex).equals(target) && autoIndex < 0) return false;
        if (manualIndex >= 0) {
            linkedTargets.set(manualIndex, target);
        } else {
            linkedTargets.add(target);
        }
        if (autoIndex >= 0) autoLinkedTargets.remove(autoIndex);
        linksChanged();
        return true;
    }

    public List<LinkTarget> getAutoLinkedTargets() {
        return autoLinkedTargetsView;
    }

    public void triggerScan() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        long cooldown = Config.autoLinkScanCooldown;
        long gameTime = level.getGameTime();
        if (cooldown > 0 && lastScanTick != Long.MIN_VALUE && gameTime >= lastScanTick
                && gameTime - lastScanTick < cooldown) {
            return;
        }
        lastScanTick = gameTime;

        Map<BlockPos, Direction> discovered = scanLoadedBlockEntities(serverLevel);
        Set<GlobalPos> manualPositions = new LinkedHashSet<>();
        for (LinkTarget target : linkedTargets) manualPositions.add(target.pos());

        int autoLimit = Math.max(0, getMaxLinks() - linkedTargets.size());
        List<LinkTarget> updated = new ArrayList<>(Math.min(autoLimit, autoLinkedTargets.size()));
        Set<BlockPos> retainedPositions = new LinkedHashSet<>();
        for (LinkTarget existing : autoLinkedTargets) {
            if (updated.size() >= autoLimit) break;
            GlobalPos globalPos = existing.pos();
            BlockPos pos = globalPos.pos();
            if (!globalPos.dimension().equals(level.dimension()) || !isInRange(pos)
                    || manualPositions.contains(globalPos)) {
                continue;
            }

            Direction discoveredSide = discovered.remove(pos);
            if (discoveredSide != null) {
                updated.add(new LinkTarget(globalPos, discoveredSide));
                retainedPositions.add(pos);
            } else if (!level.isLoaded(pos)) {
                updated.add(existing);
                retainedPositions.add(pos);
            }
        }

        List<Map.Entry<BlockPos, Direction>> additions = new ArrayList<>(discovered.entrySet());
        additions.sort(Comparator.comparingDouble(entry -> worldPosition.distSqr(entry.getKey())));
        for (Map.Entry<BlockPos, Direction> entry : additions) {
            if (updated.size() >= autoLimit) break;
            BlockPos pos = entry.getKey();
            GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
            if (manualPositions.contains(globalPos) || !retainedPositions.add(pos)) continue;
            updated.add(new LinkTarget(globalPos, entry.getValue()));
        }

        if (!autoLinkedTargets.equals(updated)) {
            autoLinkedTargets.clear();
            autoLinkedTargets.addAll(updated);
            linksChanged();
        }
    }

    public void tryLinkNearbyBlock(BlockPos placedPos) {
        if (!canModifyLinks() || placedPos.equals(worldPosition) || !isInRange(placedPos)
                || !level.isLoaded(placedPos) || getLinkedTargets().size() >= getMaxLinks()) {
            return;
        }

        Direction side = findValidEnergySide(level, placedPos);
        if (side == null) return;
        GlobalPos globalPos = GlobalPos.of(level.dimension(), placedPos);
        if (findManualLink(globalPos) >= 0 || findAutoLink(globalPos) >= 0) return;

        autoLinkedTargets.add(new LinkTarget(globalPos, side));
        linksChanged();
    }

    private Map<BlockPos, Direction> scanLoadedBlockEntities(ServerLevel serverLevel) {
        int radius = (int) Config.autoLinkRadius;
        int minChunkX = Math.floorDiv(worldPosition.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(worldPosition.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(worldPosition.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(worldPosition.getZ() + radius, 16);
        Map<BlockPos, Direction> discovered = new LinkedHashMap<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (pos.equals(worldPosition) || !isInRange(pos)) continue;

                    Direction side = findValidEnergySide(level, pos);
                    if (side == null) {
                        int existingIndex = findAutoLink(GlobalPos.of(level.dimension(), pos));
                        if (existingIndex >= 0) {
                            LinkTarget existing = autoLinkedTargets.get(existingIndex);
                            if (hasEnergyCapability(blockEntity, existing.side())) {
                                side = existing.side();
                            } else {
                                side = findEnergyCapabilitySide(blockEntity);
                            }
                        }
                    }
                    if (side != null) discovered.put(pos.immutable(), side);
                }
            }
        }
        return discovered;
    }

    @Nullable
    public static Direction findValidEnergySide(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        for (Direction direction : Direction.values()) {
            IBlockEnergyConnector connector = EnergyUtils.getConnector(blockEntity, direction);
            if (connector != null && connector.canSendTo(blockEntity, direction)) return direction;
        }
        return null;
    }

    private static boolean hasEnergyCapability(BlockEntity blockEntity, Direction side) {
        IBlockEnergyConnector connector = EnergyUtils.getConnector(blockEntity, side);
        return connector != null && connector.hasCapability(blockEntity, side);
    }

    @Nullable
    private static Direction findEnergyCapabilitySide(BlockEntity blockEntity) {
        for (Direction direction : Direction.values()) {
            if (hasEnergyCapability(blockEntity, direction)) return direction;
        }
        return null;
    }

    @Override
    public void writeCustomTag(net.minecraft.nbt.CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        net.minecraft.nbt.ListTag autoLinks = new net.minecraft.nbt.ListTag();
        for (LinkTarget target : autoLinkedTargets) autoLinks.add(writeLinkToTag(target));
        tag.put("autoWirelessLinks", autoLinks);
    }

    @Override
    public void readCustomTag(net.minecraft.nbt.CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        autoLinkedTargets.clear();
        Set<GlobalPos> seen = new LinkedHashSet<>();
        net.minecraft.nbt.ListTag autoLinks = tag.getList("autoWirelessLinks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        int available = Math.max(0, getMaxLinks() - linkedTargets.size());
        int entriesToRead = Math.min(autoLinks.size(), getMaxLinks());
        for (int i = 0; i < entriesToRead && autoLinkedTargets.size() < available; i++) {
            LinkTarget target = readLinkFromTag(autoLinks.getCompound(i));
            if (target != null && seen.add(target.pos()) && findManualLink(target.pos()) < 0) {
                autoLinkedTargets.add(target);
            }
        }
        invalidateCombinedTargets();
    }

    @Override
    protected void linksChanged() {
        invalidateCombinedTargets();
        super.linksChanged();
    }

    private int findAutoLink(GlobalPos targetPos) {
        for (int i = 0; i < autoLinkedTargets.size(); i++) {
            if (autoLinkedTargets.get(i).pos().equals(targetPos)) return i;
        }
        return -1;
    }

    private void invalidateCombinedTargets() {
        combinedTargetsDirty = true;
    }

    public class AutoWirelessPointHandler extends FluxConnectorHandler {

        private long desired;
        private int targetCursor;

        @Override
        public void onCycleStart() {
            desired = sendToTargets(getLimit(), true);
        }

        @Override
        public void onCycleEnd() {
            mBuffer += mChange = -sendToTargets(Math.min(mBuffer, getLimit()), false);
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

        private long sendToTargets(long energy, boolean simulate) {
            long leftover = energy;
            List<LinkTarget> targets = getLinkedTargets();
            int size = targets.size();
            for (int offset = 0; offset < size; offset++) {
                if (leftover <= 0) return energy;
                LinkTarget target = targets.get((targetCursor + offset) % size);
                if (level == null || !target.pos().dimension().equals(level.dimension())) continue;

                BlockEntity blockEntity = target.getTarget(level);
                if (blockEntity == null) continue;
                IBlockEnergyConnector connector = EnergyUtils.getConnector(blockEntity, target.side());
                if (connector == null || !connector.canSendTo(blockEntity, target.side())) continue;

                long sent = Math.max(0, Math.min(leftover,
                        connector.sendTo(leftover, blockEntity, target.side(), simulate)));
                leftover -= sent;
            }
            return energy - leftover;
        }
    }
}
