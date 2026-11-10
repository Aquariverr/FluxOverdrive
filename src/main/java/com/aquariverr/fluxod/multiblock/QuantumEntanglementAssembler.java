package com.aquariverr.fluxod.multiblock;

import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementPortBlock;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
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

public final class QuantumEntanglementAssembler {

    public enum Status {
        COMPLETE,
        BUILT,
        MISSING_MATERIALS,
        OBSTRUCTED,
        UNLOADED,
        OUT_OF_BOUNDS,
        DENIED,
        FAILED
    }

    public record Result(Status status, int placed, @Nullable BlockPos problemPos) {}

    public static Result assemble(TileQuantumEntanglementController controller, ServerPlayer player) {
        if (!(controller.getLevel() instanceof ServerLevel level) || !controller.canPlayerManage(player)) {
            return new Result(Status.DENIED, 0, null);
        }

        BlockPos origin = controller.getBlockPos();
        BlockState controllerState = controller.getBlockState();
        List<Placement> placements = new ArrayList<>();
        Map<Item, Integer> requiredMaterials = new IdentityHashMap<>();
        for (QuantumEntanglementTemplate.Cell cell : QuantumEntanglementStructure.cells()) {
            BlockPos target = QuantumEntanglementStructure.worldPos(origin,
                    controllerState.getValue(QuantumEntanglementControllerBlock.FACING), cell.offset());
            if (!level.isInWorldBounds(target)) return new Result(Status.OUT_OF_BOUNDS, 0, target);
            if (!level.isLoaded(target)) return new Result(Status.UNLOADED, 0, target);

            BlockState expected = expectedState(cell, controllerState);
            BlockState existing = level.getBlockState(target);
            if (QuantumEntanglementStructure.matches(cell, existing,
                    controllerState.getValue(QuantumEntanglementControllerBlock.FACING))) continue;
            if (!existing.canBeReplaced()) return new Result(Status.OBSTRUCTED, 0, target);
            if (!level.mayInteract(player, target)
                    || !player.mayUseItemAt(target, Direction.UP, new ItemStack(expected.getBlock()))) {
                return new Result(Status.DENIED, 0, target);
            }
            placements.add(new Placement(target.immutable(), expected));
            requiredMaterials.merge(expected.getBlock().asItem(), 1, Integer::sum);
        }

        if (placements.isEmpty()) {
            controller.validateStructure();
            return new Result(Status.COMPLETE, 0, null);
        }
        if (!player.isCreative() && !hasMaterials(player, requiredMaterials)) {
            return new Result(Status.MISSING_MATERIALS, 0, null);
        }

        if (!player.isCreative()) consume(player, requiredMaterials);
        List<BlockSnapshot> snapshots = new ArrayList<>(placements.size());
        for (Placement placement : placements) {
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, placement.pos(), Block.UPDATE_ALL);
            snapshots.add(snapshot);
            if (!level.setBlock(placement.pos(), placement.state(), Block.UPDATE_ALL)
                    || EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
                rollback(level, snapshots);
                if (!player.isCreative()) refund(player, requiredMaterials);
                return new Result(Status.FAILED, 0, placement.pos());
            }
        }

        controller.markStructureDirty();
        controller.validateStructure();
        return new Result(Status.BUILT, placements.size(), null);
    }

    public static Component resultMessage(Result result) {
        return switch (result.status()) {
            case COMPLETE -> Component.translatable("message.flux_overdrive.quantum_entanglement.build.complete");
            case BUILT -> Component.translatable("message.flux_overdrive.quantum_entanglement.build.success", result.placed());
            case MISSING_MATERIALS -> Component.translatable("message.flux_overdrive.quantum_entanglement.build.missing");
            case OBSTRUCTED -> positionMessage("message.flux_overdrive.quantum_entanglement.build.obstructed", result.problemPos());
            case UNLOADED -> positionMessage("message.flux_overdrive.quantum_entanglement.build.unloaded", result.problemPos());
            case OUT_OF_BOUNDS -> positionMessage("message.flux_overdrive.quantum_entanglement.build.out_of_bounds", result.problemPos());
            case DENIED -> positionMessage("message.flux_overdrive.quantum_entanglement.build.denied", result.problemPos());
            case FAILED -> positionMessage("message.flux_overdrive.quantum_entanglement.build.failed", result.problemPos());
        };
    }

    public static Block expectedBlock(QuantumEntanglementTemplate.CellType type) {
        return switch (type) {
            case CASING -> RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING.get();
            case PORT -> RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get();
            case ANTENNA -> RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA.get();
        };
    }

    public static BlockState expectedState(QuantumEntanglementTemplate.Cell cell, BlockState controllerState) {
        BlockState state = expectedBlock(cell.type()).defaultBlockState();
        if (cell.type() == QuantumEntanglementTemplate.CellType.PORT) {
            state = state.setValue(QuantumEntanglementPortBlock.FACING,
                    controllerState.getValue(QuantumEntanglementControllerBlock.FACING));
        }
        return state;
    }

    private static boolean hasMaterials(ServerPlayer player, Map<Item, Integer> required) {
        Map<Item, Integer> available = countMaterials(player);
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    private static Map<Item, Integer> countMaterials(ServerPlayer player) {
        Map<Item, Integer> result = new IdentityHashMap<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) result.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return result;
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
        }
        player.getInventory().setChanged();
    }

    private static void refund(ServerPlayer player, Map<Item, Integer> materials) {
        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    private static void rollback(ServerLevel level, List<BlockSnapshot> snapshots) {
        for (int index = snapshots.size() - 1; index >= 0; index--) snapshots.get(index).restore();
    }

    private static Component positionMessage(String key, @Nullable BlockPos pos) {
        if (pos == null) return Component.translatable(key, "?", "?", "?");
        return Component.translatable(key, pos.getX(), pos.getY(), pos.getZ());
    }

    private record Placement(BlockPos pos, BlockState state) {}

    private QuantumEntanglementAssembler() {}
}
