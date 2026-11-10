package com.aquariverr.fluxod.client.model.ctm;

import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class QuantumEntanglementConnectedTexture {
    private static final List<Direction> HORIZONTAL_DIRECTIONS = List.of(
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    static long compute(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Set<Membership> memberships = memberships(level, pos);
        return ConnectedTextureLogic.compute(pos, samplePos -> {
            BlockState sampleState = level.getBlockState(samplePos);
            if (sampleState.is(state.getBlock())) return true;
            for (Membership membership : memberships) {
                if (QuantumEntanglementStructure.matchesPart(
                        level, membership.origin(), membership.facing(), samplePos)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static Set<Membership> memberships(BlockAndTintGetter level, BlockPos partPos) {
        Set<Membership> result = new HashSet<>();
        BlockState partState = level.getBlockState(partPos);
        for (Direction facing : HORIZONTAL_DIRECTIONS) {
            if (partState.is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                    && partState.getValue(QuantumEntanglementControllerBlock.FACING) == facing) {
                result.add(new Membership(partPos.immutable(), facing));
            }
            for (QuantumEntanglementTemplate.Cell cell : QuantumEntanglementStructure.cells()) {
                BlockPos origin = QuantumEntanglementStructure.controllerOriginFor(
                        partPos, facing, cell.offset());
                BlockState controllerState = level.getBlockState(origin);
                if (controllerState.is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                        && controllerState.getValue(QuantumEntanglementControllerBlock.FACING) == facing
                        && QuantumEntanglementStructure.matchesPart(level, origin, facing, partPos)) {
                    result.add(new Membership(origin.immutable(), facing));
                }
            }
        }
        return Set.copyOf(result);
    }

    private record Membership(BlockPos origin, Direction facing) {}

    private QuantumEntanglementConnectedTexture() {}
}
