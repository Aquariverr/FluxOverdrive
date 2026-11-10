package com.aquariverr.fluxod.block.entity;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Incrementally removes a three-dimensional, resistance-aware sphere around a Quantum Battery
 * quench. Only chunks loaded when the process is created are eligible, and chunks that unload
 * before their turn are skipped rather than force-loaded.
 */
final class QuantumBatteryQuenchProcess {

    private static final double DISTANCE_EPSILON = 1.0E-7;

    private final ServerLevel level;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double radius;
    private final double radiusSquared;
    private final Explosion explosionContext;
    private final int minBlockX;
    private final int maxBlockX;
    private final int minBlockY;
    private final int maxBlockY;
    private final int minBlockZ;
    private final int maxBlockZ;
    private final int minSectionY;
    private final int maxSectionY;
    private final List<ChunkTarget> eligibleChunks;
    private final LongOpenHashSet forcedBlocks = new LongOpenHashSet();
    private final LongArrayList forcedCleanup = new LongArrayList();
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    private int forcedCleanupIndex;
    private int chunkIndex;
    private int nextSectionY;
    private boolean currentChunkInitialized;
    private boolean terrainDone;
    private boolean forcedCleanupInitialized;
    private QuantumBatteryQuenchGeometry.BlockCursor sectionCursor;
    private boolean done;

    QuantumBatteryQuenchProcess(ServerLevel level, double centerX, double centerY, double centerZ,
                                double radius, Collection<BlockPos> forcedBlocks) {
        this.level = level;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = Math.max(1.0, radius);
        this.radiusSquared = this.radius * this.radius;
        this.explosionContext = new Explosion(level, null, centerX, centerY, centerZ,
                (float) this.radius, false, Explosion.BlockInteraction.DESTROY);

        for (BlockPos pos : forcedBlocks) {
            long packedPos = pos.asLong();
            if (level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) == null) {
                continue;
            }
            this.forcedBlocks.add(packedPos);
        }

        this.minBlockX = QuantumBatteryQuenchGeometry.minBlockCoordinate(centerX, this.radius);
        this.maxBlockX = QuantumBatteryQuenchGeometry.maxBlockCoordinate(centerX, this.radius);
        this.minBlockY = Math.max(level.getMinBuildHeight(),
                QuantumBatteryQuenchGeometry.minBlockCoordinate(centerY, this.radius));
        this.maxBlockY = Math.min(level.getMaxBuildHeight() - 1,
                QuantumBatteryQuenchGeometry.maxBlockCoordinate(centerY, this.radius));
        this.minBlockZ = QuantumBatteryQuenchGeometry.minBlockCoordinate(centerZ, this.radius);
        this.maxBlockZ = QuantumBatteryQuenchGeometry.maxBlockCoordinate(centerZ, this.radius);
        this.minSectionY = SectionPos.blockToSectionCoord(this.minBlockY);
        this.maxSectionY = SectionPos.blockToSectionCoord(this.maxBlockY);
        this.eligibleChunks = snapshotEligibleChunks();
    }

    ServerLevel level() {
        return level;
    }

    boolean isDone() {
        return done;
    }

    int eligibleChunkCount() {
        return eligibleChunks.size();
    }

    /**
     * Performs at most {@code maxWork} block visits while also respecting a shared tick deadline.
     */
    int tick(long deadlineNanos, int maxWork) {
        int work = 0;
        int loopCount = 0;
        while (!done && work < maxWork) {
            if ((loopCount++ & 15) == 0 && Util.getNanos() >= deadlineNanos) break;

            if (terrainDone) {
                if (!forcedCleanupInitialized) prepareForcedCleanup();
                if (forcedCleanupIndex >= forcedCleanup.size()) {
                    done = true;
                    break;
                }
                long packedPos = forcedCleanup.getLong(forcedCleanupIndex++);
                LevelChunk forcedChunk = level.getChunkSource().getChunkNow(
                        SectionPos.blockToSectionCoord(BlockPos.getX(packedPos)),
                        SectionPos.blockToSectionCoord(BlockPos.getZ(packedPos)));
                if (forcedChunk != null) processBlock(forcedChunk, packedPos, true);
                work++;
                continue;
            }

            if (chunkIndex >= eligibleChunks.size()) {
                terrainDone = true;
                continue;
            }

            ChunkTarget target = eligibleChunks.get(chunkIndex);
            LevelChunk chunk = level.getChunkSource().getChunkNow(target.chunkX(), target.chunkZ());
            if (chunk == null) {
                advanceChunk();
                continue;
            }
            if (!currentChunkInitialized) {
                nextSectionY = minSectionY;
                currentChunkInitialized = true;
            }

            if (sectionCursor == null && !prepareNextSection(chunk, target)) {
                advanceChunk();
                continue;
            }

            long packedPos = BlockPos.asLong(sectionCursor.x(), sectionCursor.y(), sectionCursor.z());
            sectionCursor.advance();
            processBlock(chunk, packedPos, false);
            work++;
            if (!sectionCursor.hasNext()) sectionCursor = null;
        }
        return work;
    }

    private List<ChunkTarget> snapshotEligibleChunks() {
        int minChunkX = Math.floorDiv(minBlockX, 16);
        int maxChunkX = Math.floorDiv(maxBlockX, 16);
        int minChunkZ = Math.floorDiv(minBlockZ, 16);
        int maxChunkZ = Math.floorDiv(maxBlockZ, 16);
        List<ChunkTarget> chunks = new ArrayList<>((maxChunkX - minChunkX + 1)
                * (maxChunkZ - minChunkZ + 1));

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int chunkMinX = chunkX << 4;
            int chunkMaxX = chunkMinX + 15;
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int chunkMinZ = chunkZ << 4;
                int chunkMaxZ = chunkMinZ + 15;
                double horizontalDistanceSquared = QuantumBatteryQuenchGeometry.squaredDistanceToBlockBox(
                        centerX, centerZ, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
                if (horizontalDistanceSquared > radiusSquared + DISTANCE_EPSILON) continue;
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) continue;
                chunks.add(new ChunkTarget(chunkX, chunkZ, horizontalDistanceSquared));
            }
        }
        chunks.sort(Comparator.comparingDouble(ChunkTarget::distanceSquared));
        return List.copyOf(chunks);
    }

    private boolean prepareNextSection(LevelChunk chunk, ChunkTarget target) {
        int chunkMinX = Math.max(minBlockX, target.chunkX() << 4);
        int chunkMaxX = Math.min(maxBlockX, (target.chunkX() << 4) + 15);
        int chunkMinZ = Math.max(minBlockZ, target.chunkZ() << 4);
        int chunkMaxZ = Math.min(maxBlockZ, (target.chunkZ() << 4) + 15);

        while (nextSectionY <= maxSectionY) {
            int sectionY = nextSectionY++;
            int sectionMinY = Math.max(minBlockY, SectionPos.sectionToBlockCoord(sectionY));
            int sectionMaxY = Math.min(maxBlockY, SectionPos.sectionToBlockCoord(sectionY) + 15);
            if (!QuantumBatteryQuenchGeometry.sphereIntersectsBlockBox(centerX, centerY, centerZ, radius,
                    chunkMinX, sectionMinY, chunkMinZ, chunkMaxX, sectionMaxY, chunkMaxZ)) {
                continue;
            }

            int sectionIndex = level.getSectionIndexFromSectionY(sectionY);
            LevelChunkSection[] sections = chunk.getSections();
            if (sectionIndex < 0 || sectionIndex >= sections.length) continue;
            if (sections[sectionIndex].hasOnlyAir()) continue;
            sectionCursor = new QuantumBatteryQuenchGeometry.BlockCursor(
                    chunkMinX, chunkMaxX, sectionMinY, sectionMaxY,
                    chunkMinZ, chunkMaxZ);
            return true;
        }
        return false;
    }

    private void processBlock(LevelChunk chunk, long packedPos, boolean cleanupForced) {
        int blockX = BlockPos.getX(packedPos);
        int blockY = BlockPos.getY(packedPos);
        int blockZ = BlockPos.getZ(packedPos);
        boolean forced = cleanupForced || forcedBlocks.remove(packedPos);
        double distanceSquared = 0.0;
        if (!forced) {
            distanceSquared = QuantumBatteryQuenchGeometry.squaredDistanceToBlockCenter(
                    centerX, centerY, centerZ, blockX, blockY, blockZ);
            if (distanceSquared > radiusSquared + DISTANCE_EPSILON) return;
        }

        mutablePos.set(blockX, blockY, blockZ);
        BlockState state = chunk.getBlockState(mutablePos);
        if (state.isAir()) return;
        if (!forced) {
            float resistance = Math.max(
                    state.getExplosionResistance(level, mutablePos, explosionContext),
                    state.getFluidState().getExplosionResistance(level, mutablePos, explosionContext));
            if (!QuantumBatteryState.shouldDestroyBlock(radius, Math.sqrt(distanceSquared), resistance)) return;
        }

        level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void prepareForcedCleanup() {
        long controllerPos = BlockPos.asLong(
                (int) Math.floor(centerX), (int) Math.floor(centerY), (int) Math.floor(centerZ));
        boolean controllerRemaining = false;
        LongIterator iterator = forcedBlocks.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            if (packedPos == controllerPos) {
                controllerRemaining = true;
            } else {
                forcedCleanup.add(packedPos);
            }
        }
        if (controllerRemaining) forcedCleanup.add(controllerPos);
        forcedBlocks.clear();
        forcedCleanupInitialized = true;
    }

    private void advanceChunk() {
        chunkIndex++;
        currentChunkInitialized = false;
        sectionCursor = null;
    }

    private record ChunkTarget(int chunkX, int chunkZ, double distanceSquared) {}
}
