package com.aquariverr.fluxod.multiblock;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuantumBatteryAssembler {

    private static final int PLACEMENTS_PER_TICK = 256;

    public enum Status {
        COMPLETE,
        STARTED,
        BUSY,
        BUILT,
        PARTIAL,
        MISSING_MATERIALS,
        OBSTRUCTED,
        UNLOADED,
        OUT_OF_BOUNDS,
        CORE_LOCKED,
        DENIED,
        FAILED
    }

    public record Result(Status status, int placed, int remaining, @Nullable BlockPos problemPos) {}

    public record Start(Result result, @Nullable Job job) {}

    public static Start plan(TileQuantumBatteryController controller, ServerPlayer player, int targetTier) {
        if (targetTier < 1 || targetTier > 3) {
            return result(Status.FAILED, 0, 0, null);
        }
        if (!(controller.getLevel() instanceof ServerLevel level) || !controller.canPlayerManage(player)) {
            return result(Status.DENIED, 0, 0, null);
        }
        if (targetTier > controller.getCoreLevel()) {
            return result(Status.CORE_LOCKED, 0, 0, null);
        }

        List<Placement> placements = new ArrayList<>();
        BlockPos center = controller.getBlockPos();
        for (int tier = 1; tier <= targetTier; tier++) {
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                QuantumBatteryStructure.Offset offset = cell.offset();
                BlockPos target = center.offset(offset.x(), offset.y(), offset.z());
                if (!level.isInWorldBounds(target)) {
                    return result(Status.OUT_OF_BOUNDS, 0, 0, target);
                }
                if (!level.isLoaded(target)) {
                    return result(Status.UNLOADED, 0, 0, target);
                }

                Block expected = expectedBlock(tier, cell.type());
                BlockState existing = level.getBlockState(target);
                if (isAcceptedBlock(tier, cell, existing)) continue;
                if (!existing.canBeReplaced()) {
                    return result(Status.OBSTRUCTED, 0, 0, target);
                }
                ItemStack placementStack = new ItemStack(expected);
                if (!level.mayInteract(player, target)
                        || !player.mayUseItemAt(target, Direction.UP, placementStack)) {
                    return result(Status.DENIED, 0, 0, target);
                }
                placements.add(new Placement(target.immutable(), expected, tier, cell));
            }
        }

        if (placements.isEmpty()) {
            controller.validateStructure();
            return result(Status.COMPLETE, 0, 0, null);
        }
        if (!player.isCreative() && !hasAnyMaterial(player, placements)) {
            return result(Status.MISSING_MATERIALS, 0, placements.size(), null);
        }

        Job job = new Job(player.getUUID(), targetTier, List.copyOf(placements));
        return new Start(new Result(Status.STARTED, 0, placements.size(), null), job);
    }

    public static Component resultMessage(Result result) {
        return switch (result.status()) {
            case COMPLETE -> Component.translatable("message.flux_overdrive.quantum_battery.build.complete");
            case STARTED -> Component.translatable("message.flux_overdrive.quantum_battery.build.started", result.remaining());
            case BUSY -> Component.translatable("message.flux_overdrive.quantum_battery.build.busy");
            case BUILT -> Component.translatable("message.flux_overdrive.quantum_battery.build.success", result.placed());
            case PARTIAL -> Component.translatable("message.flux_overdrive.quantum_battery.build.partial",
                    result.placed(), result.remaining());
            case MISSING_MATERIALS -> Component.translatable("message.flux_overdrive.quantum_battery.build.missing",
                    result.remaining());
            case OBSTRUCTED -> positionMessage("message.flux_overdrive.quantum_battery.build.obstructed", result.problemPos());
            case UNLOADED -> positionMessage("message.flux_overdrive.quantum_battery.build.unloaded", result.problemPos());
            case OUT_OF_BOUNDS -> positionMessage("message.flux_overdrive.quantum_battery.build.out_of_bounds", result.problemPos());
            case CORE_LOCKED -> Component.translatable("message.flux_overdrive.quantum_battery.build.core_locked");
            case DENIED -> positionMessage("message.flux_overdrive.quantum_battery.build.denied", result.problemPos());
            case FAILED -> positionMessage("message.flux_overdrive.quantum_battery.build.failed", result.problemPos());
        };
    }

    public static Block expectedBlock(int tier, QuantumBatteryStructure.CellType type) {
        return switch (tier) {
            case 1 -> type == QuantumBatteryStructure.CellType.CASING
                    ? RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING.get()
                    : RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS.get();
            case 2 -> type == QuantumBatteryStructure.CellType.CASING
                    ? RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING.get()
                    : RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS.get();
            case 3 -> type == QuantumBatteryStructure.CellType.CASING
                    ? RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING.get()
                    : RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS.get();
            default -> throw new IllegalArgumentException("Invalid quantum battery tier: " + tier);
        };
    }

    public static boolean isAcceptedBlock(int tier, QuantumBatteryStructure.Cell cell, BlockState state) {
        if (state.is(expectedBlock(tier, cell.type()))) return true;
        return cell.type() == QuantumBatteryStructure.CellType.CASING
                && QuantumBatteryStructure.isStabilizerSlot(tier, cell)
                && state.getBlock() instanceof QuantumBatteryPlugin plugin
                && plugin.canInstallInRing(tier);
    }

    private static Start result(Status status, int placed, int remaining, @Nullable BlockPos pos) {
        return new Start(new Result(status, placed, remaining, pos), null);
    }

    private static Component positionMessage(String key, @Nullable BlockPos pos) {
        if (pos == null) return Component.translatable(key, "?", "?", "?");
        return Component.translatable(key, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean hasAnyMaterial(ServerPlayer player, List<Placement> placements) {
        for (Placement placement : placements) {
            Item item = placement.block().asItem();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (player.getInventory().getItem(slot).is(item)) return true;
            }
        }
        return false;
    }

    private static Map<Item, Integer> countAvailableMaterials(ServerPlayer player) {
        Map<Item, Integer> counts = new IdentityHashMap<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static void consume(ServerPlayer player, Map<Item, Integer> materials) {
        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(entry.getKey())) continue;
                int consumed = Math.min(stack.getCount(), remaining);
                stack.shrink(consumed);
                remaining -= consumed;
            }
            if (remaining != 0) {
                throw new IllegalStateException("Quantum battery material disappeared during assembly: " + entry.getKey());
            }
        }
        player.getInventory().setChanged();
    }

    private static void refund(ServerPlayer player, Map<Item, Integer> materials) {
        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    private record Placement(BlockPos pos, Block block, int tier, QuantumBatteryStructure.Cell cell) {}

    public static final class Job {
        private final UUID playerId;
        private final int targetTier;
        private final List<Placement> placements;
        private int cursor;
        private int totalPlaced;

        private Job(UUID playerId, int targetTier, List<Placement> placements) {
            this.playerId = playerId;
            this.targetTier = targetTier;
            this.placements = placements;
        }

        public UUID playerId() {
            return playerId;
        }

        @Nullable
        public Result tick(TileQuantumBatteryController controller) {
            if (!(controller.getLevel() instanceof ServerLevel level)) {
                return new Result(Status.FAILED, totalPlaced, placements.size() - cursor, null);
            }
            if (targetTier > controller.getCoreLevel()) {
                return new Result(Status.CORE_LOCKED, totalPlaced, placements.size() - cursor, null);
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            BlockPos controllerPos = controller.getBlockPos();
            if (player == null || player.level() != level || !controller.canPlayerManage(player)
                    || player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                    controllerPos.getZ() + 0.5) > 64.0) {
                return new Result(Status.DENIED, totalPlaced, placements.size() - cursor, null);
            }

            int end = Math.min(cursor + PLACEMENTS_PER_TICK, placements.size());
            Map<Item, Integer> available = player.isCreative() ? Map.of() : countAvailableMaterials(player);
            AssemblyMaterialBudget<Item> budget = new AssemblyMaterialBudget<>(available);
            Map<Item, Integer> reserved = new IdentityHashMap<>();
            List<Placement> batch = new ArrayList<>();

            for (; cursor < end; cursor++) {
                Placement placement = placements.get(cursor);
                BlockState current = level.getBlockState(placement.pos());
                if (isAcceptedBlock(placement.tier(), placement.cell(), current)) continue;
                Result invalid = validateCurrentPlacement(level, player, placement, totalPlaced,
                        placements.size() - cursor);
                if (invalid != null) return invalid;

                Item item = placement.block().asItem();
                if (!player.isCreative()) {
                    if (!budget.reserve(item)) continue;
                    reserved.merge(item, 1, Integer::sum);
                }
                batch.add(placement);
            }

            if (!batch.isEmpty()) {
                Result result = placeBatch(level, player, batch, reserved);
                if (result != null) {
                    return new Result(result.status(), totalPlaced, placements.size() - cursor, result.problemPos());
                }
                totalPlaced += batch.size();
            }

            if (cursor < placements.size()) return null;

            controller.validateStructure();
            int remaining = 0;
            for (Placement placement : placements) {
                if (!isAcceptedBlock(placement.tier(), placement.cell(), level.getBlockState(placement.pos()))) {
                    remaining++;
                }
            }
            if (remaining == 0) return new Result(Status.BUILT, totalPlaced, 0, null);
            if (totalPlaced == 0) return new Result(Status.MISSING_MATERIALS, 0, remaining, null);
            return new Result(Status.PARTIAL, totalPlaced, remaining, null);
        }

        @Nullable
        private static Result validateCurrentPlacement(ServerLevel level, ServerPlayer player, Placement placement,
                                                        int placed, int remaining) {
            BlockPos pos = placement.pos();
            if (!level.isInWorldBounds(pos)) return new Result(Status.OUT_OF_BOUNDS, placed, remaining, pos);
            if (!level.isLoaded(pos)) return new Result(Status.UNLOADED, placed, remaining, pos);
            if (!level.getBlockState(pos).canBeReplaced()) return new Result(Status.OBSTRUCTED, placed, remaining, pos);
            if (!level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, Direction.UP, new ItemStack(placement.block()))) {
                return new Result(Status.DENIED, placed, remaining, pos);
            }
            return null;
        }

        @Nullable
        private static Result placeBatch(ServerLevel level, ServerPlayer player, List<Placement> batch,
                                         Map<Item, Integer> reserved) {
            List<BlockSnapshot> snapshots = new ArrayList<>(batch.size());
            if (!player.isCreative()) consume(player, reserved);

            for (Placement placement : batch) {
                BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, placement.pos(), Block.UPDATE_ALL);
                snapshots.add(snapshot);
                if (!level.setBlock(placement.pos(), placement.block().defaultBlockState(), Block.UPDATE_ALL)) {
                    rollback(level, player, snapshots, batch, reserved);
                    return new Result(Status.FAILED, 0, 0, placement.pos());
                }
                if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
                    rollback(level, player, snapshots, batch, reserved);
                    return new Result(Status.DENIED, 0, 0, placement.pos());
                }
            }
            return null;
        }

        private static void rollback(ServerLevel level, ServerPlayer player, List<BlockSnapshot> snapshots,
                                     List<Placement> batch, Map<Item, Integer> reserved) {
            for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore();
            if (player.isCreative()) return;

            Map<Item, Integer> refundable = new IdentityHashMap<>(reserved);
            for (Placement placement : batch) {
                if (level.getBlockState(placement.pos()).is(placement.block())) {
                    refundable.computeIfPresent(placement.block().asItem(),
                            (ignored, count) -> Math.max(0, count - 1));
                }
            }
            refundable.values().removeIf(count -> count <= 0);
            refund(player, refundable);
        }
    }

    private QuantumBatteryAssembler() {}
}
