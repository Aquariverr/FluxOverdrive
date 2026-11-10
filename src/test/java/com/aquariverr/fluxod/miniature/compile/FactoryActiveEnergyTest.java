package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.runtime.BatchEnergySchedule.Stage;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static com.aquariverr.fluxod.miniature.compile.FactoryCompilerTest.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.RIGHT;
import static org.junit.jupiter.api.Assertions.*;

class FactoryActiveEnergyTest {
    @Test void excessInstalledCapacityCostsNothingAndEnvironmentalCapacityIsPerMachine() {
        var factory = success(line(10, 64), SMELT);
        assertEquals(FactoryRules.energyPerTick(10), factory.energyPerTick());
        assertEquals(List.of(new Stage(0, 5)), factory.energySchedule(1, 2).stages());
        assertEquals(List.of(new Stage(0, 60)), factory.energySchedule(12, 2).stages());
        assertEquals(List.of(new Stage(0, 1)), factory.energySchedule(1, 2000).stages());
        var preview = CompilationPreview.from(CompilationResult.success(factory));
        assertEquals(64, preview.machineCount());
        assertEquals(factory.energyPerTick(), preview.energyPerTick());
        assertEquals(factory.energyPerBatch(), preview.energyPerBatch());
    }

    @Test void finalPartialRoundOnlyChargesItsRemainingMachines() {
        var factory = success(line(17, 5), SMELT);
        var schedule = factory.energySchedule(1, 2);
        assertEquals(List.of(new Stage(0, 5), new Stage(1, 4)), schedule.stages());
        assertEquals(40, factory.requiredTicks(1, 2));
        assertEquals(FactoryRules.energyPerTick(5), schedule.energyPerTick(19));
        assertEquals(FactoryRules.energyPerTick(4), schedule.energyPerTick(20));
        assertEquals(FactoryRules.energyPerBatch(9), schedule.totalEnergy(40));
    }

    @Test void cellsThatFinishEarlyStopChargingWhileOtherCellsContinue() {
        var layout = FactoryLayout.empty(5).withCell(pos(0, 0), marker(ORE, 10, RIGHT))
                .withCell(pos(1, 0), machine(3)).withCell(pos(2, 0), machine(64))
                .withCell(pos(3, 0), new FactoryCell.Arrow()).withCell(pos(4, 0), new FactoryCell.Arrow());
        var first = recipe("first", List.of(amount(ORE, 1)), List.of(amount(DUST, 1)));
        var second = recipe("second", List.of(amount(DUST, 2)), List.of(amount(INGOT, 1)));
        var result = FactoryCompiler.compile(layout, 1, (p, m, resources) -> p.x() == 1 ? first : second);
        assertTrue(result.successful());
        var factory = result.factory();
        assertEquals(List.of(new Stage(0, 8), new Stage(1, 3), new Stage(3, 1)), factory.energySchedule(1, 1).stages());
        assertEquals(80, factory.requiredTicks(1, 1));
        assertEquals(FactoryRules.energyPerBatch(15), factory.energyPerBatch());
        assertEquals(factory.energyPerBatch(), CompilationPreview.from(result).energyPerBatch());
    }

    @Test void hugeDurationsUseBoundedScheduleAndNativeFluidNormalization() {
        var huge = success(line(100_000_001, 1), SMELT);
        assertEquals(2_000_000_020, huge.requiredTicks(1, 1));
        assertEquals(1, huge.energySchedule(1, 1).stages().size());
        assertEquals(FactoryRules.energyPerTick(1).multiply(BigInteger.valueOf(2_000_000_020L)), huge.energyPerBatch());
        var fluid = recipe("fluid", List.of(amount(ORE, 1)), List.of(amount(FLUID, 100)));
        var normalized = success(line(21, 64), fluid);
        assertEquals(BigInteger.TEN, fluid.normalization());
        assertEquals(List.of(new Stage(0, 2)), normalized.energySchedule(1, 2).stages());
    }
}
