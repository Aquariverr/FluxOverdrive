package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileFluxExtractCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxStorage;
import com.aquariverr.fluxod.block.entity.TileQuantumEnergyConstraintInterface;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
public final class FluxExtractCrystalGameTests {

    private static final BlockPos CRYSTAL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos SOURCE_POS = new BlockPos(3, 1, 1);
    private static final BlockPos STORAGE_POS = new BlockPos(5, 1, 1);

    @GameTest(template = "empty")
    public static void registersAsPlugWithoutAutomaticLinks(GameTestHelper helper) {
        helper.setBlock(CRYSTAL_POS, RegistryBlocks.FLUX_EXTRACT_CRYSTAL.get().defaultBlockState());
        helper.setBlock(SOURCE_POS,
                RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get().defaultBlockState());

        BlockEntity crystalEntity = helper.getBlockEntity(CRYSTAL_POS);
        helper.assertTrue(crystalEntity instanceof TileFluxExtractCrystal,
                "Flux Extract Crystal did not create its registered block entity");
        TileFluxExtractCrystal crystal = (TileFluxExtractCrystal) crystalEntity;
        helper.assertTrue(crystal instanceof IFluxPlug,
                "Flux Extract Crystal is not registered as a Flux plug");
        helper.assertTrue(crystal.getDeviceType() == FluxDeviceType.PLUG,
                "Flux Extract Crystal has the wrong Flux device type");
        helper.assertTrue(!crystal.getBlockState().hasProperty(BlockStateProperties.POWERED),
                "Flux Extract Crystal unexpectedly exposes automatic redstone scanning");
        helper.assertTrue(crystal.getLinkedTargets().isEmpty(),
                "Flux Extract Crystal unexpectedly created automatic links");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void manuallyLinkedSourceTransfersIntoFluxNetwork(GameTestHelper helper) {
        helper.setBlock(CRYSTAL_POS, RegistryBlocks.FLUX_EXTRACT_CRYSTAL.get().defaultBlockState());
        helper.setBlock(SOURCE_POS,
                RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get().defaultBlockState());
        helper.setBlock(STORAGE_POS, RegistryBlocks.ENDER_FLUX_STORAGE.get().defaultBlockState());

        TileFluxExtractCrystal crystal = (TileFluxExtractCrystal) helper.getBlockEntity(CRYSTAL_POS);
        TileQuantumEnergyConstraintInterface source =
                (TileQuantumEnergyConstraintInterface) helper.getBlockEntity(SOURCE_POS);
        TileFluxStorage.Ender storage = (TileFluxStorage.Ender) helper.getBlockEntity(STORAGE_POS);
        int sourceEnergy = source.receiveStabilizationEnergy(1_000, false);
        helper.assertTrue(sourceEnergy > 0, "Test energy source could not store energy");
        crystal.getTransferHandler().setLimit(sourceEnergy);
        storage.getTransferHandler().setLimit(sourceEnergy);

        BlockPos absoluteSource = helper.absolutePos(SOURCE_POS);
        helper.assertTrue(crystal.isValidLinkTarget(absoluteSource, Direction.NORTH),
                "Flux Extract Crystal rejected a valid energy source face");
        helper.assertTrue(crystal.addLink(absoluteSource, Direction.NORTH),
                "Flux Extract Crystal could not add a manual link");

        FluxNetwork network = FluxNetworkData.getInstance().createNetwork(
                FakePlayerFactory.getMinecraft(helper.getLevel()), "Extract Test",
                0x00FFFF, SecurityLevel.PUBLIC, "");
        crystal.connect(network);
        storage.connect(network);

        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(source.getEnergy() == 0,
                        "Flux Extract Crystal did not remove energy from its linked source");
                helper.assertTrue(storage.getTransferHandler().getBuffer() == sourceEnergy,
                        "Extracted energy did not reach Flux Network storage");
                helper.succeed();
            } finally {
                FluxNetworkData.getInstance().deleteNetwork(network);
            }
        });
    }

    private FluxExtractCrystalGameTests() {
    }
}
