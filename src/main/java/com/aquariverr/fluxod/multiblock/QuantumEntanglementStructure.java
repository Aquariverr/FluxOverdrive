package com.aquariverr.fluxod.multiblock;

import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementResourcePortBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementRedstonePortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public final class QuantumEntanglementStructure {

    public record Validation(boolean formed, BlockPos problem) {}

    public static List<QuantumEntanglementTemplate.Cell> cells() {
        return QuantumEntanglementTemplate.cells();
    }

    public static QuantumEntanglementTemplate.MaterialCounts materialCounts() {
        return QuantumEntanglementTemplate.materialCounts();
    }

    public static BlockPos worldPos(BlockPos origin, Direction facing, QuantumEntanglementTemplate.Offset offset) {
        return origin.relative(facing.getClockWise(), offset.x())
                .relative(Direction.UP, offset.y())
                .relative(facing, offset.z());
    }

    public static BlockPos controllerOriginFor(BlockPos position, Direction facing,
                                               QuantumEntanglementTemplate.Offset offset) {
        return position.relative(facing.getClockWise(), -offset.x())
                .relative(Direction.DOWN, offset.y())
                .relative(facing, -offset.z());
    }

    @Nullable
    public static QuantumEntanglementTemplate.Cell cellAt(BlockPos origin, Direction facing, BlockPos position) {
        for (QuantumEntanglementTemplate.Cell cell : cells()) {
            if (worldPos(origin, facing, cell.offset()).equals(position)) return cell;
        }
        return null;
    }

    public static boolean matchesPart(BlockGetter level, BlockPos origin, Direction facing, BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (origin.equals(position)) {
            return state.is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                    && state.getValue(QuantumEntanglementControllerBlock.FACING) == facing;
        }
        QuantumEntanglementTemplate.Cell cell = cellAt(origin, facing, position);
        return cell != null && matches(cell, state, facing);
    }

    public static boolean isCasingPosition(BlockPos origin, BlockState controllerState, BlockPos position) {
        Direction facing = controllerState.getValue(QuantumEntanglementControllerBlock.FACING);
        for (QuantumEntanglementTemplate.Cell cell : cells()) {
            if (cell.type() == QuantumEntanglementTemplate.CellType.CASING
                    && worldPos(origin, facing, cell.offset()).equals(position)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(BlockPos origin, BlockState controllerState, BlockPos position) {
        Direction facing = controllerState.getValue(QuantumEntanglementControllerBlock.FACING);
        for (QuantumEntanglementTemplate.Cell cell : cells()) {
            if (worldPos(origin, facing, cell.offset()).equals(position)) return true;
        }
        return origin.equals(position);
    }

    public static Validation validate(Level level, BlockPos origin, BlockState controllerState) {
        if (!controllerState.is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())) {
            return new Validation(false, origin);
        }

        Direction facing = controllerState.getValue(QuantumEntanglementControllerBlock.FACING);
        for (QuantumEntanglementTemplate.Cell cell : cells()) {
            BlockPos target = worldPos(origin, facing, cell.offset());
            if (!level.isInWorldBounds(target) || !level.isLoaded(target)) {
                return new Validation(false, target);
            }
            BlockState state = level.getBlockState(target);
            if (!matches(cell, state, facing)) return new Validation(false, target);
        }
        return new Validation(true, null);
    }

    public static boolean matches(QuantumEntanglementTemplate.Cell cell, BlockState state, Direction facing) {
        return switch (cell.type()) {
            case CASING -> state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING.get())
                    || isEntanglementPort(state);
            case PORT -> portFaces(state, facing);
            case ANTENNA -> state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA.get());
        };
    }

    private static boolean isEntanglementPort(BlockState state) {
        return state.is(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get())
                || state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get())
                || state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get())
                || state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get());
    }

    private static boolean portFaces(BlockState state, Direction facing) {
        if (state.is(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get())) {
            return state.getValue(com.aquariverr.fluxod.block.QuantumEntanglementPortBlock.FACING) == facing;
        }
        if ((state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get())
                || state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get()))
                && state.getValue(QuantumEntanglementResourcePortBlock.FACING) == facing) {
            return true;
        }
        return state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get())
                && state.getValue(QuantumEntanglementRedstonePortBlock.FACING) == facing;
    }

    private QuantumEntanglementStructure() {}
}
