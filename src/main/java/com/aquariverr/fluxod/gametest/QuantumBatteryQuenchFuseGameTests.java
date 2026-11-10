package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import sonar.fluxnetworks.api.FluxConstants;

import java.math.BigInteger;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class QuantumBatteryQuenchFuseGameTests {

    private static final BlockPos BATTERY_POS = new BlockPos(12, 12, 12);
    private static final QuantumBatteryStructure.Offset CONSTRAINT_OFFSET =
            new QuantumBatteryStructure.Offset(3, 0, 0);
    private static final QuantumBatteryStructure.Offset FUSE_OFFSET =
            new QuantumBatteryStructure.Offset(-3, 0, 0);
    private static final BigInteger STORED_ENERGY = BigInteger.valueOf(1_000_000L);

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tierOneFuseStopsQuenchWithoutExplosionOrBlockDamage(GameTestHelper helper) {
        QuantumBatteryPlugin fuse = RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1.get();
        helper.assertTrue(fuse.canInstallInRing(1), "Tier I fuse does not fit the Tier I ring");
        helper.assertTrue(!fuse.canInstallInRing(2) && !fuse.canInstallInRing(3),
                "Tier I fuse must stay in the inner ring so burnout stops the whole structure");

        helper.setBlock(BATTERY_POS, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get().defaultBlockState());
        TileQuantumBatteryController battery = (TileQuantumBatteryController)
                helper.getLevel().getBlockEntity(helper.absolutePos(BATTERY_POS));
        helper.assertTrue(battery != null, "Battery controller block entity was not created");

        for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(1).cells()) {
            QuantumBatteryStructure.Offset offset = cell.offset();
            Block block = QuantumBatteryAssembler.expectedBlock(1, cell.type());
            if (offset.equals(CONSTRAINT_OFFSET)) {
                block = RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get();
            } else if (offset.equals(FUSE_OFFSET)) {
                block = RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1.get();
            }
            helper.setBlock(BATTERY_POS.offset(offset.x(), offset.y(), offset.z()), block.defaultBlockState());
        }
        battery.validateStructure();
        helper.assertTrue(battery.isOperational(), "Fused Tier I battery did not become operational");

        CompoundTag data = new CompoundTag();
        battery.writeCustomTag(data, FluxConstants.NBT_SAVE_ALL);
        data.putByteArray("QuantumEnergy", STORED_ENERGY.toByteArray());
        data.putInt("StabilityBasisPoints", 0);
        data.putInt("StabilityUnpoweredTicks", 0);
        data.putInt("StabilityPoweredTicks", 0);
        battery.readCustomTag(data, FluxConstants.NBT_SAVE_ALL);
        helper.assertTrue(STORED_ENERGY.equals(battery.getStoredEnergy()),
                "Battery test energy was not loaded");

        ArmorStand witness = helper.spawn(EntityType.ARMOR_STAND, BATTERY_POS.above());
        float witnessHealth = witness.getHealth();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(battery.getStoredEnergy().signum() == 0,
                    "Fuse preserved stored FE");
            helper.assertTrue(!battery.isOperational() && battery.getStructureTier() == 0,
                    "Fuse did not stop the battery structure");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(
                            BATTERY_POS.offset(FUSE_OFFSET.x(), FUSE_OFFSET.y(), FUSE_OFFSET.z()))).isAir(),
                    "Fuse was not burned out");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(BATTERY_POS))
                            .is(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get()),
                    "Protected quench destroyed the controller");
            helper.assertTrue(witness.isAlive() && witness.getHealth() == witnessHealth,
                    "Protected quench damaged a nearby entity");

            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(1).cells()) {
                QuantumBatteryStructure.Offset offset = cell.offset();
                if (offset.equals(FUSE_OFFSET)) continue;
                Block expected = offset.equals(CONSTRAINT_OFFSET)
                        ? RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get()
                        : QuantumBatteryAssembler.expectedBlock(1, cell.type());
                BlockPos position = helper.absolutePos(BATTERY_POS.offset(offset.x(), offset.y(), offset.z()));
                helper.assertTrue(helper.getLevel().getBlockState(position).is(expected),
                        "Protected quench damaged structure block at " + offset);
            }
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(BATTERY_POS.above()))
                            .is(Blocks.AIR),
                    "Protected quench changed an unrelated interior block");
            helper.succeed();
        });
    }

    private QuantumBatteryQuenchFuseGameTests() {}
}
