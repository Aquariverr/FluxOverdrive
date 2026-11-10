package com.aquariverr.fluxod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

/** Server reads that neither load chunks nor renew the UNKNOWN tickets created by Level#getChunk. */
public final class LoadedBlockAccess {
    @Nullable
    private static LevelChunk chunk(@Nullable Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()
                || !level.isInWorldBounds(pos)) return null;
        var chunks = serverLevel.getChunkSource();
        int x = pos.getX() >> 4;
        int z = pos.getZ() >> 4;
        // getChunkNow can still see a cached chunk awaiting unload. Require a live FULL holder first.
        return chunks.hasChunk(x, z) ? chunks.getChunkNow(x, z) : null;
    }

    @Nullable
    public static Snapshot read(@Nullable Level level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos);
        if (chunk == null) return null;
        // Even EntityCreationType.CHECK promotes pending block-entity NBT. Use only live instances.
        BlockEntity blockEntity = chunk.getBlockEntities().get(pos);
        if (blockEntity != null && (blockEntity.isRemoved() || blockEntity.getLevel() != level
                || !blockEntity.getBlockPos().equals(pos))) blockEntity = null;
        return new Snapshot(chunk.getBlockState(pos), blockEntity);
    }

    public static boolean isCurrent(BlockEntity blockEntity) {
        if (blockEntity.isRemoved()) return false;
        Snapshot current = read(blockEntity.getLevel(), blockEntity.getBlockPos());
        return current != null && current.blockEntity() == blockEntity;
    }

    /** Keeps redstone callbacks on the same non-loading block getter, including neighboring reads. */
    public static SignalGetter signals(Level level) {
        return new SignalGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                Snapshot current = read(level, pos);
                return current == null ? null : current.blockEntity();
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                LevelChunk current = chunk(level, pos);
                return current == null ? Blocks.AIR.defaultBlockState() : current.getBlockState(pos);
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Override
            public int getHeight() { return level.getHeight(); }

            @Override
            public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
    }

    public record Snapshot(BlockState state, @Nullable BlockEntity blockEntity) {}

    private LoadedBlockAccess() {}
}
