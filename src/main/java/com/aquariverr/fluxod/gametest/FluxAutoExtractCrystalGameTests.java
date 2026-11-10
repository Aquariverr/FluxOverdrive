package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileFluxAutoExtractCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxStorage;
import com.aquariverr.fluxod.block.entity.TileQuantumEnergyConstraintInterface;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPlug;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkData;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class FluxAutoExtractCrystalGameTests {

    private static final BlockPos CRYSTAL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos SOURCE_POS = new BlockPos(2, 1, 1);
    private static final BlockPos STORAGE_POS = new BlockPos(5, 1, 1);

    @GameTest(template = "empty")
    public static void registersAsPlugAndDiscoversEnergySource(GameTestHelper helper) {
        helper.setBlock(CRYSTAL_POS, RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL.get().defaultBlockState());
        helper.setBlock(SOURCE_POS,
                RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get().defaultBlockState());

        BlockEntity crystalEntity = helper.getBlockEntity(CRYSTAL_POS);
        helper.assertTrue(crystalEntity instanceof TileFluxAutoExtractCrystal,
                "Auto Extract Crystal did not create its registered block entity");
        TileFluxAutoExtractCrystal crystal = (TileFluxAutoExtractCrystal) crystalEntity;
        helper.assertTrue(crystal instanceof IFluxPlug,
                "Auto Extract Crystal is not registered as a Flux plug");
        helper.assertTrue(crystal.getDeviceType() == FluxDeviceType.PLUG,
                "Auto Extract Crystal has the wrong Flux device type");

        BlockEntity sourceEntity = helper.getBlockEntity(SOURCE_POS);
        helper.assertTrue(sourceEntity instanceof TileQuantumEnergyConstraintInterface,
                "Test source did not create its registered block entity");
        TileQuantumEnergyConstraintInterface source = (TileQuantumEnergyConstraintInterface) sourceEntity;
        source.receiveStabilizationEnergy(1_000, false);

        crystal.triggerScan();
        helper.assertTrue(crystal.getLinkedTargets().stream()
                        .anyMatch(target -> target.pos().pos().equals(helper.absolutePos(SOURCE_POS))),
                "Auto Extract Crystal did not discover an extractable FE source");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void transfersSourceEnergyIntoFluxNetwork(GameTestHelper helper) {
        helper.setBlock(CRYSTAL_POS, RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL.get().defaultBlockState());
        helper.setBlock(SOURCE_POS,
                RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get().defaultBlockState());
        helper.setBlock(STORAGE_POS, RegistryBlocks.ENDER_FLUX_STORAGE.get().defaultBlockState());

        TileFluxAutoExtractCrystal crystal = (TileFluxAutoExtractCrystal) helper.getBlockEntity(CRYSTAL_POS);
        TileQuantumEnergyConstraintInterface source =
                (TileQuantumEnergyConstraintInterface) helper.getBlockEntity(SOURCE_POS);
        TileFluxStorage.Ender storage = (TileFluxStorage.Ender) helper.getBlockEntity(STORAGE_POS);
        int sourceEnergy = source.receiveStabilizationEnergy(1_000, false);
        helper.assertTrue(sourceEnergy > 0, "Test energy source could not store energy");
        crystal.getTransferHandler().setLimit(sourceEnergy);
        storage.getTransferHandler().setLimit(sourceEnergy);

        FluxNetwork network = FluxNetworkData.getInstance().createNetwork(
                FakePlayerFactory.getMinecraft(helper.getLevel()), "Auto Extract Test",
                0x00FFFF, SecurityLevel.PUBLIC, "");
        crystal.connect(network);
        storage.connect(network);
        crystal.triggerScan();

        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(source.getEnergy() == 0,
                        "Auto Extract Crystal did not remove energy from its source");
                helper.assertTrue(storage.getTransferHandler().getBuffer() == sourceEnergy,
                        "Extracted energy did not reach Flux Network storage");
                helper.succeed();
            } finally {
                FluxNetworkData.getInstance().deleteNetwork(network);
            }
        });
    }

    private FluxAutoExtractCrystalGameTests() {
    }
}
