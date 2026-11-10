package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.runtime.*;
import com.aquariverr.fluxod.miniature.storage.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

import java.math.BigInteger;
import java.util.Map;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureActiveEnergyGameTests {
    @GameTest(template = "empty")
    public static void partialLastRoundReloadsAndChargesOnlyRemainingMachines(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
        var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
        var source = line(h, 5, 17, 17);
        var catalog = MiniatureRecipeCatalog.get(h.getLevel().getServer());
        var compiled = catalog.compile(source.layout(), h.getLevel()).factory();
        BigInteger total = BigInteger.ZERO;
        var state = source;
        for (int paid = 1; paid <= 40; paid++) {
            // No recipes or environment are needed after the immutable batch has started.
            var step = FactoryRunner.prepare(state, paid == 1 ? compiled : null, catalog.generation(),
                    paid == 1 ? new FactoryRules.Environment(10, 1) : null, paid);
            h.assertTrue(step.charge().equals(FactoryRules.energyPerTick(paid <= 20 ? 5 : 4)), "Inactive machines were charged");
            state = step.storage(); total = total.add(step.charge());
            h.assertTrue(state.inputs().amount(ore).signum() == 0 && state.outputs().amount(ingot)
                    .equals(paid == 40 ? BigInteger.valueOf(17) : BigInteger.ZERO), "Work was refunded or committed early");
            if (paid < 40) {
                state = FactoryStateCodec.load(FactoryStateCodec.save(state, registries), registries);
                var preview = RuntimePreview.from(state, FactoryRunner.Status.RUNNING, 2);
                h.assertTrue(preview.energyPerTick().equals(FactoryRules.energyPerTick(paid < 20 ? 5 : 4))
                        && preview.totalEnergy().equals(FactoryRules.energyPerBatch(9)), "Preview lost the changing fee or batch total");
                var paused = state.withControls(false, RedstoneMode.IGNORE);
                h.assertTrue(FactoryRunner.prepare(paused, null, 0, null, 500).charge().signum() == 0, "Paused work consumed energy");
            }
        }
        h.assertTrue(total.equals(FactoryRules.energyPerBatch(9)) && state.batch() == null, "Incorrect total energy or incomplete batch");
        h.assertTrue(RuntimePreview.from(state, FactoryRunner.Status.READY, 2).energyPerTick().signum() == 0, "Idle machines show an active charge");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scarceInputsAndOutputRoomReduceChargeBeforeAtomicBatteryPayment(GameTestHelper h) {
        var source = line(h, 64, 1, 1);
        var f = MiniatureRuntimeGameTests.fixture(h, source, FactoryRules.energyPerTick(1));
        f.tick();
        var started = f.tile().factoryState().storage().orElseThrow().batch();
        h.assertTrue(started != null && started.parallelism() == 1 && started.paidTicks() == 1
                && started.energyPerTick().equals(FactoryRules.energyPerTick(1)) && f.battery().getStoredEnergy().signum() == 0,
                "One working furnace among 64 could not start with exactly one machine payment");
        var catalog = MiniatureRecipeCatalog.get(h.getLevel().getServer());
        source = line(h, 64, 1, 128);
        var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), h.getLevel().registryAccess());
        source = source.withOutputs(new ResourceInventory(Map.of(ingot, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.TWO))));
        var compiled = catalog.compile(source.layout(), h.getLevel()).factory();
        var limited = FactoryRunner.prepare(source, compiled, catalog.generation(), new FactoryRules.Environment(10, 1), 1);
        h.assertTrue(limited.storage().batch().parallelism() == 2 && limited.charge().equals(FactoryRules.energyPerTick(1)),
                "Output-limited work charged installed machines instead of its single active machine");
        h.succeed();
    }

    private static FactoryStorage line(GameTestHelper h, int machines, int perBatch, int stored) {
        var registries = h.getLevel().registryAccess();
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
        return FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE, machines), registries).storage()
                .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(ore, BigInteger.valueOf(perBatch)), GridDirection.RIGHT))
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow())
                .withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(stored)))).withControls(true, RedstoneMode.IGNORE);
    }
}
