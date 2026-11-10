package com.aquariverr.fluxod.clienttest;

import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.entanglement.QuantumPluginAdapters;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementAssembler;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import sonar.fluxnetworks.api.FluxConstants;

/** Real structures and public binding APIs in the isolated client verification world. */
final class MiniatureEntanglementClientTestSupport {
    static final BlockPos ACCESS_POS = new BlockPos(48, 90, 0);

    static void assemble(ServerPlayer player, BlockPos factoryPos) {
        var level = player.serverLevel();
        int radius = QuantumBatteryStructure.stabilizerSlotRadius(3);
        BlockPos batteryPos = factoryPos.offset(radius, 0, 0);
        level.setBlock(batteryPos, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get().defaultBlockState(), 3);
        var battery = (TileQuantumBatteryController) level.getBlockEntity(batteryPos);
        battery.setOwnerUUID(player.getUUID());
        CompoundTag data = new CompoundTag();
        battery.writeCustomTag(data, FluxConstants.NBT_SAVE_ALL);
        data.putInt("CoreLevel", 3);
        battery.readCustomTag(data, FluxConstants.NBT_SAVE_ALL);
        for (int tier = 1; tier <= 3; tier++) {
            int slotRadius = QuantumBatteryStructure.stabilizerSlotRadius(tier);
            for (var cell : QuantumBatteryStructure.layer(tier).cells()) {
                var offset = cell.offset();
                BlockPos pos = batteryPos.offset(offset.x(), offset.y(), offset.z());
                if (pos.equals(factoryPos)) continue;
                var block = QuantumBatteryAssembler.expectedBlock(tier, cell.type());
                if (offset.equals(new QuantumBatteryStructure.Offset(slotRadius, 0, 0))) {
                    block = RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get();
                }
                level.setBlock(pos, block.defaultBlockState(), 3);
            }
        }
        battery.validateStructure();
        require(battery.isOperational(), "Client fixture tier-three battery did not form");
        var state = RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get().defaultBlockState()
                .setValue(QuantumEntanglementControllerBlock.FACING, Direction.NORTH);
        level.setBlock(ACCESS_POS, state, 3);
        for (var cell : QuantumEntanglementTemplate.cells()) {
            level.setBlock(QuantumEntanglementStructure.worldPos(ACCESS_POS, Direction.NORTH, cell.offset()),
                    QuantumEntanglementAssembler.expectedState(cell, state), 3);
        }
        // Keep the survival-mode test player supported after teleporting beside the facility.
        level.setBlock(ACCESS_POS.offset(0, -1, -3), Blocks.STONE.defaultBlockState(), 3);
        access(player).validateStructure();
        player.teleportTo(ACCESS_POS.getX() + 0.5, ACCESS_POS.getY(), ACCESS_POS.getZ() - 2.5);
        bind(player, factoryPos);
        access(player).openMenu(player);
    }

    static void bind(ServerPlayer player, BlockPos factoryPos) {
        var level = player.serverLevel();
        var battery = (TileQuantumBatteryController) level.getBlockEntity(
                factoryPos.offset(QuantumBatteryStructure.stabilizerSlotRadius(3), 0, 0));
        require(access(player).bindBattery(player, GlobalPos.of(level.dimension(), battery.getBlockPos())),
                "Public battery binding was refused");
        var endpoint = battery.getEntanglementEndpoints().stream()
                .filter(entry -> entry.key().adapterId().equals(QuantumPluginAdapters.MINIATURE_UNIVERSE_ID))
                .findFirst().orElseThrow();
        require(endpoint.remoteMenu() && endpoint.absolutePos().equals(factoryPos)
                        && access(player).selectEndpoint(player, endpoint.key(), battery.getPluginDirectoryRevision()),
                "Public miniature endpoint selection was refused");
        require(level.getBlockEntity(factoryPos) instanceof TileMiniatureUniverse, "Source factory was replaced");
    }

    static TileQuantumEntanglementController access(ServerPlayer player) {
        return (TileQuantumEntanglementController) player.serverLevel().getBlockEntity(ACCESS_POS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private MiniatureEntanglementClientTestSupport() {}
}
