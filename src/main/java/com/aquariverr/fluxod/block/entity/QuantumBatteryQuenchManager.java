package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/** Keeps Quantum Battery quench destruction alive after its controller block is removed. */
public final class QuantumBatteryQuenchManager {

    private static final long TICK_BUDGET_NANOS = 5_000_000L;
    private static final int MAX_BLOCK_VISITS_PER_TICK = 32_768;
    private static final int PROCESS_SLICE_BLOCKS = 4_096;
    private static final Map<MinecraftServer, ArrayDeque<QuantumBatteryQuenchProcess>> ACTIVE_PROCESSES =
            new IdentityHashMap<>();

    public static void start(ServerLevel level, double centerX, double centerY, double centerZ,
                             double radius, Collection<BlockPos> forcedBlocks) {
        QuantumBatteryQuenchProcess process = new QuantumBatteryQuenchProcess(
                level, centerX, centerY, centerZ, radius, forcedBlocks);
        ACTIVE_PROCESSES.computeIfAbsent(level.getServer(), ignored -> new ArrayDeque<>())
                .addLast(process);
        FluxOverdrive.LOGGER.info(
                "Scheduled Quantum Battery quench at [{}, {}, {}], radius {}, eligible chunks {}",
                centerX, centerY, centerZ, radius, process.eligibleChunkCount());
    }

    public static void tick(MinecraftServer server) {
        ArrayDeque<QuantumBatteryQuenchProcess> processes = ACTIVE_PROCESSES.get(server);
        if (processes == null || processes.isEmpty()) return;

        long deadline = Util.getNanos() + TICK_BUDGET_NANOS;
        int remainingVisits = MAX_BLOCK_VISITS_PER_TICK;
        while (!processes.isEmpty() && remainingVisits > 0 && Util.getNanos() < deadline) {
            QuantumBatteryQuenchProcess process = processes.removeFirst();
            int work = process.tick(deadline, Math.min(PROCESS_SLICE_BLOCKS, remainingVisits));
            remainingVisits -= Math.max(1, work);
            if (!process.isDone()) processes.addLast(process);
        }

        if (processes.isEmpty()) ACTIVE_PROCESSES.remove(server);
    }

    public static void clear(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ArrayDeque<QuantumBatteryQuenchProcess> processes = ACTIVE_PROCESSES.get(server);
        if (processes == null) return;
        processes.removeIf(process -> process.level() == level);
        if (processes.isEmpty()) ACTIVE_PROCESSES.remove(server);
    }

    private QuantumBatteryQuenchManager() {}
}
