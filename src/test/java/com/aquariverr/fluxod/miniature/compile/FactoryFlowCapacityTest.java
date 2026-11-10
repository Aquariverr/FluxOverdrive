package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import static com.aquariverr.fluxod.miniature.compile.FactoryCompilerTest.*;
import static com.aquariverr.fluxod.miniature.model.FactoryDiagnostic.Code.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryFlowCapacityTest {
    @Test void machineCountChangesOnlyCapacityAndFeesNotTheConfiguredRecipe() {
        var slow = success(line(10, 1), SMELT);
        var fast = success(line(10, 20), SMELT);
        assertEquals(slow.inputs(), fast.inputs());
        assertEquals(slow.outputs(), fast.outputs());
        assertEquals(BigInteger.TEN, slow.operations().get(pos(1, 0)));
        assertEquals(1, slow.maxParallelism(2));
        assertEquals(4, fast.maxParallelism(2));
        assertEquals(100, slow.requiredTicks(1, 2));
        assertEquals(20, fast.requiredTicks(4, 2));
        assertEquals(slow.energyPerTick().multiply(BigInteger.valueOf(200)), slow.energyPerBatch());
        assertEquals(128, success(line(1, 64), SMELT).maxParallelism(2));
    }

    @Test void nativePartialStandardFluidBatchIsLegalButFractionalNativeRecipesAreNot() {
        var nativeRecipe = recipe("small_fluid", List.of(amount(ORE, 2)), List.of(amount(FLUID, 100)));
        var compiled = success(line(2, 3), nativeRecipe);
        assertEquals(List.of(amount(FLUID, 100)), compiled.outputs());
        assertEquals(60, compiled.maxParallelism(2));
        assertFailure(line(3, 3), nativeRecipe, NON_INTEGRAL_RECIPE, pos(1, 0));
        var twoInputs = recipe("ratio", List.of(amount(ORE, 2), amount(DUST, 3)), SMELT.outputs());
        var layout = line(4, 64).withCell(pos(1, 1), marker(DUST, 6, UP));
        assertEquals(List.of(amount(INGOT, 2)), success(layout, twoInputs).outputs());
        assertFailure(layout.withCell(pos(1, 1), marker(DUST, 3, UP)), twoInputs, MISSING_INPUT, pos(1, 0));
    }

    @Test void weightedSplitConservesEveryResourceAndExactComponents() {
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("test:variant", "kept");
        var fluid = new ResourceKey(ResourceKind.FLUID, "test:fluid", tag);
        var chemical = new ResourceKey(ResourceKind.CHEMICAL, "test:chemical");
        var products = List.of(amount(INGOT, 4), amount(fluid, 8), amount(chemical, 12));
        var recipe = recipe("split", SMELT.inputs(), products);
        var compiled = success(splitProducts(), recipe);
        assertEquals(FactoryCompiler.map(products), FactoryCompiler.map(compiled.outputs()));
        assertEquals(Map.of(INGOT, BigInteger.ONE, fluid, BigInteger.TWO, chemical, BigInteger.valueOf(3)),
                FactoryCompiler.map(compiled.outputRows().get(1)));
        assertEquals(Map.of(INGOT, BigInteger.valueOf(3), fluid, BigInteger.valueOf(6), chemical, BigInteger.valueOf(9)),
                FactoryCompiler.map(compiled.outputRows().get(0)));
        var merged = splitProducts().withCell(pos(4, 0), new FactoryCell.Arrow(DOWN));
        assertEquals(Map.of(1, products), success(merged, recipe).outputRows());
        assertFailure(splitProducts(), SMELT, NON_INTEGRAL_SPLIT, pos(2, 1));
        assertFailure(splitProducts().withCell(pos(2, 0), FactoryCell.Empty.INSTANCE), recipe, ROUTE_DEAD_END, pos(2, 1));
    }

    @Test void rawInputsCanSplitAcrossMachinesButCannotBypassThem() {
        var layout = FactoryLayout.empty(5).withCell(pos(0, 1), marker(ORE, 8, RIGHT))
                .withCell(pos(1, 1), new FactoryCell.Arrow(Map.of(RIGHT, 3, UP, 1)))
                .withCell(pos(2, 1), machine(3)).withCell(pos(1, 0), machine(1));
        for (int x = 2; x < 5; x++) layout = layout.withCell(pos(x, 0), new FactoryCell.Arrow());
        for (int x = 3; x < 5; x++) layout = layout.withCell(pos(x, 1), new FactoryCell.Arrow());
        var compiled = success(layout, SMELT);
        assertEquals(List.of(amount(INGOT, 8)), compiled.outputs());
        assertEquals(20, compiled.requiredTicks(1, 2));
        assertEquals(BigInteger.valueOf(6), compiled.operations().get(pos(2, 1)));
        assertFailure(layout.withCell(pos(1, 0), new FactoryCell.Arrow()), SMELT, BYPASS_ROUTE, pos(5, 0));
    }

    @Test void weightsRotateTogetherAreImmutableAndAffectCompilationIdentity() {
        var arrow = new FactoryCell.Arrow(Map.of(RIGHT, 1, UP, 3));
        assertEquals(Map.of(DOWN, 1, RIGHT, 3), arrow.rotated().weights());
        assertEquals(arrow, arrow.rotated().rotated().rotated().rotated());
        assertEquals(Map.of(RIGHT, 1), arrow.withWeight(UP, 0).weights());
        assertThrows(IllegalArgumentException.class, () -> new FactoryCell.Arrow(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> arrow.withWeight(UP, -1));
        assertThrows(IllegalArgumentException.class, () -> arrow.withWeight(UP, 1_000_001));
        assertThrows(UnsupportedOperationException.class, () -> arrow.weights().clear());
        assertNotEquals(FactoryFingerprint.of(splitProducts(), Map.of(pos(1, 1), SMELT)),
                FactoryFingerprint.of(splitProducts().withCell(pos(2, 1), new FactoryCell.Arrow(Map.of(RIGHT, 3, UP, 1))), Map.of(pos(1, 1), SMELT)));
    }

    @Test void excessiveWorkIsRejectedInsteadOfOverflowingTickProgress() {
        assertEquals(DURATION_LIMIT_EXCEEDED, compile(line(Integer.MAX_VALUE, 1), SMELT).diagnostic().code());
        var huge = FactoryLimits.MAX_ENCODED_QUANTITY;
        var expand = recipe("expand", SMELT.inputs(), List.of(new ResourceAmount(DUST, huge)));
        var process = recipe("process", List.of(amount(DUST, 1)), List.of(amount(CRYSTAL, 1)));
        var shrink = recipe("shrink", List.of(new ResourceAmount(CRYSTAL, huge)), SMELT.outputs());
        var layout = FactoryLayout.empty(5).withCell(pos(0, 0), marker(ORE, 1, RIGHT))
                .withCell(pos(1, 0), machine(1)).withCell(pos(2, 0), machine(1)).withCell(pos(3, 0), machine(1))
                .withCell(pos(4, 0), new FactoryCell.Arrow());
        var result = FactoryCompiler.compile(layout, 1, (p, m, resources) -> p.x() == 1 ? expand : p.x() == 2 ? process : shrink);
        assertEquals(DURATION_LIMIT_EXCEEDED, result.diagnostic().code());
        assertDoesNotThrow(() -> CompilationPreview.from(result));
    }

    private static FactoryLayout splitProducts() {
        var layout = FactoryLayout.empty(5).withCell(pos(0, 1), marker(ORE, 1, RIGHT)).withCell(pos(1, 1), machine(1))
                .withCell(pos(2, 1), new FactoryCell.Arrow(Map.of(RIGHT, 1, UP, 3)));
        for (int x = 2; x < 5; x++) layout = layout.withCell(pos(x, 0), new FactoryCell.Arrow());
        for (int x = 3; x < 5; x++) layout = layout.withCell(pos(x, 1), new FactoryCell.Arrow());
        return layout;
    }
}
